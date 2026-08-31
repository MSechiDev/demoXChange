# demoXChange Backend API — Frontend Integration Guide

Contract for the parts of the demoXChange backend that are live today: authentication, users, categories, items, item photos, listings, offers, exchanges, reviews, reports, and messaging. Use this to build the frontend against the real behavior of the API — not a history of changes.

Base URL: `http://localhost:8080` (dev). All endpoints are under `/api`.

## Authentication

Stateless JWT (HS256), sent as a standard bearer token. No sessions, no refresh endpoint — the token simply expires and the client re-logs in.

```
Authorization: Bearer <token>
```

Token claims: `sub` = username, `uid` = numeric user id, `roles` = array of role names (e.g. `["USER"]`, `["ADMIN"]`).

Roles: `GUEST`, `USER`, `ADMIN`.

### `POST /api/auth/login` — public
Request:
```json
{ "username": "string", "password": "string" }
```
Response `200`:
```json
{ "token": "string", "roles": ["USER"] }
```
`username`/`password` both required (non-blank); wrong credentials → `401` (default Spring Security body, no custom `ApiError`).

### `POST /api/auth/logout`
`204 No Content`. Purely symbolic — JWT is stateless, so "logout" just means the client discards the token. No server-side call is required, but the endpoint exists if you want to hit it anyway.

### `POST /api/auth/register` — public
Self-registration is open (no admin gate) — anyone can call this. `email` is required as of today.
Request:
```json
{
  "username": "string (max 70)",
  "email": "string, valid email, max 254",
  "password": "string",
  "roles": ["USER"]
}
```
- `password` must satisfy `StrongPassword`: ≥12 chars, at least one lowercase, one uppercase, one digit, one symbol, and not in a small common-password blocklist.
- `roles`: non-empty set, each value one of `GUEST|USER|ADMIN` (case-insensitive). Nothing stops a self-registering user from requesting `ADMIN` today — there's no server-side restriction on which roles a caller may grant themselves.

Response `201`:
```json
{ "id": 1, "username": "string", "email": "string", "enabled": true, "roles": ["USER"] }
```
Conflicts: `409 username_unavailable`, `409 email_unavailable`.

Existing users created before today have `email: null` — there's no endpoint yet to backfill/update it.

## Error shape

Domain errors (business-rule violations raised explicitly by the service layer) return:
```json
{ "errorCode": "some_snake_case_code", "message": "human-readable message" }
```
with status `400` (`BadRequestException`), `404` (`NotFoundException`), `409` (`ConflictException`), or `403` (`ForbiddenException` — thrown explicitly by a service, e.g. "you're not a participant in this exchange"). Each endpoint below lists its specific `errorCode`s.

**Two different kinds of 403** exist and only one has the `ApiError` shape:
- A `ForbiddenException` thrown by a service → structured `ApiError` as above.
- A `@PreAuthorize` annotation denying access (wrong role) → default Spring Security body, no `errorCode`.

Everything else falls back to Spring Boot defaults — **do not expect the `errorCode`/`message` shape** for these:
- `401` — missing/invalid/expired JWT.
- `403` from `@PreAuthorize` (see above).
- `400` on `@Valid` failures (missing/malformed fields) — standard Spring validation error body, not `ApiError`.

**One inconsistency to know about**: most `errorCode`s are `snake_case` (e.g. `user_not_found`), but the ones raised by `ListingService` are `UPPER_SNAKE_CASE` (e.g. `LISTING_NOT_FOUND`, `ITEM_NOT_FOUND`) — don't assume casing, match on the literal string from this doc.

## Users — `/api/users`

Just a public profile lookup for now — no self-service editing, no auth-linked "my profile" shortcut (use the `uid` JWT claim and call this by id).

