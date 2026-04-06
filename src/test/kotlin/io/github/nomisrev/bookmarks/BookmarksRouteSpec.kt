package io.github.nomisrev.bookmarks

import io.github.nomisrev.KotestProject
import io.github.nomisrev.auth.JwtToken
import io.github.nomisrev.routes.ArticleResponse
import io.github.nomisrev.routes.ArticleWrapper
import io.github.nomisrev.routes.ArticlesResource
import io.github.nomisrev.routes.MultipleArticlesResponse
import io.github.nomisrev.routes.NewArticle
import io.github.nomisrev.routes.SingleArticleResponse
import io.github.nomisrev.service.RegisterUser
import io.github.nomisrev.withServer
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.StringSpec
import io.ktor.client.call.body
import io.ktor.client.plugins.resources.delete
import io.ktor.client.plugins.resources.get
import io.ktor.client.plugins.resources.post
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import java.util.UUID
import kotlin.properties.Delegates

class BookmarksRouteSpec :
  StringSpec({
    var token: JwtToken by Delegates.notNull()
    var slug: String by Delegates.notNull()
    var currentUsername: String by Delegates.notNull()

    beforeTest {
      val uniqueId = UUID.randomUUID().toString().substring(0, 8)
      currentUsername = "user_$uniqueId"
      val email = "user_$uniqueId@domain.com"
      val password = "123456789"
      val title = "Bookmark Article $uniqueId"
      val description = "This is a fake article description."
      val body = "Lorem ipsum dolor sit amet."
      val tags = listOf("arrow", "ktor")

      val userService = KotestProject.dependencies.get().userService
      token = userService.register(RegisterUser(currentUsername, email, password)).shouldBeRight()

      // Create an article
      withServer {
        val createResponse =
          post(ArticlesResource()) {
            bearerAuth(token.value)
            contentType(ContentType.Application.Json)
            setBody(ArticleWrapper(NewArticle(title, description, body, tags)))
          }
        if (createResponse.status != HttpStatusCode.Created) {
          val errBody = createResponse.body<String>()
          throw AssertionError("Failed to create article: ${createResponse.status}, body: $errBody")
        }
        slug = createResponse.body<ArticleResponse>().slug
      }
    }

    "bookmark an article and then unbookmark it, with idempotency" {
      withServer {
        // 1. Bookmark
        val favResponse =
          post(ArticlesResource.Slug.Favorite(parent = ArticlesResource.Slug(slug = slug))) {
            bearerAuth(token.value)
          }
        assert(favResponse.status == HttpStatusCode.OK)
        with(favResponse.body<SingleArticleResponse>().article) {
          assert(favorited)
          assert(favoritesCount == 1L)
        }

        // 2. Idempotency: Bookmark again
        val favResponse2 =
          post(ArticlesResource.Slug.Favorite(parent = ArticlesResource.Slug(slug = slug))) {
            bearerAuth(token.value)
          }
        if (favResponse2.status != HttpStatusCode.OK) {
          val errBody = favResponse2.body<String>()
          println("ERROR BODY: $errBody")
          throw AssertionError(
            "Failed idempotency bookmark: ${favResponse2.status}, body: $errBody"
          )
        }
        with(favResponse2.body<SingleArticleResponse>().article) {
          assert(favorited)
          assert(favoritesCount == 1L)
        }

        // 3. List bookmarked
        val listResponse = get(ArticlesResource.Bookmarked()) { bearerAuth(token.value) }
        assert(listResponse.status == HttpStatusCode.OK)
        with(listResponse.body<MultipleArticlesResponse>()) {
          assert(articlesCount == 1)
          assert(articles.size == 1)
          assert(articles[0].slug == slug)
          assert(articles[0].favorited)
        }

        // 4. Unbookmark
        val unfavResponse =
          delete(ArticlesResource.Slug.Favorite(parent = ArticlesResource.Slug(slug = slug))) {
            bearerAuth(token.value)
          }
        assert(unfavResponse.status == HttpStatusCode.OK)
        with(unfavResponse.body<SingleArticleResponse>().article) {
          assert(!favorited)
          assert(favoritesCount == 0L)
        }

        // 5. Idempotency: Unbookmark again
        val unfavResponse2 =
          delete(ArticlesResource.Slug.Favorite(parent = ArticlesResource.Slug(slug = slug))) {
            bearerAuth(token.value)
          }
        assert(unfavResponse2.status == HttpStatusCode.OK)
        with(unfavResponse2.body<SingleArticleResponse>().article) {
          assert(!favorited)
          assert(favoritesCount == 0L)
        }

        // 6. List bookmarked again
        val listResponse2 = get(ArticlesResource.Bookmarked()) { bearerAuth(token.value) }
        assert(listResponse2.status == HttpStatusCode.OK)
        with(listResponse2.body<MultipleArticlesResponse>()) {
          assert(articlesCount == 0)
          assert(articles.isEmpty())
        }
      }
    }

    "filter articles by favorited query param" {
      withServer {
        // Bookmark first
        post(ArticlesResource.Slug.Favorite(parent = ArticlesResource.Slug(slug = slug))) {
          bearerAuth(token.value)
        }

        // List by favorited param
        val listResponse =
          get(ArticlesResource.ListArticles(favorited = currentUsername)) {
            bearerAuth(token.value)
          }
        assert(listResponse.status == HttpStatusCode.OK)
        with(listResponse.body<MultipleArticlesResponse>()) {
          assert(articlesCount == 1)
          assert(articles.size == 1)
          assert(articles[0].slug == slug)
        }

        // List by favorited param (wrong user)
        val listResponseWrong =
          get(ArticlesResource.ListArticles(favorited = "non_existent_user")) {
            bearerAuth(token.value)
          }
        // User not found will return unprocessable or empty? We mapped UserNotFound to DomainError,
        // which returns 422
        // Actually we might want to check the specific error code, but for now we just verify it
        // doesn't return the article.
        assert(listResponseWrong.status == HttpStatusCode.UnprocessableEntity)
      }
    }
  })
