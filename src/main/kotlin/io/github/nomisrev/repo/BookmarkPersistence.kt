package io.github.nomisrev.repo

import io.github.nomisrev.sqldelight.BookmarksQueries
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

interface BookmarkPersistence {
  suspend fun add(
    userId: UserId,
    articleId: ArticleId,
    createdAt: OffsetDateTime = OffsetDateTime.now(ZoneOffset.UTC),
  )

  suspend fun remove(userId: UserId, articleId: ArticleId)

  suspend fun list(userId: UserId, limit: Long, offset: Long): List<ArticleId>

  suspend fun count(userId: UserId): Long
}

fun bookmarkPersistence(bookmarksQueries: BookmarksQueries): BookmarkPersistence =
  object : BookmarkPersistence {
    override suspend fun add(userId: UserId, articleId: ArticleId, createdAt: OffsetDateTime) {
      bookmarksQueries.insertIfNotExists(
        user_id = userId.serial,
        article_id = articleId.serial,
        created_at = createdAt.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
      )
    }

    override suspend fun remove(userId: UserId, articleId: ArticleId) {
      bookmarksQueries.delete(user_id = userId.serial, article_id = articleId.serial)
    }

    override suspend fun list(userId: UserId, limit: Long, offset: Long): List<ArticleId> =
      bookmarksQueries
        .listBookmarkedArticleIds(user_id = userId.serial, limit = limit, offset = offset)
        .executeAsList()
        .map { ArticleId(it) }

    override suspend fun count(userId: UserId): Long =
      bookmarksQueries.countBookmarks(user_id = userId.serial).executeAsOne()
  }
