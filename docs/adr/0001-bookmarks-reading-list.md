# ADR 0001: Bookmarks (Reading List)

## Contexto
O projeto RealWorld API (Conduit) suporta "favoritar" um artigo (`POST /api/articles/{slug}/favorite`).
No entanto, foi solicitado o desenvolvimento de um sistema de "Bookmarks" para criar uma "Reading List" (lista de leitura) do próprio usuário logado.

## Decisões
1. **Reuso da semântica de Favoritos**: Optamos por reinterpretar o endpoint de `favorite` como a ação de `bookmark` (salvar para ler depois). Não criaremos um endpoint `/bookmark` separado para evitar duplicidade de regras e manter retrocompatibilidade estrita com a spec RealWorld.
2. **Idempotência**: O sistema de bookmarks será estritamente idempotente. Adicionar um bookmark já existente retornará sucesso (200 OK) refletindo o estado final, assim como remover um bookmark inexistente.
3. **Ordenação da Lista de Leitura**: A lista de leitura (`GET /api/articles/bookmarked`) e filtros associados serão ordenados pelos bookmarks **mais recentes primeiro**. Isso exige armazenar um timestamp (`created_at`) na tabela de relacionamento.
4. **Isolamento de Domínio**: Criaremos um Bounded Context `bookmarks` isolado, comunicando-se com `articles` e `users` apenas via IDs (Long/String) através de uma Anti-Corruption Layer (ACL).
