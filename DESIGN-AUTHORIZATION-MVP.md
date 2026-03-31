# Design Doc — Camada de Usuário (Admin vs Convidado) — MVP

**Stack:** Kotlin + Arrow-kt + Ktor  
**Arquitetura:** Hexagonal (domain sem imports de infra)  
**Erros no domínio:** `Either`, nunca `throw`  
**Teste:** Kotest BDD (Given/When/Then)

## 1) Contexto e objetivo

Queremos uma camada de acesso simples:

- **Admin**: acesso a tudo.
- **Convidado** (anônimo): acesso **somente** a endpoints de leitura (HTTP `GET`) e **não pode** `POST/PUT/PATCH/DELETE`.

Para o MVP, “admin” será interpretado como **qualquer request autenticada** (JWT válido). Não haverá RBAC completo ainda.

## 2) Estado atual (como o projeto funciona hoje)

O projeto **não** usa o plugin `Authentication` do Ktor. Em vez disso, existe um middleware próprio:

- `jwtAuth(jwtService) { ctx -> ... }`  
  - Lê o header `Authorization: Token <jwt>` (via `parseAuthorizationHeader`)
  - Valida com `JwtService.verifyJwtToken(...)`
  - Se não houver token → responde `401 Unauthorized`
  - Se token inválido → responde o `DomainError` mapeado (hoje cai em `422` via `JwtInvalid`)

- `optionalJwtAuth(jwtService) { ctxOrNull -> ... }`  
  - Injeta `JwtContext?` (nulo se não houver token)
  - Útil para endpoints públicos que, opcionalmente, personalizam a resposta quando há usuário

> Isso é bom para o MVP: conseguimos implementar a “camada de usuário” sem introduzir infra no domínio.

## 3) Definições do MVP

### 3.1 Atores (quem está chamando)

- **Convidado (Guest)**: request **sem JWT**.
- **Admin**: request **com JWT válido**.

> Futuro: roles reais (claim no JWT, tabela de users com role, etc.).

### 3.2 Capacidades (o que pode fazer)

Vamos modelar permissões na borda (adapter web), por **tipo de endpoint**:

- **PublicRead**: leitura pública (GET liberado para convidado). Pode usar `optionalJwtAuth`.
- **AuthRead**: leitura autenticada (GET exige JWT). Usa `jwtAuth`.
- **Write**: escrita (POST/PUT/PATCH/DELETE exige JWT). Usa `jwtAuth`.

Isso evita a regra simplista “GET sempre liberado”, que quebraria endpoints como `GET /api/user` (lê dados privados) ou `GET /api/article/feed` (feed do usuário).

## 4) Proposta de design

### 4.1 Uma DSL/Guard de autorização por rota (adapter web)

Criar uma camada pequena de helpers para padronizar:

- quais endpoints são públicos vs autenticados
- como extraímos (opcionalmente) o `userId`
- quais status retornamos (401/403)

Exemplo de API desejada (nome sugerido):

```kotlin
// PublicRead: permite convidado; se houver JWT válido, expõe userId
suspend inline fun <reified R : Any> Route.publicGet(
  jwtService: JwtService,
  crossinline body: suspend RoutingContext.(JwtContext?) -> Unit
)

// AuthRead/Write: exige JWT válido; fornece JwtContext
suspend inline fun <reified R : Any> Route.authGet(
  jwtService: JwtService,
  crossinline body: suspend RoutingContext.(JwtContext) -> Unit
)

suspend inline fun <reified R : Any> Route.authPost(/* ... */)
suspend inline fun <reified R : Any> Route.authPut(/* ... */)
suspend inline fun <reified R : Any> Route.authDelete(/* ... */)
```

Implementação interna (MVP) delega para `jwtAuth/optionalJwtAuth` existentes.

**Por que isso é importante?**  
Porque a regra “guest não pode escrever” passa a ser *estrutural*: fica difícil criar um `post { ... }` sem autenticação “sem querer”.

### 4.2 (Opcional no MVP) Defesa no application/service

Para o escopo “guest não pode escrever”, o bloqueio na borda já resolve.  
Se quisermos defesa em profundidade, podemos adicionar ao **application layer** uma checagem simples “só executa comandos de escrita quando há `userId` autenticado”.

**Diretriz:** se entrar no domínio/application, retornar `Either` (ex.: `Either<DomainError, A>`), nunca `throw`.

## 5) Matriz de acesso (endpoints atuais)

Com base nas rotas atuais em `src/main/kotlin/.../routes/*`:

### 5.1 PublicRead (Guest pode)

- `GET /api/tags` (`tagRoutes`)
- `GET /api/profiles/{username}` (`profileRoutes`)
- `GET /api/articles/{slug}` (`articleRoutes`) — hoje já usa `optionalJwtAuth`

