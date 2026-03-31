package io.github.nomisrev.bookmarks

import arrow.core.Either
import io.github.nomisrev.DomainError
import io.github.nomisrev.repo.ArticleId
import io.github.nomisrev.repo.UserId
import io.github.nomisrev.routes.Article

interface ArticleRefResolver {
  suspend fun slugToArticleId(slug: String): Either<DomainError, ArticleId>

  suspend fun articleById(
    articleId: ArticleId,
    requestingUserId: UserId,
  ): Either<DomainError, Article>
}