### `GET /api/users/{id}/profile` — any authenticated user
Response:
```json
{
  "userId": 1,
  "username": "string",
  "averageRating": 4.5,
  "reviewsCount": 2,
  "reviews": [
    { "id": 10, "authorId": 3, "authorUsername": "string", "rating": 5, "comment": "string | null", "createdAt": "2026-08-27T10:00:00Z" }
  ]
}
```
`averageRating` is `null` (not `0`) when `reviewsCount` is `0`. `404 user_not_found` if the id doesn't exist. Rating is computed live from the `reviews` table on every call — it's never stored on the user row.

## Categories — `/api/categories`

Public read, admin-only write.

`CategoryDto`:
```json
{
  "id": 1,
  "name": "string",
  "slug": "string",
  "description": "string | null",
  "active": true,
  "createdAt": "2026-08-27T10:00:00Z",
  "updatedAt": "2026-08-27T10:00:00Z"
}
```

### `GET /api/categories` — public
Returns **all** categories (active and inactive) sorted by `name`. There is no `?active=` filter — if the public listing/search UI should hide inactive categories, filter client-side on `active`.

### `GET /api/categories/{id}` — public
`404 category_not_found` if missing.

### `POST /api/categories` — ADMIN
Request (`CreateCategoryRequest`):
```json
{ "name": "string (max 60)", "slug": "kebab-case, max 60", "description": "string | null (max 255)" }
```
`slug` pattern: `^[a-z0-9]+(-[a-z0-9]+)*$`. `201` on success.
Conflicts: `409 category_name_taken`, `409 category_slug_taken` (case-insensitive).

### `PUT /api/categories/{id}` — ADMIN
Full replace, same shape as create plus `"active": boolean`. Same uniqueness conflicts (excluding itself).

### `PATCH /api/categories/{id}/deactivate` — ADMIN
No body. Sets `active: false`, returns the updated `CategoryDto`.

### `PATCH /api/categories/{id}/activate` — ADMIN
Same as above, sets `active: true`.

An **inactive category can't be assigned to a new item** — see `POST /api/items` below.

## Items — `/api/items`

An item is something a user owns and can offer for exchange. Items exist independently of listings — you create an item first, then optionally publish it in a listing (see Listings below).

`ItemDto`:
```json
{
  "id": 5,
  "ownerId": 1,
  "categoryId": 10,
  "categoryName": "string",
  "title": "string",
  "description": "string",
  "estimatedValue": 150.00,
  "itemCondition": "buone",
  "archived": false,
  "createdAt": "2026-08-27T10:00:00Z",
  "updatedAt": "2026-08-27T10:00:00Z",
  "images": [
    { "id": 1, "itemId": 5, "url": "/files/items/5/ab12.jpg", "displayOrder": 0, "createdAt": "2026-08-27T10:00:00Z" }
  ]
}
```
`itemCondition` enum (Italian values): `nuovo | come_nuovo | ottime | buone | discrete | da_riparare`.

All endpoints below require authentication; ownership is enforced inside the service — an item that exists but belongs to someone else returns `404 item_not_found`, same as one that doesn't exist at all (no `403`, so you can't probe for other users' item ids).

### `GET /api/items` — own items only
Query params (all optional): `categoryId`, `condition` (one of the enum values), `minValue`, `maxValue`, `q` (free-text match on title/description), `includeArchived` (default `false`).
`400 invalid_price_range` if `minValue > maxValue`.

### `GET /api/items/{id}` — owner only
`404 item_not_found` if missing or not yours.

### `POST /api/items` — any authenticated user
Request (`CreateItemRequest`):
```json
{
  "categoryId": 10,
  "title": "string (max 120)",
  "description": "string (max 2000)",
  "estimatedValue": 150.00,
  "itemCondition": "buone"
}
```
`estimatedValue`: optional, `>= 0`, up to 8 integer digits and 2 decimals. `404 category_not_found`. `400 category_inactive` if the category exists but is deactivated. `201` with the created `ItemDto` (empty `images`).

### `PUT /api/items/{id}` — owner only
Same body shape as create, plus `"archived": boolean`. `404 item_not_found` if not yours.

### `DELETE /api/items/{id}` — owner only
`204 No Content`. **Soft delete** — sets `archived: true`, doesn't remove the row (so existing offers/listings referencing it keep working). `404 item_not_found` if not yours.