### 5.2 AuthRead (Guest NÃO pode; exige JWT)

- `GET /api/user` (usuário atual)
- `GET /api/article/feed` (feed do usuário)
- `GET /api/articles/{slug}/comments` (**decisão do MVP**)
  - Hoje este endpoint usa `jwtAuth` mesmo sendo GET.
  - **Opção A (manter AuthRead – recomendado para MVP):** comentários exigem login (menos exposição/abuso).
  - **Opção B (mudar para PublicRead):** trocar para `optionalJwtAuth` e permitir convidado ler comentários.

> Esta doc assume **Opção A** como default. Se o requisito for “todo GET é público”, trocamos esse endpoint para PublicRead (ver checklist).

### 5.3 Write (Guest NÃO pode; exige JWT)

- `POST /api/users` (registro) — **exceção**: é write, mas deve continuar público
- `POST /api/users/login` — **exceção**: é write, mas deve continuar público
- `PUT /api/user`
- `POST /api/articles`
- `PUT /api/articles/{slug}`
- `DELETE /api/articles/{slug}`
- `POST /api/articles/{slug}/favorite`
- `DELETE /api/articles/{slug}/favorite`
- `POST /api/articles/{slug}/comments`
- `DELETE /api/articles/{slug}/comments/{id}`
- `POST /api/profiles/{username}/follow`
- `DELETE /api/profiles/{username}/follow`

**Observação importante:** registro/login são “writes públicos” (sem JWT). No guard/DSL, trate como rotas explicitamente públicas.

## 6) Erros e respostas HTTP

### 6.1 Regras (MVP)

- Endpoint **AuthRead/Write** sem token → `401 Unauthorized`
- Endpoint **AuthRead/Write** com token inválido → manter comportamento atual (hoje mapeia para `422` via `JwtInvalid`)
- Endpoint **PublicRead** sem token → `200` (e `JwtContext? = null`)

### 6.2 Futuro (quando existir role real)

Quando houver roles no JWT:

- Token válido, mas sem permissão → `403 Forbidden`
- No domínio/application: modelar erro como `Either.Left(DomainError.Forbidden(...))`

## 7) Mudanças propostas no código (MVP)

### 7.1 Novos helpers no adapter web

Criar um arquivo, por exemplo:

- `src/main/kotlin/io/github/nomisrev/auth/access.kt` (ou `routes/access.kt`)

Com helpers como `publicGet/authGet/authPost/...` que encapsulam:

- `jwtAuth` / `optionalJwtAuth`
- padronização de status
- (opcional) logging/auditoria futura

### 7.2 Refactor incremental das rotas

Trocar usos diretos de `get/post/put/delete` + `jwtAuth(...)` por:

- `publicGet { ctx? -> ... }`
- `authGet { ctx -> ... }`
- `authPost/authPut/authDelete { ctx -> ... }`

Objetivo: reduzir repetição e tornar a regra “guest read-only” consistente.

## 8) Checklist de implementação (code mode)

- [ ] Criar helpers `publicGet/authGet/authPost/authPut/authDelete` (delegando para `jwtAuth/optionalJwtAuth`).
- [ ] Migrar rotas existentes para usar os helpers (refactor mecânico).
- [ ] Garantir que **todas** as rotas de escrita (exceto `/users` e `/users/login`) usam `auth*`.
- [ ] Decidir para `GET /api/articles/{slug}/comments`: manter AuthRead (default) ou tornar PublicRead (trocar para `optionalJwtAuth`).
- [ ] (Opcional) Padronizar “token inválido” para `401` (se quisermos alinhar com REST), sem mexer no domínio.

## 9) Plano de testes (Kotest BDD)

Priorizar testes de rota (adapter web), pois a regra é principalmente de borda no MVP.

### 9.1 Novos/ajustar RouteSpecs

Para cada endpoint de **Write**:

- **Given** um request **sem Authorization**
- **When** chamar `POST/PUT/DELETE`
- **Then** retornar `401`

Para endpoints **PublicRead**:

- **Given** request sem Authorization
- **When** chamar `GET`
- **Then** retornar `200`

Para endpoints **AuthRead**:

- **Given** request sem Authorization
- **When** chamar `GET`
- **Then** retornar `401`

Arquivos existentes úteis:

- `src/test/kotlin/io/github/nomisrev/routes/*RouteSpec.kt`

## 10) Rollout e compatibilidade

- Refactor pode ser feito **incrementalmente**, rota por rota.
- Sem mudança de contrato público (URLs/JSON), apenas consistência de autorização.

---

## Decisões pendentes (se quiser fechar antes de implementar)

1) `GET /api/articles/{slug}/comments` deve ser público para guest?  
2) Token inválido deve responder `401` (em vez de `422` atual) para consistência?

