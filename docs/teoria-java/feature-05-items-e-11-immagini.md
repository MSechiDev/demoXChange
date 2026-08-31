# Feature 5 (Item CRUD) e Feature 11 (Foto degli oggetti) — teoria

Documento di riferimento sui due strati verticali `Item` e `ItemImage`.
Stesso schema a livelli usato in tutto il progetto:

```
HTTP → SecurityFilterChain (valida il JWT) → Controller (HTTP ↔ chiamate)
     → Service (regole di dominio) → Repository (solo query) → DB
Eccezioni di dominio → ApiExceptionHandler → { errorCode, message } + 400/404/409
```

Ogni strato ha una sola responsabilità e non sconfina in quella accanto. Le entità JPA
non escono mai dal service: al confine HTTP passano solo i DTO (`record`).

---

# Feature 5 — Inserimento oggetti, ricerca, cancellazione

Gestione CRUD degli `Item` di proprietà dell'utente autenticato: ricerca con filtri,
dettaglio, creazione, modifica, cancellazione (soft-delete = archiviazione).

File coinvolti:

| Strato | File |
|---|---|
| Controller | `controllers/ItemController.java` |
| Service | `services/ItemService.java` |
| Repository | `model/repositories/ItemRepository.java` |
| DTO | `model/dto/CreateItemRequest.java`, `UpdateItemRequest.java`, `ItemDto.java` |
| Entità | `model/entities/Item.java`, `ItemCondition.java`, `Category.java` |

## Passo 1 — Il contratto dell'API

Base path `/api/items`; tutto sotto `/api/**` richiede JWT (`SecurityConfig`).

| Metodo | Path | Input | Output | Status |
|---|---|---|---|---|
| `GET` | `/api/items` | query param `categoryId`, `condition`, `minValue`, `maxValue`, `q`, `includeArchived` (tutti opzionali) | `List<ItemDto>` | `200` |
| `GET` | `/api/items/{id}` | path `id` | `ItemDto` | `200`, `404` se non tuo/inesistente |
| `POST` | `/api/items` | body `CreateItemRequest` | `ItemDto` creato | `201`, `400`, `404` categoria |
| `PUT` | `/api/items/{id}` | path `id` + body `UpdateItemRequest` | `ItemDto` aggiornato | `200`, `400`, `404` |
| `DELETE` | `/api/items/{id}` | path `id` | vuoto | `204`, `404` |

Decisioni di design:

- **Scope per proprietario**: ogni endpoint opera solo sugli item dell'utente che chiama.
  Per questo in tutti i metodi passa `currentUserId(jwt)`.
- **Delete = soft delete**: `DELETE` non cancella la riga, mette `archived = true`. Così
  offerte/scambi storici che puntano all'item non si rompono. Da qui il flag
  `includeArchived` sulla ricerca.
- **Errori uniformi**: si riusano `NotFoundException` / `BadRequestException` che
  `ApiExceptionHandler` traduce in `404` / `400` con corpo `ApiError(errorCode, message)`.
- **Codici di stato espliciti**: `201` sul create, `204` sul delete, via `@ResponseStatus`.

## Passo 2 — Repository (`ItemRepository`)

```java
Optional<Item> findByIdAndOwnerId(Long id, Long ownerId);
```

Derived query di Spring Data: `WHERE id = ? AND owner_id = ?`. È il mattone della sicurezza
a livello dati: se l'item esiste ma è di un altro utente, torna `Optional.empty()` come se
non esistesse → il service lancia `404` e non rivela l'esistenza dell'oggetto altrui.

```java
@Query("""
        select i from Item i
        where i.owner.id = :ownerId
          and (:categoryId is null or i.category.id = :categoryId)
          and (:condition is null or i.itemCondition = :condition)
          and (:includeArchived = true or i.archived = false)
          and (:minValue is null or (i.estimatedValue is not null and i.estimatedValue >= :minValue))
          and (:maxValue is null or (i.estimatedValue is not null and i.estimatedValue <= :maxValue))
          and (:q is null
               or lower(i.title) like lower(concat('%', :q, '%'))
               or lower(i.description) like lower(concat('%', :q, '%')))
        order by i.createdAt desc
        """)
List<Item> search(...);
```

