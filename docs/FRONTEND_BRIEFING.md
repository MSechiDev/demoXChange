# demoXChange — Frontend Agent Briefing

Read this first if you're building or working on the frontend for this project. It gives you everything needed to start without having to explore the backend repo yourself. The authoritative API contract lives in **`docs/api/backend-api.md`** (same folder as this file) — read that in full before writing any API-calling code. This file gives the surrounding context that doc doesn't cover: what the project is, how to run it, how auth/CORS/uploads actually work end-to-end, the domain model, and what's still missing.

## What this project is

**demoXChange** is a barter/exchange marketplace: users list items they own and negotiate swapping them for other users' items (not money — `estimatedValue` on an item is informational, there's no payment flow anywhere). It's a backend built for a Generation Italy full-stack course, done as a group project with features split among teammates (see `README.MD` at repo root — Italian, marks each feature `FATTO` = done). All 14 planned features are implemented and have a working controller. There is **no frontend in this repository yet** — you are building it from scratch, in a separate project/folder, that talks to this backend over HTTP.

## Tech stack (backend, already built — do not need to touch)

- Java 26, Spring Boot 4.1.0, Gradle (Kotlin DSL)
- PostgreSQL (schema in `sql/schema.sql`, managed by hand — `ddl-auto: validate`, not `update`)
- Spring Security + OAuth2 Resource Server, stateless JWT (HS256)
- springdoc-openapi — **Swagger UI is live at `http://localhost:8080/swagger-ui.html`** once the backend is running; useful to poke endpoints interactively while building the frontend.

## Running the backend locally

```
./gradlew bootRun        # or gradlew.bat on Windows
```

Requires:
- PostgreSQL reachable at `jdbc:postgresql://localhost:5432/xChange_db` (override via `DB_URL`/`DB_USER`/`DB_PASS` env vars). In the maintainer's dev setup this runs in a Docker container.
- A `JWT_SECRET` env var, **at least 32 bytes**, or the app refuses to start (`SecurityConfig.jwtSecretKey`). No default — you must set one.
- Optional: `JWT_ISSUER` (default `xChange_db`), `JWT_TTL` (default `PT1H`, ISO-8601 duration), `UPLOADS_DIR` (default `uploads`, relative to working dir — where item photos are stored on disk), `CORS_ALLOWED_ORIGINS` (see below).

The app runs on the default Spring Boot port **8080**. All API routes are under `/api`.

## CORS — important for local dev

`app.cors.allowed-origins` defaults to **`http://localhost:4200`** (`src/main/resources/application.yaml`). That's the Angular CLI dev-server default port — a strong hint the course expects an Angular frontend, but nothing on the backend enforces that; any framework works as long as you either run your dev server on port 4200 or set the `CORS_ALLOWED_ORIGINS` env var to your frontend's origin when starting the backend. Only `Authorization` and `Content-Type` request headers are allowed; only `Authorization` is exposed in responses. `allowCredentials` is `false` — the frontend must not rely on cookies, only the bearer token, and must not send `credentials: 'include'`/`withCredentials: true` (CORS will reject the preflight if it does, since `Access-Control-Allow-Credentials` is never set true).

## Auth flow (what the frontend must implement)

1. `POST /api/auth/register` (public) → creates a user, returns the user (no token). Registration is fully open — the caller picks their own `roles` (`GUEST|USER|ADMIN`), there's no admin gate. Password must be ≥12 chars with lower+upper+digit+symbol.
2. `POST /api/auth/login` (public) → `{ token, roles }`. Store `token`; there is **no refresh endpoint** — when it expires (default 1h TTL) the user must log in again. No 401-refresh interceptor to build, just redirect to login on 401.
3. Every other `/api/**` call needs `Authorization: Bearer <token>`.
4. The JWT itself carries `sub` (username), `uid` (numeric user id), `roles` (array). **Decode the JWT client-side to get the current user's id** — there is no `/api/auth/me` or "my profile" endpoint. To show the logged-in user's own profile, decode `uid` from the token and call `GET /api/users/{uid}/profile`.
5. `POST /api/auth/logout` exists but is purely symbolic (stateless JWT) — logging out client-side (discarding the token) is sufficient; calling the endpoint is optional.
6. Two backend routes are public with no `Authorization` header needed: `GET /api/categories/**` and `GET /files/**` (item images). Everything else under `/api/**` requires the header.

## Error handling — one contract, one inconsistency to design around

Most business-rule failures return a JSON body `{ errorCode, message }` (`400/404/409/403`). But three classes of error **do not** have this shape and fall back to Spring Boot's default error body instead: invalid/expired JWT (`401`), role-based access denial via `@PreAuthorize` (`403`), and `@Valid` bean-validation failures on request DTOs (`400`, e.g. a missing required field). Design your HTTP client's error handler to branch on status code first, and only try to read `errorCode`/`message` when the body actually has that shape — don't assume every non-2xx response is a structured `ApiError`. Full details and the exact `errorCode` per endpoint are in `docs/api/backend-api.md`.

One casing trap: most `errorCode`s are `snake_case` (`user_not_found`) but the ones thrown by `ListingService` are `UPPER_SNAKE_CASE` (`LISTING_NOT_FOUND`). Match on the literal string, don't normalize case yourself and assume it'll match.

## File uploads (item photos)

