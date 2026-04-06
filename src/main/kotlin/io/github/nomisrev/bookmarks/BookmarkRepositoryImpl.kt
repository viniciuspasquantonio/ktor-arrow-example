package io.github.nomisrev.bookmarks

import io.github.nomisrev.sqldelight.FavoritesQueries
import java.time.OffsetDateTime

class BookmarkRepositoryImpl(
  private val favoritesQueries: FavoritesQueries
) : BookmarkRepository {

  override suspend fun exists(userId: BookmarkUserId, articleId: BookmarkArticleId): Boolean =
    favoritesQueries.isFavorite(userId.value, articleId.value).executeAsOneOrNull() != null

  override suspend fun add(userId: BookmarkUserId, articleId: BookmarkArticleId) {
    favoritesQueries.insert(
      articleId = articleId.value,
      userId = userId.value,
      createdAt = OffsetDateTime.now()
    )
  }

  override suspend fun remove(userId: BookmarkUserId, articleId: BookmarkArticleId) {
    favoritesQueries.delete(
      articleId = articleId.value,
      userId = userId.value
    )
  }

  override suspend fun countByArticle(articleId: BookmarkArticleId): Long =
    favoritesQueries.favoriteCount(articleId.value).executeAsOne()

  override suspend fun listArticleIdsByUser(
    userId: BookmarkUserId,
    limit: Int,
    offset: Int
  ): List<BookmarkArticleId> =
    favoritesQueries
      .selectArticleIdsByUserId(userId.value, limit.toLong(), offset.toLong())
      .executeAsList()
      .map { BookmarkArticleId(it) }

  override suspend fun countByUser(userId: BookmarkUserId): Long =
    favoritesQueries.countByUser(userId.value).executeAsOne()
}