Pattern chiave: **`:param is null or <condizione>`** ripetuto per ogni filtro. Un unico JPQL
che si adatta: se il parametro è `null`, quel `AND` diventa sempre vero e il filtro sparisce.
Evita di costruire query dinamiche (Criteria API / Specifications) per un caso semplice.

- `i.owner.id = :ownerId` è **sempre** presente e non annullabile.
- `:includeArchived = true or i.archived = false` → di default nascondi gli archiviati.
- Match su `q` case-insensitive su titolo **o** descrizione.
- Il binding dei parametri è per nome (`@Param`), non per posizione: l'ordine nella firma
  del metodo non deve combaciare con l'ordine nel JPQL, ma **deve** combaciare con l'ordine
  della chiamata dal service.

## Passo 3 — DTO

- **`CreateItemRequest`** — cosa il client deve mandare per creare. Annotazioni Jakarta
  Validation come primo filtro: `@NotBlank @Size(max = 120)` su `title` (allineato alla
  colonna DB), `@PositiveOrZero @Digits(integer = 8, fraction = 2)` su `estimatedValue`
  (che, senza `@NotNull`, è opzionale).
- **`UpdateItemRequest`** — come create **più** `boolean archived`. Semantica PUT = replace:
  il client manda lo stato completo desiderato. Niente `ownerId`: il proprietario non cambia.