- `POST /api/items/{itemId}/images` — `multipart/form-data`, field name must be exactly `file`. Max 5MB per file (`max-file-size`), max 10 images per item (server-enforced, `409 too_many_images`). Accepted: JPEG/PNG/WEBP, validated by file content not the `Content-Type` header you send.
- Uploaded images are served back publicly (no auth) at the `url` field returned in `ItemImageDto`, e.g. `GET http://localhost:8080/files/items/5/ab12cd34.jpg` — these are relative paths, prefix with the backend origin to render `<img>` tags.
- Reordering is drag-and-drop-friendly: `PATCH /api/items/{itemId}/images/order` takes the full new-order array of image ids (must include every existing id exactly once).

## Domain model / how the pieces connect

```
AppUser --owns--> Item --optionally published as--> Listing --receives--> Offer --once approved--> Exchange --once both sides confirm--> (both leave) Review
                     |                                  |
                     +--assigned to--> Category          +--negotiation thread--> Message (chat scoped to one Offer negotiation)

AppUser --can file--> Report (against a user OR a listing, not both)
```

Key lifecycle facts a UI needs to reflect correctly:
- An **Item** is independent of a **Listing** — you create/manage items (with photos) first, then optionally publish one in a listing. An item can only be in one *active* listing at a time.
- **Listing.status**: `attivo` (open, searchable) → `in_trattativa` (an offer was just accepted, no new offers accepted) → `scambiato` (exchange completed) or `eliminato` (owner manually retired it). If the exchange on it is cancelled, it automatically reverts to `attivo`. Only `GET /api/listings` (browse-all) filters server-side to `attivo`; `/api/listings/mine` shows all your own regardless of status.
- **Offer.status**: `in_attesa` → `accettata` (approved) / `rifiutata` (auto-rejected — happens to every *other* pending offer on a listing when one gets approved) / `controproposta` (owner counter-offered — a *new* offer row is created, chained via `parentOfferId`) / `annullata`.
- Approving an offer auto-creates the **Exchange** — there's no manual creation endpoint for it.
- **Exchange.status**: `in_corso` → `completato` (both `ownerConfirmedAt` and `offererConfirmedAt` set) or `annullato` (either side can cancel unilaterally while in progress — reverts the listing to `attivo`).
- **Message** threads are scoped to one offer negotiation (offerer ↔ listing owner), not free-standing DMs. Any offer id in a counter-offer chain resolves server-side to the same root thread — use the `offerId` field the server echoes back (always the root) as your thread key, not whatever id you called with.
- **Review**: one per (exchange, author) — each of the two exchange participants can leave exactly one review of the other, only after `completato`. Reviews aren't listed by their own GET; they surface via the recipient's `GET /api/users/{id}/profile`.
- **Report**: targets exactly one of a user or a listing. Only one open report (`aperta`/`in_revisione`) per reporter+target at a time.

All enum values that go over the wire are **Italian strings** (e.g. `itemCondition: buone`, `listing status: attivo`) — don't translate them client-side when sending requests, only when displaying labels to the user.

## Roles

`GUEST | USER | ADMIN`, carried in the JWT `roles` claim. In practice:
- Browsing/searching listings and categories, viewing profiles: any authenticated `USER` (categories GET is fully public, no auth).
- Everything item/listing/offer/exchange/review/report/message-related: any authenticated user acting on their own resources — ownership is enforced in the service layer, not by role.
- Category create/update/activate/deactivate and `GET /api/reports` (the full moderation queue) + `PATCH /api/reports/{id}/review`: `ADMIN` only, enforced via `@PreAuthorize` (denial returns a bare 403, no `errorCode` — see error handling above).
- `GUEST` exists as an enum value but nothing in the backend currently gates behavior on it specifically.

## Repo map (backend, for reference if you need to check exact behavior)

- `src/main/java/.../controllers/` — one controller per resource (`AuthController`, `UserController`, `CategoryController`, `ItemController`, `ItemImageController`, `ListingController` — also hosts `POST .../offers`, `OfferController`, `ExchangeController`, `ReviewController`, `ReportController`, `MessageController`)
- `src/main/java/.../model/dto/` — every request/response shape; these map 1:1 to what's documented in `docs/api/backend-api.md`
- `src/main/java/.../model/entities/` — JPA entities + enums (`ListingStatus`, `OfferStatus`, `ExchangeStatus`, `ItemCondition`, `ReportReason`, `ReportStatus`, `UserRole`) — the enum source of truth if the API doc is ever ambiguous about exact values
- `src/main/java/.../security/` — JWT/CORS config, `SecurityConfig.java` is the single source of truth for what's public vs authenticated vs admin-only
- `sql/schema.sql` — full DB schema snapshot, useful if you need to understand a relationship the DTOs don't make obvious
- `docs/api/backend-api.md` — the full endpoint-by-endpoint contract (request/response bodies, every error code, query params)

## Known gaps (don't build UI assuming these are handled server-side)

- No endpoint to update a user's email or any profile field after registration.
- Reporting your own listing isn't blocked (only self-reporting a *user* is) — if this matters for UX, hide the "report" action client-side when `listing.ownerId === currentUserId`.
- A self-registering user can request `ADMIN` for themselves — nothing stops them. Whether/how to expose a role picker on the registration form (or hide it and hardcode `USER`) is a frontend product decision, not a backend constraint.
- No notifications system (no `notifications` table/endpoint) — new offer / offer accepted / new message / report resolved events aren't pushed anywhere; if you want a "new activity" indicator you'll need to poll (e.g. `GET /api/messages/mine?unreadOnly=true`, `GET /api/offers/received`) rather than expect a websocket/SSE push.