## Item photos — `/api/items/{itemId}/images`

Owner-only management of an item's photos. Files are validated by their actual bytes (magic numbers), not by the client-supplied `Content-Type` — a renamed non-image file is rejected regardless of what header the client sends. Stored files are served publicly (no auth) at the URL in `ItemImageDto.url`, e.g. `GET /files/items/5/ab12cd34.jpg`.

Max **10 images per item**. Accepted formats: JPEG, PNG, WEBP.

### `GET /api/items/{itemId}/images` — owner only
`404 item_not_found` if not yours. Ordered by `displayOrder` ascending.

### `POST /api/items/{itemId}/images` — owner only
`multipart/form-data`, field name `file`. `201` with the created `ItemImageDto` (appended at the end of the order). `400 invalid_file` if empty or not a recognized image format. `409 too_many_images` at the 10-image cap.

### `PATCH /api/items/{itemId}/images/order` — owner only
Request (`ReorderImagesRequest`):
```json
{ "imageIds": [3, 1, 2] }
```
Must list **exactly** the item's existing image ids, each once, in the new order — any missing id, extra id, duplicate, or `null` → `400 invalid_image_order`. Returns the full reordered `ItemImageDto` list.

### `DELETE /api/items/{itemId}/images/{imageId}` — owner only
`204 No Content`. Deletes the DB row, the file on disk, and repacks the remaining images' `displayOrder` to stay contiguous from `0`. `404 image_not_found` if the image doesn't belong to that item.

## Listings — `/api/listings`

Publishing an item for exchange. One active listing per item (`existsByItemId` check) — you can't list the same item twice while a listing for it is still live.

`ListingDto`:
```json
{
  "id": 6,
  "itemId": 5,
  "ownerId": 1,
  "city": "string",
  "status": "attivo",
  "publishedAt": "2026-08-27T10:00:00Z",
  "updatedAt": "2026-08-27T10:00:00Z",
  "acceptedCategoryIds": [10, 12]
}
```
`status` enum: `attivo | in_trattativa | scambiato | eliminato`. Lifecycle is fully automatic now, not just settable via `PATCH`:
- `attivo` — open, visible in search, can receive offers.
- `in_trattativa` — an offer on it was just accepted (see Offers below); no new offers accepted while in this state.
- `scambiato` — the exchange on it completed (both sides confirmed, see Exchanges below).
- `eliminato` — manually retired via `PATCH .../status`.
- If an exchange on the listing is cancelled, it automatically reverts to `attivo`.

