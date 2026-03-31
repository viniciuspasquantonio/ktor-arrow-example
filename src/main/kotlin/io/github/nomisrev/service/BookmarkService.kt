package io.github.nomisrev.service

import arrow.core.Either
import arrow.core.raise.either
import io.github.nomisrev.DomainError
import io.github.nomisrev.bookmarks.ArticleRefResolver
import io.github.nomisrev.repo.BookmarkPersistence
import io.github.nomisrev.repo.UserId
import io.github.nomisrev.routes.Article
import io.github.nomisrev.routes.MultipleArticlesResponse

interface BookmarkService {
  suspend fun addBookmark(userId: UserId, slug: String): Either<DomainError, Article>

  suspend fun removeBookmark(userId: UserId, slug: String): Either<DomainError, Article>

  suspend fun listBookmarks(
    userId: UserId,
    limit: Long,
    offset: Long,
  ): Either<DomainError, MultipleArticlesResponse>
}

fun bookmarkService(
  bookmarkPersistence: BookmarkPersistence,
  articleRefResolver: ArticleRefResolver,
): BookmarkService =
  object : BookmarkService {
    override suspend fun addBookmark(userId: UserId, slug: String): Either<DomainError, Article> =
      either {
        val articleId = articleRefResolver.slugToArticleId(slug).bind()
        bookmarkPersistence.add(userId, articleId)
        articleRefResolver.articleById(articleId, userId).bind()
      }

    override suspend fun removeBookmark(
      userId: UserId,
      slug: String,
    ): Either<DomainError, Article> = either {
      val articleId = articleRefResolver.slugToArticleId(slug).bind()
      bookmarkPersistence.remove(userId, articleId)
      articleRefResolver.articleById(articleId, userId).bind()
    }

    override suspend fun listBookmarks(
      userId: UserId,
      limit: Long,
      offset: Long,
    ): Either<DomainError, MultipleArticlesResponse> = either {
      val articleIds = bookmarkPersistence.list(userId, limit, offset)
      val articles =
        articleIds.map { articleId -> articleRefResolver.articleById(articleId, userId).bind() }
      val count = bookmarkPersistence.count(userId).toInt()
      MultipleArticlesResponse(articles, count)
    }
  }