- **`ItemDto`** — cosa il server restituisce. Include roba derivata utile al frontend senza
  chiamate extra: `categoryName` (non solo l'id), i timestamp, il flag `archived`, e — dopo
  la feature 11 — la lista `images`.

`record`: immutabili, niente boilerplate, solo contenitori di dati.

## Passo 4 — Service (`ItemService`)

Dipendenze iniettate via costruttore: `ItemRepository`, `CategoryRepository`,
`AppUserRepository`.

- **`search(...)`** — `@Transactional(readOnly = true)`. Valida ciò che il DB non può
  (range prezzo `min <= max` → `BadRequestException("invalid_price_range", ...)`),
  normalizza `q` (vuoto/solo spazi → `null` così il filtro si disattiva), delega al
  repository, mappa `Item → ItemDto`. La transazione tiene aperta la sessione mentre `toDto`
  naviga le relazioni lazy (`getCategory().getName()`), evitando `LazyInitializationException`.
- **`findByIdForOwner(id, ownerId)`** — `toDto(getOwnedOrThrow(id, ownerId))`. È il punto in
  cui "non tuo" diventa `404`.
- **`create(ownerId, request)`** — `@Transactional`. `getActiveCategoryOrThrow` (categoria
  esistente → `404 category_not_found`, e attiva → `400 category_inactive`); carica l'`AppUser`
  reale per la FK; `.trim()` su titolo/descrizione; `createdAt/updatedAt` li mette l'entità
  in `@PrePersist`.
- **`update(id, ownerId, request)`** — `@Transactional`. Prima `getOwnedOrThrow` (`404`).
  Ricarica la categoria **solo se è cambiata** (micro-ottimizzazione). Aggiorna tutti i
  campi (PUT full-replace), incluso `archived` → via `PUT` si può anche disarchiviare.
  `saveAndFlush` per forzare subito l'UPDATE e vedere `updatedAt` fresco in `toDto`.
- **`delete(id, ownerId)`** — soft delete: `getOwnedOrThrow`, `setArchived(true)`, `save`.

Helper privati: `getOwnedOrThrow` (esiste ed è mio), `getActiveCategoryOrThrow` (esiste e
attiva), `toDto` (`static`, mapping puro entità → DTO).

## Passo 5 — Controller (`ItemController`)

`@RestController`, `@RequestMapping("/api/items")`, iniezione del solo `ItemService`.

- `@RequestParam(required = false)` → parametro mancante = `null` → il service disattiva il
  filtro. Spring converte da stringa: `"12"` → `Long`, `"nuovo"` → enum `ItemCondition`
  (`condition=pippo` → `400` automatico), `"9.99"` → `BigDecimal`.
- `@RequestBody` deserializza il JSON nel record; `@Valid` esegue le annotazioni di
  validazione prima di entrare nel metodo (fallite → `400`).
- `@ResponseStatus(HttpStatus.CREATED)` → `201`; `void` + `NO_CONTENT` → `204`.

## Passo 6 — Utente corrente dal JWT

- **Creazione** (`AuthService.login` → `JwtService.createToken`): dopo che
  `authenticationManager.authenticate` ha verificato username/password, si costruisce un
  `JwtClaimsSet` con `subject = username`, `claim("uid", user.getId())`,
  `claim("roles", ...)`, firmato HMAC-SHA256 con `app.jwt.secret`.
- **Verifica** (ogni richiesta): `oauth2ResourceServer(...jwt...)` in `SecurityConfig`.
  Spring legge `Authorization: Bearer <token>`, valida firma e scadenza col `JwtDecoder`.
  Non valido → `401`, il controller non parte nemmeno. I `roles` diventano authority `ROLE_*`.
- **Lettura**: `@AuthenticationPrincipal Jwt jwt` inietta il token validato;
  `jwt.getClaim("uid")` rilegge l'id. Passato al service come `ownerId` in ogni operazione:
  `GET /api/items/999` di un item altrui → `findByIdAndOwnerId` non lo trova → `404`.

## Passo 7 — Prova manuale

Prerequisiti: Postgres attivo, `JWT_SECRET` impostata (≥ 32 byte), schema già presente
(`ddl-auto: validate`), almeno una categoria attiva e un utente per il login.
Swagger: `http://localhost:8080/swagger-ui.html`.

```bash
# login
curl -s -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"<password>"}'
# -> { "token": "eyJ...", "roles": ["ADMIN"] }

TOKEN='eyJ...'

# create
curl -s -X POST http://localhost:8080/api/items \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"categoryId":1,"title":"Bici da corsa","description":"Carbonio","estimatedValue":750.00,"itemCondition":"come_nuovo"}'

# search
curl -s "http://localhost:8080/api/items?q=bici&condition=come_nuovo&minValue=100&maxValue=1000" \
  -H "Authorization: Bearer $TOKEN"

# update / delete
curl -s -X PUT   http://localhost:8080/api/items/1 -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d '{...,"archived":false}'
curl -s -i -X DELETE http://localhost:8080/api/items/1 -H "Authorization: Bearer $TOKEN"   # 204
```

Casi d'errore: `title` vuoto/>120 → `400`; `estimatedValue: -5` → `400`;
`itemCondition` fuori enum → `400`; `categoryId` inesistente → `404`; categoria inattiva →
`400`; nessun header `Authorization` → `401`; `minValue>maxValue` → `400 invalid_price_range`;
id di un altro utente → `404`.

---

# Feature 11 — Gestione foto degli oggetti

Upload, ordinamento e cancellazione delle immagini di un `Item`. **Strada B**: il file
arriva al nostro backend e lo salviamo in una cartella sul server; nella colonna
`ItemImage.url` mettiamo un path pubblico servito staticamente.

## Relazione con le altre feature

- **Feature 5 (Items)** — estensione diretta. Riusa `Item`,
  `ItemRepository.findByIdAndOwnerId`, il pattern `currentUserId(jwt)`, le eccezioni +
  `ApiExceptionHandler`. Unico ritocco invasivo: il campo `List<ItemImageDto> images` in
  `ItemDto`, popolato in `ItemService.toDto` da `item.getImages()` (la relazione
  `@OneToMany` con `@OrderBy("displayOrder ASC")` esisteva già sull'entità `Item`).
- **Feature 3 (annunci/Listing)** e **Feature 4 (ricerca annunci)** — consumano i dati:
  mostrano la copertina (`displayOrder = 0`). Nessun accoppiamento di codice nuovo.
- **Feature 8/9 (offerte/recensioni)** — solo indiretto (il compratore guarda le foto).
- **Item archiviati** — la gestione foto resta permessa (il proprietario fa pulizia).
- **`build.gradle`** — niente da aggiungere: `spring-boot-starter-webmvc` abilita già il
  multipart.

## Passo 1 — Il contratto dell'API

Endpoint annidati sotto l'item: **`/api/items/{itemId}/images`**. Annidare fa passare il
controllo di proprietà sempre per lo stesso `itemId`.

| Metodo | Path | Input | Output | Status |
|---|---|---|---|---|
| `GET` | `/api/items/{itemId}/images` | — | `List<ItemImageDto>` ordinata per `displayOrder` | `200` |
| `POST` | `/api/items/{itemId}/images` | `multipart/form-data`, parte **`file`** | `ItemImageDto` (accodato: `displayOrder = count`) | `201` |
| `PATCH` | `/api/items/{itemId}/images/order` | JSON `{ "imageIds": [7,3,5] }` = ordine completo | `List<ItemImageDto>` riordinata | `200` |
| `DELETE` | `/api/items/{itemId}/images/{imageId}` | — | vuoto | `204` |

Status di errore (riusano le eccezioni esistenti):

| Situazione | Eccezione | HTTP |
|---|---|---|
| Nessun/invalid JWT | (automatico) | `401` |
| Item inesistente o non tuo | `NotFoundException("item_not_found")` | `404` |
| Immagine inesistente o non di quell'item | `NotFoundException("image_not_found")` | `404` |
| File vuoto / tipo non ammesso | `BadRequestException("invalid_file")` | `400` |
| File oltre il limite di dimensione | `MaxUploadSizeExceededException` (gestita in `ApiExceptionHandler`) | `400` (`file_too_large`) |
| `imageIds` non è una permutazione esatta | `BadRequestException("invalid_image_order")` | `400` |
| Superato il tetto massimo | `ConflictException("too_many_images")` | `409` |

Decisioni fissate (9):

1. Nome della parte multipart: `file`.
2. Tipi ammessi: `image/jpeg`, `image/png`, `image/webp`. Dimensione max: 5 MB per file.
3. Tetto massimo immagini per item: 10 → oltre, `409`.
4. Layout su disco: `${app.uploads.dir}/items/{itemId}/{uuid}.{ext}`; in `url` salviamo
   `/files/items/{itemId}/{uuid}.{ext}`. `{uuid}` generato da noi (`UUID.randomUUID()`),
   mai `file.getOriginalFilename()` (path traversal); `{ext}` derivata dal content-type.
5. `DELETE` = hard delete (riga DB + file). Diverso dal soft-delete dell'item: un file/
   riferimento orfano non ha valore storico.
6. Riordino: il client manda la lista completa e ordinata degli id; il server riassegna
   `displayOrder` = 0,1,2,… La copertina è `displayOrder == 0` (nessun flag separato).
7. Ownership: ogni endpoint verifica che l'item sia dell'utente corrente
   (`itemRepository.findByIdAndOwnerId` + `currentUserId(jwt)`); l'immagine dev'essere anche
   di quell'item (`findByIdAndItemId`).
8. Item archiviati: gestione foto permessa comunque.
9. `ItemDto` guadagna `List<ItemImageDto> images`, popolato in `ItemService.toDto`.

## Passo 2 — Config & storage (il costo della Strada B)

### `application.yaml`

```yaml
spring:
  servlet:
    multipart:
      max-file-size: 5MB      # tetto per singolo file
      max-request-size: 6MB   # un filo piu' grande, per l'overhead del multipart
app:
  uploads:
    dir: ${UPLOADS_DIR:uploads}   # cartella relativa alla working dir
```

`.gitignore`: aggiunto `uploads/`.

### `AppUploadsProperties` (`security/`)

```java
@ConfigurationProperties(prefix = "app.uploads")
public record AppUploadsProperties(String dir) {}
```

Stesso pattern di `AppJwtProperties` / `AppCorsProperties`. Registrato in `SecurityConfig`:
`@EnableConfigurationProperties({AppJwtProperties.class, AppCorsProperties.class, AppUploadsProperties.class})`.

### `StaticResourcesConfig` (`config/`)

```java
@Configuration
public class StaticResourcesConfig implements WebMvcConfigurer {
    private final AppUploadsProperties props;
    // costruttore...

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path uploadDir = Paths.get(props.dir()).toAbsolutePath().normalize();
        registry.addResourceHandler("/files/**")
                .addResourceLocations(uploadDir.toUri().toString());
    }
}
```

Mappa `GET /files/**` alla cartella fisica degli upload. `toUri().toString()` produce
`file:///percorso/assoluto/uploads/` con lo slash finale (necessario perché Spring lo tratti
come directory) — più robusto della concatenazione di stringhe.

### `SecurityConfig` — matcher esplicito

```java
.requestMatchers(HttpMethod.GET, "/files/**").permitAll()
```

Funzionalmente `/files/**` cade già in `.anyRequest().permitAll()` (non è sotto `/api/**`),
ma renderlo esplicito documenta l'intenzione e protegge da un futuro irrigidimento di
`anyRequest()`.

### `ApiExceptionHandler` — file troppo grande

```java
@ExceptionHandler(MaxUploadSizeExceededException.class)
public ResponseEntity<ApiError> handleTooLarge(MaxUploadSizeExceededException ex) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(new ApiError("file_too_large", "File exceeds the maximum allowed size"));
}
```

Spring lancia questa eccezione **prima** di entrare nel controller quando il file supera
`max-file-size`. Senza handler il client riceverebbe un `500` generico.

### `ImageStorageService` (`services/`)

Si occupa **solo** del file su disco: validazione tipo, scrittura, cancellazione. Non
conosce entità né regole di dominio.

```java
private static final String PUBLIC_PREFIX = "/files/";
private static final Map<String, String> ALLOWED_TYPES = Map.of(
        "image/jpeg", "jpg", "image/png", "png", "image/webp", "webp");
private final Path root;   // = Paths.get(props.dir()).toAbsolutePath().normalize()

public String store(long itemId, MultipartFile file) {
    String ext = validateAndGetExtension(file);          // invalid_file -> 400
    String filename = UUID.randomUUID() + "." + ext;     // nome generato da noi
    Path itemDir = root.resolve("items").resolve(String.valueOf(itemId)).normalize();
    Files.createDirectories(itemDir);
    try (InputStream in = file.getInputStream()) {
        Files.copy(in, itemDir.resolve(filename), StandardCopyOption.REPLACE_EXISTING);
    }
    return PUBLIC_PREFIX + "items/" + itemId + "/" + filename;
}

public void delete(String publicUrl) {
    if (publicUrl == null || !publicUrl.startsWith(PUBLIC_PREFIX)) return;
    Path target = root.resolve(publicUrl.substring(PUBLIC_PREFIX.length())).normalize();
    if (!target.startsWith(root)) return;                // guardia path traversal
    Files.deleteIfExists(target);
}
```

Punti chiave:

- `root` è lo **stesso** path assoluto normalizzato usato da `StaticResourcesConfig` → ciò
  che scrivi qui è esattamente ciò che viene servito da `/files/**`.
- Nome file = UUID generato da noi; `file.getOriginalFilename()` non viene mai toccato.
- Estensione dal **content-type** via whitelist, non dal nome file del client.
- `delete` ha due guardie: path che inizia con `/files/`, e path che dopo `normalize()`
  resta dentro `root` (blocca `../`).
- Dimensione massima: la impone Spring col multipart config, prima di arrivare qui.
- `IOException` → `UncheckedIOException` (diventa `500`): errore vero del server.
- Limite noto: `getContentType()` è dichiarato dal client e teoricamente falsificabile; un
  controllo più forte ispezionerebbe i primi byte (magic number).

## Passo 3 — Repository (`ItemImageRepository`)

```java
List<ItemImage> findByItemIdOrderByDisplayOrderAsc(Long itemId);
Optional<ItemImage> findByIdAndItemId(Long id, Long itemId);
long countByItemId(Long itemId);
```

- **`findByItemIdOrderByDisplayOrderAsc`** — tutte le immagini di un item, già ordinate.
  `ByItemId` naviga la relazione `item.id` (come `i.owner.id` nel JPQL della feature 5).
  `OrderBy...Asc` è nella query perché qui carichi dal repository, non da `item.getImages()`.
  Usato in `GET .../images` e nel `PATCH .../order`.
- **`findByIdAndItemId`** — una singola immagine, ma solo se appartiene a quell'item.
  Secondo livello di sicurezza: `DELETE /api/items/5/images/99` dove la 99 è di un altro
  item → `Optional.empty()` → `404 image_not_found`. Usato in `DELETE`.
- **`countByItemId`** — doppio uso: tetto massimo (`count >= 10` → `409`) e prossimo
  `displayOrder` (siccome `delete` ricompatta gli ordini a `0..n-1`, `count` è l'indice
  libero successivo). Ritorna `long` (mai `null`). Usato in `POST`.

**Vincoli dello schema `item_images` da tenere presenti** (li impone il DB, gestito a mano
con `ddl-auto: validate`):

| Vincolo | Effetto sul codice |
|---|---|
| `UNIQUE (item_id, display_order)` non differibile | non si può permutare `display_order` riga per riga → vedi `applyOrder` al Passo 5 |
| `CHECK (display_order BETWEEN 0 AND 9)` | coincide con `MAX_IMAGES_PER_ITEM = 10`; niente "parcheggio" fuori range |
| `id GENERATED ALWAYS AS IDENTITY` | per reinserire con id fissato serve `INSERT ... OVERRIDING SYSTEM VALUE` |
| FK `item_id` con `ON DELETE CASCADE` | cancellando un `Item` sparirebbero anche le sue immagini (oggi l'item fa solo soft-delete) |

## Passo 4 — DTO

### `ItemImageDto` (risposta)

```java
public record ItemImageDto(long id, long itemId, String url, short displayOrder, OffsetDateTime createdAt) {}
```

Unica forma in cui un'immagine esce dall'API (mai l'entità `ItemImage`, che trascinerebbe
`item → owner → category` e rischio `LazyInitializationException`).

- `id` — per il `DELETE` e per costruire `imageIds` nel riordino.
- `itemId` — piatto (`long`), non l'oggetto. Utile quando le immagini arrivano annidate in
  `ItemDto`.
- `url` — il path pubblico `/files/items/5/ab12.jpg`; il frontend ci antepone l'host in
  `<img src>`.
- `displayOrder` — posizione (0 = copertina).
- `createdAt` — niente `updatedAt`: un'immagine non si modifica, si cancella e si ricarica.

Nessuna annotazione di validazione: si valida l'input, non l'output.

### `ReorderImagesRequest` (input del riordino)

```java
public record ReorderImagesRequest(@NotEmpty List<Long> imageIds) {}
```

Body di `PATCH .../order`. `{ "imageIds": [7,3,5] }` = "7 in posizione 0, 3 in 1, 5 in 2".

- `@NotEmpty` — lista non `null` e non vuota; se salta, `400` via `@Valid` prima del metodo.
- Quello che `@NotEmpty` **non** può controllare (lo fa il service): che gli id siano
  *esattamente* l'insieme delle immagini di quell'item — niente mancanti, estranei,
  duplicati → `BadRequestException("invalid_image_order", ...)`.
- Solo id, non oggetti: il riordino non cambia nessun altro dato.
- `PATCH` e non `PUT`: modifica parziale di una collezione esistente.

### `ItemDto` — modifica accoppiata a `ItemService.toDto`

Aggiunto `List<ItemImageDto> images`. Siccome `ItemDto` è un `record`, aggiungere un campo
rompe la chiamata `new ItemDto(...)` in `ItemService.toDto`: le due modifiche si fanno
insieme (Passo 5), così ogni passo resta compilabile.

## Passo 5 — Service

### `ItemService.toDto` (modifica)

```java
List<ItemImageDto> images = item.getImages().stream()
        .map(ItemService::toImageDto)
        .toList();
```

`item.getImages()` è la relazione `@OneToMany` LAZY con `@OrderBy("displayOrder ASC")` già
presente su `Item` → arriva ordinata. È LAZY, ma tutti i chiamanti di `toDto` (`search`,
`findByIdForOwner`, `create`, `update`) girano in `@Transactional` → si carica senza
`LazyInitializationException`. Risultato: `GET /api/items` e `GET /api/items/{id}` mostrano
le foto senza endpoint extra. `toImageDto` è un mapping puro `static`.

### `ItemImageService` (nuovo)

Dipendenze: `ItemRepository` (ownership), `ItemImageRepository`, `ImageStorageService`.
Non parla mai col filesystem direttamente: delega a `ImageStorageService`.
Costante `MAX_IMAGES_PER_ITEM = 10`.

**`list(itemId, ownerId)`** — `@Transactional(readOnly = true)`.
`getOwnedItemOrThrow` (→ `404` se non tuo), poi `findByItemIdOrderByDisplayOrderAsc` → DTO.

**`add(itemId, ownerId, file)`** — `@Transactional`. Controlli dal più economico al più
costoso:
1. ownership sull'item;
2. `countByItemId` → se `>= 10`, `ConflictException("too_many_images")` (prima di scrivere
   byte inutili);
3. `imageStorageService.store(...)` — dentro avviene la validazione tipo (`invalid_file`) e
   la scrittura; ritorna `/files/items/{itemId}/{uuid}.ext`;
4. `displayOrder` della nuova = `current` (il conteggio): accodata. Funziona perché `delete`
   ricompatta gli ordini a `0..n-1`;
5. `save` → DTO.
   Limite noto: se il `save` fallisce dopo la scrittura, il file resta orfano (in
   produzione: `try/catch` che chiama `imageStorageService.delete(url)`).

**`reorder(itemId, ownerId, imageIds)`** — `@Transactional`.
ownership → carica le immagini reali → `validateReorder` → costruisce la lista di entità
nell'ordine richiesto → `applyOrder(itemId, ordered)` → rilegge e restituisce la lista
riordinata. Non tocca file su disco.

**`delete(itemId, ownerId, imageId)`** — `@Transactional`.
ownership → `findByIdAndItemId` (→ `404 image_not_found` se non di quell'item) → salva `url`
in una variabile → `delete` della riga (hard delete) → `repackDisplayOrder` (richiude il
buco negli ordini) → **cancella il file per ultimo**. Se qualcosa lato DB esplode, il
rollback ripristina la riga e il file è ancora lì → stato coerente.

### `applyOrder` — perché non un semplice `saveAll`

La tabella `item_images` ha `UNIQUE (item_id, display_order)` **non differibile** e
`CHECK (display_order BETWEEN 0 AND 9)`. Aggiornare `display_order` riga per riga
(`saveAll`) rompe il vincolo su uno stato intermedio: scambiare la posizione 0 con la 1
crea per un istante due righe a `display_order = 1`. E PostgreSQL verifica i vincoli UNIQUE
non differibili **riga per riga, anche dentro un singolo statement** — quindi non basta
nemmeno un `UPDATE ... CASE` (quel trucco funziona su MySQL, non su PostgreSQL). Il `CHECK`
0..9 impedisce anche di "parcheggiare" temporaneamente gli ordini fuori range.

Soluzione: `applyOrder` **svuota le righe dell'item e le reinserisce** nell'ordine voluto,
con SQL nativo, preservando `id` e `created_at` (`INSERT ... OVERRIDING SYSTEM VALUE` perché
`id` è `GENERATED ALWAYS AS IDENTITY`). Dopo il `DELETE` tutti gli slot sono liberi, quindi
i reinserimenti non collidono. Chiude con `entityManager.clear()` perché le entità caricate
prima hanno ora un `display_order` disallineato. Costo O(n) con n ≤ 10: accettabile.

Nota: il `DELETE` della singola immagine in `delete()` viene mandato al DB dall'auto-flush
che precede la query nativa di `applyOrder`, quindi non serve un `flush()` esplicito.

**Helper:**
- `getOwnedItemOrThrow(itemId, ownerId)` — `itemRepository.findByIdAndOwnerId(...)` +
  `orElseThrow(NotFoundException("item_not_found"))`. Chiamato da tutti e 4 i metodi
  pubblici: nessun endpoint sfugge al controllo di proprietà.
- `validateReorder(existing, imageIds)` — tre controlli che `@NotEmpty` non può fare:
  niente `null` nella lista (altrimenti `Set.copyOf` → NPE → `500`); niente duplicati
  (`Set` più piccolo della lista); stesso identico insieme degli id esistenti (blocca
  mancanti ed estranei — deve essere una permutazione).
- `repackDisplayOrder(itemId)` — rilegge le immagini rimaste in ordine e delega a
  `applyOrder` per riscrivere `display_order = 0,1,2,…` senza buchi. Solo da `delete`.
- `applyOrder(itemId, ordered)` — vedi riquadro sopra: `DELETE` + reinserimento nativo,
  usato sia da `reorder` sia da `repackDisplayOrder`.
- `toDto(ItemImage)` — mapping puro `static` (duplicato rispetto a `ItemService.toImageDto`:
  è la convenzione del progetto, ogni service ha il suo `toDto`).

## Passo 6 — Controller (`ItemImageController`)

`@RestController`, `@RequestMapping("/api/items/{itemId}/images")` → `{itemId}` è a livello
di classe, ogni metodo lo riceve con `@PathVariable long itemId`. Iniezione del solo
`ItemImageService`.

- **`GET`** → `list(...)`. `200` con array (vuoto se non ce ne sono).
- **`POST`** `consumes = MediaType.MULTIPART_FORM_DATA_VALUE` + `@ResponseStatus(CREATED)`.
  `@RequestParam("file") MultipartFile file` (non `@RequestBody`: il file è una *parte* del
  form; `"file"` è il nome deciso al Passo 1). Se il client manda JSON → `415`; se la parte
  manca → `400`. Nessun `@Valid` (l'input è il file). `consumes` fa anche mostrare a Swagger
  il file picker.
- **`PATCH /order`** → `@Valid @RequestBody ReorderImagesRequest`. Path `/order` per non
  collidere con `DELETE /{imageId}`. Restituisce la lista riordinata (`200`).
- **`DELETE /{imageId}`** → `void` + `@ResponseStatus(NO_CONTENT)` → `204`. Due
  `@PathVariable`: `itemId` (classe) e `imageId`.

## Passo 6b — Utente corrente dal JWT

```java
private static long currentUserId(Jwt jwt) { return jwt.getClaim("uid"); }
```

**Identico** alla feature 5. Spring Security ha già validato il Bearer token;
`@AuthenticationPrincipal Jwt jwt` lo inietta decodificato; `getClaim("uid")` rilegge l'id
utente messo dentro al login. Diventa l'`ownerId` passato al service in ogni chiamata →
nessuno gestisce le foto di un item altrui, nemmeno cambiando gli id nell'URL.
`currentUserId` è duplicato tra `ItemController` e `ItemImageController` (7 righe):
accettabile, coerente con lo stile del progetto.

## Passo 7 — Prova manuale

```bash
TOKEN='eyJ...'   # da POST /api/auth/login
ITEM=1           # da POST /api/items

# upload (parte "file")
curl -s -X POST "http://localhost:8080/api/items/$ITEM/images" \
  -H "Authorization: Bearer $TOKEN" -F "file=@/percorso/foto1.jpg"
# -> 201 { "id": 1, "itemId": 1, "url": "/files/items/1/....jpg", "displayOrder": 0, ... }

# il file e' raggiungibile senza token
open "http://localhost:8080/files/items/1/....jpg"

# lista
curl -s "http://localhost:8080/api/items/$ITEM/images" -H "Authorization: Bearer $TOKEN"

# riordino (id nell'ordine desiderato)
curl -s -X PATCH "http://localhost:8080/api/items/$ITEM/images/order" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"imageIds":[3,1,2]}'

# cancellazione
curl -s -i -X DELETE "http://localhost:8080/api/items/$ITEM/images/1" -H "Authorization: Bearer $TOKEN"   # 204

# la feature 5 ora mostra le foto
curl -s "http://localhost:8080/api/items/$ITEM" -H "Authorization: Bearer $TOKEN"
```

Casi d'errore: upload di un `.txt` → `400 invalid_file`; file > 5 MB → `400 file_too_large`;
`itemId` inesistente o di un altro utente → `404 item_not_found`; `imageId` di un altro
item → `404 image_not_found`; riordino con un id mancante/estraneo/duplicato →
`400 invalid_image_order`; 11ª immagine → `409 too_many_images`; nessun `Authorization` →
`401`. Dopo il `DELETE`: il file sparisce dalla cartella `uploads/` e gli ordini si
ricompattano.
