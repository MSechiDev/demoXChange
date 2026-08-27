# demoXChange Backend API — Frontend Integration Guide

Contract for the parts of the demoXChange backend that are live today: authentication, users, categories, reports, and messaging. Use this to build the frontend against the real behavior of the API — not a history of changes.

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

### `POST /api/auth/users` — ADMIN only
Creates a user directly (no self-registration endpoint exists yet).
Request:
```json
{ "username": "string (max 70)", "password": "string", "roles": ["USER"] }
```
- `password` must satisfy `StrongPassword`: ≥12 chars, at least one lowercase, one uppercase, one digit, one symbol, and not in a small common-password blocklist.
- `roles`: non-empty set, each value one of `GUEST|USER|ADMIN` (case-insensitive).

Response `201`:
```json
{ "id": 1, "username": "string", "enabled": true, "roles": ["USER"] }
```

## Error shape

Domain errors (business-rule violations raised explicitly by the service layer) return:
```json
{ "errorCode": "some_snake_case_code", "message": "human-readable message" }
```
with status `400` (`BadRequestException`), `404` (`NotFoundException`), or `409` (`ConflictException`). Each endpoint below lists its specific `errorCode`s.

Everything else falls back to Spring Boot defaults — **do not expect the `errorCode`/`message` shape** for these:
- `401` — missing/invalid/expired JWT.
- `403` — authenticated but `@PreAuthorize` denied (wrong role, or not the resource owner).
- `400` on `@Valid` failures (missing/malformed fields) — standard Spring validation error body, not `ApiError`.

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
`averageRating` is `null` (not `0`) when `reviewsCount` is `0`. `404 user_not_found` if the id doesn't exist.

## Categories — `/api/categories`

Public read, admin-only write. There is no `Item`/`Listing` API yet, so categories can't be filtered by usage — this is just the taxonomy CRUD.

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
Returns **all** categories (active and inactive) sorted by `name`. There is no `?active=` filter — if the public listing/search UI should hide inactive categories, filter client-side on `active` for now.

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
No body. Sets `active: false`, returns the updated `CategoryDto`. (Equivalent to a `PUT` with `active: false`, but doesn't require resending name/slug/description.)

### `PATCH /api/categories/{id}/activate` — ADMIN
Same as above, sets `active: true`.

## Reports — `/api/reports`

Lets a user report either another **user** or a **listing** (listings aren't buildable yet from the frontend since `Listing` has no API — but the field exists and works if a `reportedListingId` is valid). Admins review and close reports.

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
`?status=aperta` (optional, one of the enum values above) to filter. Oldest first (queue order). Omit for all statuses.

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

`201` with the created `ReportDto` (status `aperta`).

### `PUT /api/reports/{id}/review` — ADMIN
Request (`ReviewReportRequest`):
```json
{ "status": "risolta", "resolutionNote": "string | null (max 1000)" }
```
`status` must be one of `in_revisione|risolta|respinta` (an admin can't set it back to `aperta`).
`409 report_already_closed` if the report is already `risolta`/`respinta` — closed reports can't be reviewed again.
On success: sets `status`, `resolutionNote`, `reviewedBy` (the acting admin), `reviewedAt` (now).

## Messages — `/api/offers/{offerId}/messages` and `/api/messages`

Private chat between the two people in an offer negotiation: the **offerer** and the **listing owner**. There's no free-standing DM — a conversation only exists in the context of an offer. `Offers` themselves have no API yet (that's a teammate's feature), so you can't create the offer that starts a conversation from the frontend yet, but once one exists (e.g. seeded in the DB) messaging on it works end-to-end.

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
One entry per conversation you're part of (as offerer or listing owner): the most recent message in that thread, newest-conversation-first. Use it to render an inbox/conversation list.

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

## Not built yet

Auth, Users, Categories, Reports, and Messages have controllers right now. The rest of the app (per the team's feature list) has entities/repositories mapped but **no service or controller** — don't build UI against these until the owning teammate ships them:

- Listing publish/status/exchange preferences
- Search listings by category/price
- Item CRUD (insert/search/delete)
- Offers on listings (send, accept, counter-offer) — messaging above depends on this existing, so it's next in line
- Post-exchange reviews
- Item photo upload/reorder/delete
- Exchange completion confirmation (both sides)
- Admin aggregate view of open reports (a moderation queue/list beyond the single-report `GET /api/reports` above)

If you need one of these to unblock frontend work, check with the teammate who owns it rather than guessing the shape — this doc will get a new section once each one is actually implemented.
