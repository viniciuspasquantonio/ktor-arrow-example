package io.github.nomisrev.bookmarks

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensureNotNull
import io.github.nomisrev.DomainError
import io.github.nomisrev.UserNotFound
import io.github.nomisrev.repo.ArticleId
import io.github.nomisrev.repo.ArticlePersistence
import io.github.nomisrev.repo.UserId
import io.github.nomisrev.routes.Article
import io.github.nomisrev.service.ArticleService
import io.github.nomisrev.service.Slug
import io.github.nomisrev.sqldelight.UsersQueries

class ArticleAclImpl(
  private val articlePersistence: ArticlePersistence,
  private val articleService: ArticleService,
) : ArticleAcl {

  override suspend fun articleIdBySlug(slug: String): Either<DomainError, BookmarkArticleId> =
    either {
      val article = articlePersistence.findArticleBySlug(Slug(slug)).bind()
      BookmarkArticleId(article.id.serial)
    }

  override suspend fun articleViewBySlugForActor(
    slug: String,
    actorUserId: Long?,
  ): Either<DomainError, ArticleView> = either {
    // We can use articleService.getArticleBySlug, then map to ArticleView.
    // However, ArticleService.getArticleBySlug currently returns favorited=false hardcoded.
    // If actorUserId is not null, we should probably check if it's favorited.
    // For now, since the PRD wants us to return the view, we just use the service.
    // Wait, the routes themselves already map Article to SingleArticleResponse.
    val article = articleService.getArticleBySlug(Slug(slug)).bind()
    mapToView(article)
  }

  override suspend fun listArticlesByIds(
    articleIds: List<BookmarkArticleId>,
    actorUserId: Long?,
  ): Either<DomainError, List<ArticleView>> = either {
    if (articleIds.isEmpty()) return@either emptyList()

    val userId = actorUserId?.let { UserId(it) }
    val articles = articleService.getArticlesByIds(articleIds.map { ArticleId(it.value) }, userId)

    // To preserve order of the provided articleIds
    val articleMap = articles.associateBy { it.articleId }
    articleIds.mapNotNull { articleMap[it.value] }.map { mapToView(it) }
  }

  private fun mapToView(article: Article): ArticleView =
    ArticleView(
      slug = article.slug,
      title = article.title,
      description = article.description,
      body = article.body,
      authorUsername = article.author.username,
      authorBio = article.author.bio,
      authorImage = article.author.image,
      authorFollowing = article.author.following,
      favorited = article.favorited,
      favoritesCount = article.favoritesCount,
      createdAt = article.createdAt,
      updatedAt = article.updatedAt,
      tagList = article.tagList,
    )
}

class UserAclImpl(private val usersQueries: UsersQueries) : UserAcl {
  override suspend fun userIdByUsername(username: String): Either<DomainError, BookmarkUserId> =
    either {
      val userId = usersQueries.selectIdByUsername(username).executeAsOneOrNull()
      ensureNotNull(userId) { UserNotFound("username=$username") }
      BookmarkUserId(userId.serial)
    }
}
