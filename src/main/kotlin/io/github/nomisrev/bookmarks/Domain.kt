package io.github.nomisrev.bookmarks

import arrow.core.Either
import io.github.nomisrev.DomainError
import java.time.OffsetDateTime

// Domain Models

@JvmInline value class BookmarkUserId(val value: Long)
@JvmInline value class BookmarkArticleId(val value: Long)

// Shared View for ACL / Responses
data class ArticleView(
  val slug: String,
  val title: String,
  val description: String,
  val body: String,
  val authorUsername: String,
  val authorBio: String?,
  val authorImage: String,
  val authorFollowing: Boolean,
  val favorited: Boolean,
  val favoritesCount: Long,
  val createdAt: OffsetDateTime,
  val updatedAt: OffsetDateTime,
  val tagList: List<String>,
)

data class ArticlesPageView(
  val articles: List<ArticleView>,
  val articlesCount: Int,
)

// Inbound Ports (Use Cases)

interface BookmarkArticle {
  suspend fun invoke(actorUserId: Long, slug: String): Either<DomainError, ArticleView>
}

interface UnbookmarkArticle {
  suspend fun invoke(actorUserId: Long, slug: String): Either<DomainError, ArticleView>
}

interface ListMyBookmarkedArticles {
  suspend fun invoke(actorUserId: Long, limit: Int, offset: Int): Either<DomainError, ArticlesPageView>
}

interface ListArticlesBookmarkedBy {
  suspend fun invoke(
    username: String,
    actorUserId: Long?,
    limit: Int,
    offset: Int
  ): Either<DomainError, ArticlesPageView>
}

// Outbound Ports

interface BookmarkRepository {
  suspend fun exists(userId: BookmarkUserId, articleId: BookmarkArticleId): Boolean
  suspend fun add(userId: BookmarkUserId, articleId: BookmarkArticleId): Unit
  suspend fun remove(userId: BookmarkUserId, articleId: BookmarkArticleId): Unit
  suspend fun countByArticle(articleId: BookmarkArticleId): Long
  suspend fun listArticleIdsByUser(userId: BookmarkUserId, limit: Int, offset: Int): List<BookmarkArticleId>
  suspend fun countByUser(userId: BookmarkUserId): Long
}

interface ArticleAcl {
  suspend fun articleIdBySlug(slug: String): Either<DomainError, BookmarkArticleId>
  suspend fun articleViewBySlugForActor(slug: String, actorUserId: Long?): Either<DomainError, ArticleView>
  suspend fun listArticlesByIds(
    articleIds: List<BookmarkArticleId>,
    actorUserId: Long?,
  ): Either<DomainError, List<ArticleView>>
}

interface UserAcl {
  suspend fun userIdByUsername(username: String): Either<DomainError, BookmarkUserId>
}
