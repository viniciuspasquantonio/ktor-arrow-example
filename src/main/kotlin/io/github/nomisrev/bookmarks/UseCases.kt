package io.github.nomisrev.bookmarks

import arrow.core.Either
import arrow.core.raise.either
import io.github.nomisrev.DomainError

class BookmarkArticleUseCase(
  private val bookmarkRepository: BookmarkRepository,
  private val articleAcl: ArticleAcl,
) : BookmarkArticle {
  override suspend fun invoke(actorUserId: Long, slug: String): Either<DomainError, ArticleView> =
    either {
      val articleId = articleAcl.articleIdBySlug(slug).bind()
      val bookmarkUserId = BookmarkUserId(actorUserId)
      
      // Idempotency: if not exists, add
      if (!bookmarkRepository.exists(bookmarkUserId, articleId)) {
        bookmarkRepository.add(bookmarkUserId, articleId)
      }
      
      articleAcl.articleViewBySlugForActor(slug, actorUserId).bind()
    }
}

class UnbookmarkArticleUseCase(
  private val bookmarkRepository: BookmarkRepository,
  private val articleAcl: ArticleAcl,
) : UnbookmarkArticle {
  override suspend fun invoke(actorUserId: Long, slug: String): Either<DomainError, ArticleView> =
    either {
      val articleId = articleAcl.articleIdBySlug(slug).bind()
      val bookmarkUserId = BookmarkUserId(actorUserId)
      
      // Idempotency: if exists, remove
      if (bookmarkRepository.exists(bookmarkUserId, articleId)) {
        bookmarkRepository.remove(bookmarkUserId, articleId)
      }
      
      articleAcl.articleViewBySlugForActor(slug, actorUserId).bind()
    }
}

class ListMyBookmarkedArticlesUseCase(
  private val bookmarkRepository: BookmarkRepository,
  private val articleAcl: ArticleAcl,
) : ListMyBookmarkedArticles {
  override suspend fun invoke(
    actorUserId: Long,
    limit: Int,
    offset: Int,
  ): Either<DomainError, ArticlesPageView> = either {
    val bookmarkUserId = BookmarkUserId(actorUserId)
    
    val articleIds = bookmarkRepository.listArticleIdsByUser(bookmarkUserId, limit, offset)
    val totalCount = bookmarkRepository.countByUser(bookmarkUserId).toInt()
    
    val articles = if (articleIds.isEmpty()) {
      emptyList()
    } else {
      articleAcl.listArticlesByIds(articleIds, actorUserId).bind()
    }
    
    ArticlesPageView(
      articles = articles,
      articlesCount = totalCount
    )
  }
}

class ListArticlesBookmarkedByUseCase(
  private val bookmarkRepository: BookmarkRepository,
  private val articleAcl: ArticleAcl,
  private val userAcl: UserAcl,
) : ListArticlesBookmarkedBy {
  override suspend fun invoke(
    username: String,
    actorUserId: Long?,
    limit: Int,
    offset: Int
  ): Either<DomainError, ArticlesPageView> = either {
    val bookmarkUserId = userAcl.userIdByUsername(username).bind()
    
    val articleIds = bookmarkRepository.listArticleIdsByUser(bookmarkUserId, limit, offset)
    val totalCount = bookmarkRepository.countByUser(bookmarkUserId).toInt()
    
    val articles = if (articleIds.isEmpty()) {
      emptyList()
    } else {
      articleAcl.listArticlesByIds(articleIds, actorUserId).bind()
    }
    
    ArticlesPageView(
      articles = articles,
      articlesCount = totalCount
    )
  }
}
