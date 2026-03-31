# Bookmarks (Reading List) — PRD + Design Doc

Following Nori workflow...

## Contexto
Este projeto implementa a RealWorld API (Conduit). Hoje, usuários podem ler/escrever/comentar artigos. Falta um “ler depois”: uma lista privada de artigos salvos.

## Objetivo (Goal)
Adicionar um sistema de **bookmarks de artigos** para que um usuário autenticado possa:
1) **Adicionar** bookmark em um artigo  
2) **Remover** bookmark  
3) **Listar** seus bookmarks (reading list)

## Escopo (In scope)
- Bookmarks **somente** de **artigos**
- Novo **bounded context**: `bookmarks`
- **Zero shared domain models** entre contexts (comunicação via IDs/strings)
- **Arrow Either em tudo** (sem `throw` no domínio)
- API REST sob `/api/bookmarks`
- Paginação **offset/limit** e ordenação por **data de criação do bookmark (DESC)**

## Fora de escopo (Out of scope)
- Bookmark de outros tipos (comentários, perfis, etc.)
- Pastas/labels para bookmarks
- Notificações, lembretes, recomendação
- Sincronização offline
- Endpoint público: bookmarks são **privados** do usuário autenticado

## Critérios de aceitação (Acceptance Criteria)
1) **POST /api/bookmarks** com um `slug` válido cria (ou mantém) o bookmark do usuário e retorna o **Article** (modelo de API) com `201 Created`.
2) **DELETE /api/bookmarks/{slug}** remove o bookmark (se existir) e retorna o **Article** com `200 OK`.
3) **GET /api/bookmarks?offset&limit** retorna `MultipleArticlesResponse` contendo apenas artigos bookmarkados pelo usuário, ordenados por bookmark mais recente primeiro.
4) Operações são **idempotentes**:
   - POST para um artigo já bookmarkado não cria duplicata.
   - DELETE para um artigo não-bookmarkado não quebra (comportamento definido no design abaixo).
5) Se `slug` não existir: retorna erro de domínio mapeado para `422 Unprocessable Entity` (padrão do projeto).
6) Domínio não lança exceções; todas as falhas do domínio retornam `Either<DomainError, *>`.

---

# Design Doc

## Visão geral de arquitetura (Hexagonal)
Criar BC `bookmarks` com:
- **Domain**: regras e casos de uso (sem imports de infra)
- **Ports (interfaces)**: persistência de bookmarks e resolução de artigo por `slug`/`id`
- **Adapters (infra)**:
  - SQLDelight para persistência de bookmarks
  - Adapter para resolver `slug -> articleId` usando o BC de artigos (via `ArticlePersistence`)
  - Rotas Ktor expondo `/api/bookmarks`

### Separação entre contexts (zero shared domain models)
- **Bookmarks BC** guarda **referência**: `(userId, articleId)` + `createdAt`
- A API recebe `slug`, mas o Bookmarks BC não “entende” artigo: ele usa um **port** para resolver `slug -> articleId`.
- Para listar, o Bookmarks BC obtém os `articleId`s e usa um **port** para materializar `Article` (modelo de API), sem compartilhar “domínio de artigos”.

> Observação: no projeto atual, `routes.Article` é um modelo de API (DTO), não um domínio estrito. Ainda assim, para respeitar “zero shared domain models”, o Bookmarks BC não deve importar modelos de domínio de artigos; ele pode retornar DTOs **apenas na camada de rota** (mapping no boundary).

## API

### Resources
Base: `/api`

#### POST `/api/bookmarks`
Request:
```json
{ "bookmark": { "slug": "some-article-slug" } }
```
Response:
- `201 Created` + `SingleArticleResponse` (ou `ArticleResponse`) conforme padrão do projeto

Idempotência:
- Se já existir bookmark, retornar o artigo mesmo assim (201 ou 200). **Decisão**: manter `201 Created` para simplicidade do cliente, mas opcionalmente retornar `200 OK` quando já existia (não obrigatório).

Erros:
- `422` se `slug` não existir (ex.: `ArticleBySlugNotFound`)

#### DELETE `/api/bookmarks/{slug}`
Response:
- `200 OK` + `SingleArticleResponse` (artigo “des-bookmarkado”)