### `POST /api/listings` — any authenticated user
Request (`CreateListingRequest`):
```json
{ "city": "string (max 100)", "acceptedCategoryIds": [10, 12], "itemId": 5 }
```
`acceptedCategoryIds` is enforced when people make offers on this listing (see Offers) — an offered item whose category isn't in this list is rejected.
Errors: `404 USER_NOT_FOUND`, `404 ITEM_NOT_FOUND` (also returned if the item isn't yours — cloaked), `409 LISTING_ALREADY_EXISTS`, `400 INVALID_LISTING` (empty `acceptedCategoryIds`), `404 CATEGORY_NOT_FOUND`. `201` with the created `ListingDto`, `status: "attivo"`.

### `GET /api/listings/mine` — own listings only

### `PATCH /api/listings/{id}/status` — owner only
Request (`UpdateListingStatusRequest`):
```json
{ "status": "eliminato" }
```
`404 LISTING_NOT_FOUND` if missing or not yours (cloaked). No validation on which transitions are legal — you can set any enum value manually, including ones the automatic lifecycle above also uses.

### `GET /api/listings` — any authenticated user
Browse/search **everyone's** active listings (not just yours — for that, use `/mine` above). Query params (all optional): `keyword` (matches item title, case-insensitive substring), `categoryId`, `minPrice`, `maxPrice` (both match `Item.estimatedValue`).
Always filtered to `status = attivo` server-side — you'll never see `in_trattativa`/`scambiato`/`eliminato` listings here regardless of filters.

`ListingSearchDto`:
```json
{
  "id": 6,
  "city": "string",
  "status": "attivo",
  "publishedAt": "2026-08-27T10:00:00Z",
  "itemId": 5,
  "itemTitle": "string",
  "itemDescription": "string",
  "itemEstimatedValue": 150.00,
  "categoryId": 10,
  "categoryName": "string",
  "primaryImageUrl": "/files/items/5/ab12.jpg | null"
}
```
`primaryImageUrl` is the first image (`displayOrder = 0`) if the item has any, else `null`.

## Offers — `/api/offers`

Negotiation on a listing: make an offer with your own items, the listing owner approves or counters. All endpoints require authentication; ownership/participant checks happen inside the service (no `@PreAuthorize`).

`OfferDto`:
```json
{
  "offerId": 3,
  "offererId": 2,
  "offererName": "string",
  "offeredItems": [
    { "id": 7, "title": "string", "condition": "nuovo", "estimatedValue": 40.00, "imageUrl": "string | null" }
  ],
  "message": "string | null",
  "status": "in_attesa"
}
```
`status` enum: `in_attesa | accettata | rifiutata | annullata | controproposta`.

### `POST /api/listings/{listingId}/offers` — any authenticated user
Lives on `ListingController`, not `OfferController` — it's a sub-resource of the listing being offered on.
Request (`MakeOfferRequest`):
```json
{ "itemIds": [7], "message": "string | null" }
```
No `@Valid`/bean validation on this DTO — an empty/`null` `itemIds` fails inside the service, not with a clean `400` validation body.
- `404 listing_not_found`.
- `400 listing_not_available` — listing must be `attivo` (not `in_trattativa`/`scambiato`/`eliminato`; an offer already accepted on it blocks new ones).
- `404 user_not_found`.
- `400 items_not_found` — any item id that doesn't exist or isn't owned by the caller.
- `400 item_category_not_accepted` — any offered item whose category isn't in the listing's `acceptedCategoryIds`.
`201` with the created `OfferDto`, `status: "in_attesa"`.

### `GET /api/offers/sent` — offers you made
### `GET /api/offers/received` — offers made on your listings

### `PATCH /api/offers/{offerId}/approve` — listing owner only
Accepting one offer on a listing automatically **rejects every other pending (`in_attesa`) offer** on that same listing, and moves the listing to `in_trattativa`, and creates the `Exchange` that tracks completion (see Exchanges below).
`403 offer_not_owned`. `400 not_valid_status` if the offer isn't `in_attesa`.

### `POST /api/offers/{offerId}/counter` — listing owner only
Counter-propose different items on the same negotiation. Marks the **original** offer `controproposta` (with `respondedAt` set) and creates a new `Offer` row (status `in_attesa`) linked to it as a child — that new offer's id is what gets approved/countered/messaged going forward.
Request (`CounterOfferRequest`): same shape as `MakeOfferRequest`.
`400 not_valid_status` (parent not `in_attesa`), `403 offer_not_owned`, `404 user_not_found`, `400 items_not_found`, `400 item_category_not_accepted`. `201` with the new `OfferDto`.

## Exchanges — `/api/exchanges`

Tracks completion of an accepted offer. Created automatically by `PATCH /api/offers/{offerId}/approve` — there's no manual creation endpoint. Both the offerer and the listing owner ("owner") must independently confirm for it to complete.

`ExchangeDto`:
```json
{
  "id": 1,
  "offerId": 3,
  "listingId": 6,
  "ownerId": 1,
  "offererId": 2,
  "status": "in_corso",
  "ownerConfirmedAt": null,
  "offererConfirmedAt": null,
  "completedAt": null,
  "createdAt": "2026-08-27T10:00:00Z"
}
```
`status` enum: `in_corso | completato | annullato`.

All endpoints require authentication; participant checks (`ownerId`/`offererId`) are enforced via `@PreAuthorize` calling into the service — a non-participant gets the plain Spring `403` (no `ApiError` body), not `not_participant`.

### `GET /api/exchanges/mine` — exchanges you're part of
### `GET /api/exchanges/{id}` — participant only

### `PATCH /api/exchanges/{id}/confirm` — participant only
Call once as each side. First call just records your timestamp (`ownerConfirmedAt` or `offererConfirmedAt`); once **both** are set, `status` flips to `completato`, `completedAt` is stamped, and the listing moves to `scambiato`.
`400 not_valid_status` if the exchange isn't `in_corso` (already completed/cancelled). `409 already_confirmed` if you personally already confirmed (idempotency guard, not a re-confirm).

### `PATCH /api/exchanges/{id}/cancel` — participant only
Either side can unilaterally cancel while it's `in_corso`. Sets `status: annullato` and reverts the listing to `attivo` (so it can receive new offers again). Does **not** touch the underlying offer's `accettata` status.
`400 not_valid_status` if not `in_corso`.

## Reviews — `/api/reviews`

Leave a rating on the other party once an exchange is `completato`. One review per (exchange, author) pair — each side can leave exactly one review of the other for a given exchange.

`ReviewSummaryDto`:
```json
{
  "id": 10,
  "authorId": 3,
  "authorUsername": "string",
  "rating": 5,
  "comment": "string | null",
  "createdAt": "2026-08-27T10:00:00Z"
}
```
Reviews aren't fetched by their own endpoint — they surface via `GET /api/users/{id}/profile` (the recipient's `reviews` list and `averageRating`).