Idempotência:
- Se não existir bookmark, **ainda** retornar `200` com o artigo (se slug existe), ou retornar `422` “bookmark not found”.  
**Recomendação** (menos fricção): retornar `200` com o artigo quando slug existe, mesmo se bookmark não existia.

Erros:
- `422` se `slug` não existir

#### GET `/api/bookmarks?offset={int}&limit={int}`
Response:
```json
{ "articles": [ ... ], "articlesCount": 123 }
```
Ordenação:
- `createdAt` do bookmark **DESC**

## Modelagem de domínio (Bookmarks BC)

### Value objects / tipos
- `BookmarkId` (opcional, mas podemos usar PK composta)
- `BookmarkedArticleRef(articleId: Long)` (interno do BC)

### Entidades (mínimo viável)
Não é necessário criar uma entidade rica para MVP. Persistência pode ser PK composta:
- `bookmark(user_id, article_id)` + `created_at`

## Persistência (SQLDelight)

### Tabela
Arquivo sugerido: `src/main/sqldelight/io/github/nomisrev/sqldelight/Bookmarks.sq`

Sugestão (PostgreSQL):
```sql
CREATE TABLE IF NOT EXISTS bookmarks(
  user_id BIGINT REFERENCES users(id) ON DELETE CASCADE NOT NULL,
  article_id BIGINT REFERENCES articles(id) ON DELETE CASCADE NOT NULL,
  created_at VARCHAR(50) NOT NULL,
  PRIMARY KEY (user_id, article_id)
);
```

Queries necessárias:
- `insertIfNotExists(articleId, userId, createdAt)`  
  - `INSERT ... ON CONFLICT DO NOTHING`
- `delete(articleId, userId)`
- `listBookmarkedArticleIds(userId, limit, offset)`  
  - `ORDER BY created_at DESC LIMIT :limit OFFSET :offset`
- `countBookmarks(userId)` (para `articlesCount`)

## Ports (interfaces) e casos de uso

### Port: BookmarkPersistence
Arquivo sugerido: `src/main/kotlin/io/github/nomisrev/repo/BookmarkPersistence.kt`

Responsabilidades (assinaturas sugeridas):
- `add(userId: UserId, articleId: ArticleId, createdAt: OffsetDateTime): Unit`
- `remove(userId: UserId, articleId: ArticleId): Unit`
- `list(userId: UserId, limit: Int, offset: Int): List<ArticleId>`
- `count(userId: UserId): Long`
- `exists(userId: UserId, articleId: ArticleId): Boolean` (opcional)

### Port: ArticleRefResolver (anti-corruption)
Arquivo sugerido: `src/main/kotlin/io/github/nomisrev/bookmarks/ArticleRefResolver.kt`

Responsabilidades:
- `slugToArticleId(slug: String): Either<DomainError, ArticleId>`
- `articleById(articleId: ArticleId, requestingUserId: UserId): Either<DomainError, io.github.nomisrev.routes.Article>`

Observação: se você quiser manter o Bookmarks BC ainda mais “puro”, faça o service de bookmarks retornar apenas `List<ArticleId>` e a rota que materializa os artigos. Mas como o AC pede POST/DELETE retornando Article, o caminho mais simples é o resolver materializar o artigo já com os flags corretos.

### Service: BookmarkService
Arquivo sugerido: `src/main/kotlin/io/github/nomisrev/service/BookmarkService.kt`

Use cases:
- `addBookmark(userId: UserId, slug: String): Either<DomainError, routes.Article>`
- `removeBookmark(userId: UserId, slug: String): Either<DomainError, routes.Article>`
- `listBookmarks(userId: UserId, limit: Int, offset: Int): Either<DomainError, MultipleArticlesResponse>`

Fluxos:
- `add`: resolve `slug -> articleId`; `add`; materializa `Article` e retorna
- `remove`: resolve `slug -> articleId`; `remove`; materializa `Article` e retorna
- `list`: busca `articleId`s + `count`; materializa artigos; monta `MultipleArticlesResponse`

## Erros e mapeamento HTTP
Reusar `DomainError` existente quando possível:
- `ArticleBySlugNotFound(slug)` para slug inválido

Se for necessário um erro específico:
- `BookmarkError` (ex.: `BookmarkNotFound`) — **provavelmente desnecessário** se DELETE for idempotente e não falhar quando não existe bookmark.