### `POST /api/reviews` — any authenticated user
Request (`CreateReviewDto`):
```json
{ "exchangeId": 1, "rating": 5, "comment": "string | null (max 1000)" }
```
`rating`: integer 1–5. The recipient is inferred automatically (whichever of the exchange's two parties you aren't) — there's no `recipientId` field.
- `404 user_not_found`, `404 exchange_not_found`.
- `400 exchange_not_completed` if the exchange's `status` isn't `completato`.
- `403 not_participant` if you're neither the offerer nor the listing owner on that exchange.
- `400 cannot_review_self` (defensive guard, shouldn't be reachable in practice).
- `409 review_already_exists` if you already reviewed this exchange.
- A small number of internal data-integrity checks (e.g. an exchange missing its linked offer) intentionally fall through as a raw `500` — those signal a server bug, not something the client did wrong.

## Reports — `/api/reports`

Lets a user report either another **user** or a **listing**. Admins review and close reports.

`ReportDto`:
```json
{
  "id": 1,
  "reporterId": 2,
  "reporterUsername": "string",
  "reportedUserId": 3,
  "reportedUsername": "string | null",
  "reportedListingId": null,
  "reason": "spam",
  "description": "string | null",
  "status": "aperta",
  "reviewedById": null,
  "reviewedByUsername": null,
  "reviewedAt": null,
  "resolutionNote": null,
  "createdAt": "2026-08-27T10:00:00Z"
}
```
Exactly one of `reportedUserId` / `reportedListingId` is ever non-null on a given report, depending on the target type.

`reason` enum (Italian values, used as-is on the wire): `spam | contenuto_offensivo | truffa | oggetto_illegale | profilo_falso | altro`

`status` enum (Italian values): `aperta | in_revisione | risolta | respinta`
- `aperta` — just created.
- `in_revisione` — admin picked it up but hasn't closed it.
- `risolta` / `respinta` — terminal states (accepted/rejected). No further transitions once here.

### `GET /api/reports/mine` — any authenticated user
Own reports only (by JWT `uid`), newest first.

### `GET /api/reports` — ADMIN only
`?status=aperta` (optional, one of the enum values above) to filter. Oldest first (queue order) — this doubles as the moderation queue view, there's no separate aggregate endpoint. Omit for all statuses.

### `GET /api/reports/{id}` — ADMIN or the reporter who created it
Anyone else gets `403`. `404 report_not_found` if it doesn't exist.

### `POST /api/reports` — any authenticated user
Request (`CreateReportRequest`):
```json
{ "reason": "spam", "description": "string | null (max 1000)", "reportedUserId": 3, "reportedListingId": null }
```
Business rules (all returned as `ApiError`):
- Exactly one of `reportedUserId` / `reportedListingId` must be set — `400 invalid_target` if both or neither.
- Can't report yourself — `400 cannot_report_self`.
- Target user/listing must exist — `404 user_not_found` / `404 listing_not_found`.
- Only one **open** report (`aperta` or `in_revisione`) per reporter+target at a time — `409 report_already_open`. A closed report doesn't block a new one against the same target.
- No check prevents reporting your own listing (only self-reporting a user is blocked) — known, accepted gap.

`201` with the created `ReportDto` (status `aperta`).

### `PATCH /api/reports/{id}/review` — ADMIN
Request (`ReviewReportRequest`):
```json
{ "status": "risolta", "resolutionNote": "string | null (max 1000)" }
```
`status` must be one of `in_revisione|risolta|respinta` (an admin can't set it back to `aperta`).
`409 report_already_closed` if the report is already `risolta`/`respinta` — closed reports can't be reviewed again.
On success: sets `status`, `resolutionNote`, `reviewedBy` (the acting admin), `reviewedAt` (now).

## Messages — `/api/offers/{offerId}/messages` and `/api/messages`

Private chat between the two people in an offer negotiation: the **offerer** and the **listing owner**. There's no free-standing DM — a conversation only exists in the context of an offer (this is a deliberate design choice, not a gap).

Counter-offers create new `Offer` rows chained by `parentOfferId`, but there is **exactly one message thread per negotiation**: every endpoint below takes an offer id — any offer in the chain, original or counter-offer — and the server resolves it up to the root offer internally. So it doesn't matter which offer id in the negotiation you use, you always land on the same thread.

`MessageDto`:
```json
{
  "id": 1,
  "offerId": 5,
  "senderId": 2,
  "senderUsername": "string",
  "body": "string",
  "sentAt": "2026-08-27T10:00:00Z",
  "readAt": null
}
```
`offerId` is always the **root** offer id of the negotiation (even if you sent the request against a counter-offer's id) — use it as the thread/conversation identifier. There's no `recipientId`: the other participant is whichever of {offerer, listing owner} isn't `senderId`.

### `GET /api/messages/mine` — any authenticated user
Query param `unreadOnly` (optional, default `false`).
- `unreadOnly=false` (default): one entry per conversation you're part of (as offerer or listing owner) — the most recent message in that thread, newest-conversation-first. Use it to render an inbox/conversation list.
- `unreadOnly=true`: every individual unread message addressed to you (not sender-filtered to "latest per thread"), oldest first — use it for an unread-count/notification feed, not the inbox list.

### `GET /api/offers/{offerId}/messages` — offerer or listing owner only
Full thread, oldest → newest. As a side effect, marks every message from the other participant as read (`readAt` set) — call this when the user opens the conversation. `403` if you're neither participant. `404 offer_not_found` if the offer doesn't exist.

### `POST /api/offers/{offerId}/messages` — offerer or listing owner only
Request (`SendMessageRequest`):
```json
{ "body": "string, not blank, max 2000" }
```
Same participant check as the `GET` above. `201` with the created `MessageDto`. This is also how you "reply" — there's no separate reply endpoint, just post again on the same thread.

### `DELETE /api/messages/{id}` — the sender only
`204 No Content`. **Hard delete** — the message is gone for both participants, not just hidden from your side. `403` if you didn't send it. `404 message_not_found` if it doesn't exist.

## Known gaps

Everything in the team's feature list is now built and has a controller. What's left is scoped, known behavior rather than missing endpoints:
- Reporting your own listing isn't blocked (only self-reporting a *user* is) — see Reports above.
- A self-registering user can request any role, including `ADMIN` — see Auth above.
- No endpoint to update an existing user's `email` (or any other profile field) after registration.
- Messaging is scoped to offer negotiations by design — there's no way to message a user you haven't made/received an offer with.