Rotas devem responder via helper já existente:
- `Either<DomainError, A>.respond(HttpStatusCode.XXX)`

## Rotas Ktor
Arquivo sugerido: `src/main/kotlin/io/github/nomisrev/routes/bookmarks.kt`

Autenticação:
- `jwtAuth(jwtService)` obrigatório em todos endpoints

DTOs (request):
```kotlin
@Serializable data class BookmarkWrapper<T : Any>(val bookmark: T)
@Serializable data class CreateBookmark(val slug: String)
```

Sem shared domain models:
- Rotas podem usar os DTOs já existentes de artigo (`SingleArticleResponse` / `MultipleArticlesResponse`) porque isso é **modelo de API**, não domínio interno do Bookmarks BC.

## Integração de dependências
Arquivos:
- `src/main/kotlin/io/github/nomisrev/env/Dependencies.kt` (instanciar persistence/service)
- `src/main/kotlin/io/github/nomisrev/routes/root.kt` (registrar `bookmarkRoutes`)

## Testes (Kotest BDD)
Estratégia:
- Teste de rota (blackbox) criando artigo e exercitando `/api/bookmarks`

Casos mínimos:
- adicionar → listar inclui artigo → remover → listar vazio
- slug inválido → `422`
- paginação `offset/limit` (ex.: 2 bookmarks, `limit=1` retorna 1)

---

# Tickets (para um code agent)

## Ticket 1 — SQLDelight: tabela e queries de bookmarks
**Objetivo:** adicionar armazenamento de bookmarks.
- Arquivo: `src/main/sqldelight/io/github/nomisrev/sqldelight/Bookmarks.sq`
- Queries: `insertIfNotExists`, `delete`, `listBookmarkedArticleIds`, `countBookmarks`
- Critério: build gera queries e compila

## Ticket 2 — Persistence adapter: BookmarkPersistence
**Objetivo:** implementar interface de persistência.
- Arquivo(s):
  - `src/main/kotlin/io/github/nomisrev/repo/BookmarkPersistence.kt`
  - `src/main/kotlin/io/github/nomisrev/repo/bookmarkPersistence.kt` (factory/impl)
- Critério: execução correta das queries; idempotência em `insertIfNotExists`

## Ticket 3 — Anti-corruption: ArticleRefResolver
**Objetivo:** resolver `slug -> articleId` e materializar artigo sem acoplar domínios.
- Arquivo: `src/main/kotlin/io/github/nomisrev/bookmarks/ArticleRefResolver.kt`
- Critério: retorna `Either` e propaga `ArticleBySlugNotFound` para slug inválido

## Ticket 4 — Domain/service: BookmarkService
**Objetivo:** casos de uso add/remove/list.
- Arquivo: `src/main/kotlin/io/github/nomisrev/service/BookmarkService.kt`
- Critério: `Either` end-to-end, sem throw no domínio

## Ticket 5 — Rotas: `/api/bookmarks`
**Objetivo:** expor endpoints.
- Arquivo: `src/main/kotlin/io/github/nomisrev/routes/bookmarks.kt`
- Endpoints:
  - `POST /api/bookmarks` (201 + Article)
  - `DELETE /api/bookmarks/{slug}` (200 + Article)
  - `GET /api/bookmarks?offset&limit` (200 + MultipleArticlesResponse)

## Ticket 6 — Wiring: Dependencies + root routes
**Objetivo:** conectar tudo na aplicação.
- Arquivos:
  - `src/main/kotlin/io/github/nomisrev/env/Dependencies.kt`
  - `src/main/kotlin/io/github/nomisrev/routes/root.kt`

## Ticket 7 — Testes: BookmarksRouteSpec
**Objetivo:** validar comportamento externamente.
- Arquivo: `src/test/kotlin/io/github/nomisrev/routes/BookmarksRouteSpec.kt`
- Critério: testes verdes e estáveis (sem mocks e sem detalhes internos)

---

## Decisões registradas
- Bookmarks são de **artigos** apenas
- API via recurso `/api/bookmarks`
- Armazenamento: `(userId, articleId)` (sem slug na persistência)
- POST/DELETE retornam **Article**
- Listagem paginada por **offset/limit** e ordenação por bookmark mais recente
