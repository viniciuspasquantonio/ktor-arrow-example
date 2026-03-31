package io.github.nomisrev.routes

import io.github.nomisrev.KotestProject
import io.github.nomisrev.auth.JwtToken
import io.github.nomisrev.service.RegisterUser
import io.github.nomisrev.withServer
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.call.body
import io.ktor.client.plugins.resources.delete
import io.ktor.client.plugins.resources.get
import io.ktor.client.plugins.resources.post
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlin.properties.Delegates

class BookmarksRouteSpec :
  StringSpec({
    val username = "bookmarkUser"
    val email = "bookmark@domain.com"
    val password = "password123"

    var token: JwtToken by Delegates.notNull()
    var articleSlug: String by Delegates.notNull()

    beforeTest {
      token =
        KotestProject.dependencies
          .get()
          .userService
          .register(RegisterUser(username, email, password))
          .shouldBeRight()
    }

    "POST /api/bookmarks successfully creates a bookmark" {
      withServer {
        // 1. Create an article to bookmark
        val articleResponse =
          post(ArticlesResource()) {
            bearerAuth(token.value)
            contentType(ContentType.Application.Json)
            setBody(ArticleWrapper(NewArticle("Bookmark Test", "Desc", "Body", emptyList())))
          }
        articleResponse.status shouldBe HttpStatusCode.Created
        val article = articleResponse.body<ArticleResponse>()
        articleSlug = article.slug

        // 2. Bookmark it
        val response =
          post(BookmarksResource()) {
            bearerAuth(token.value)
            contentType(ContentType.Application.Json)
            setBody(BookmarkWrapper(CreateBookmark(articleSlug)))
          }

        response.status shouldBe HttpStatusCode.Created
        val body = response.body<SingleArticleResponse>()
        body.article.slug shouldBe articleSlug
      }
    }

    "POST /api/bookmarks is idempotent" {
      withServer {
        val articleResponse =
          post(ArticlesResource()) {
            bearerAuth(token.value)
            contentType(ContentType.Application.Json)
            setBody(
              ArticleWrapper(NewArticle("Bookmark Test Idempotent", "Desc", "Body", emptyList()))
            )
          }
        val slug = articleResponse.body<ArticleResponse>().slug

        // First bookmark
        post(BookmarksResource()) {
          bearerAuth(token.value)
          contentType(ContentType.Application.Json)
          setBody(BookmarkWrapper(CreateBookmark(slug)))
        }

        // Second bookmark
        val response =
          post(BookmarksResource()) {
            bearerAuth(token.value)
            contentType(ContentType.Application.Json)
            setBody(BookmarkWrapper(CreateBookmark(slug)))
          }

        response.status shouldBe HttpStatusCode.Created
        val body = response.body<SingleArticleResponse>()
        body.article.slug shouldBe slug
      }
    }

    "DELETE /api/bookmarks/{slug} removes the bookmark" {
      withServer {
        // Create and bookmark an article first
        val articleResponse =
          post(ArticlesResource()) {
            bearerAuth(token.value)
            contentType(ContentType.Application.Json)
            setBody(ArticleWrapper(NewArticle("Bookmark Delete Test", "Desc", "Body", emptyList())))
          }
        val slug = articleResponse.body<ArticleResponse>().slug

        post(BookmarksResource()) {
          bearerAuth(token.value)
          contentType(ContentType.Application.Json)
          setBody(BookmarkWrapper(CreateBookmark(slug)))
        }

        val response =
          delete(BookmarksResource.Slug(slug = slug)) {
            bearerAuth(token.value)
            contentType(ContentType.Application.Json)
          }

        response.status shouldBe HttpStatusCode.OK
        val body = response.body<SingleArticleResponse>()
        body.article.slug shouldBe slug

        // Verify it's no longer in the list
        val listResponse =
          get(BookmarksResource()) {
            bearerAuth(token.value)
            contentType(ContentType.Application.Json)
          }
        val listBody = listResponse.body<MultipleArticlesResponse>()
        listBody.articles.none { it.slug == slug } shouldBe true
      }
    }

    "GET /api/bookmarks lists bookmarked articles" {
      withServer {
        val articleResponse =
          post(ArticlesResource()) {
            bearerAuth(token.value)
            contentType(ContentType.Application.Json)
            setBody(ArticleWrapper(NewArticle("Bookmark List Test", "Desc", "Body", emptyList())))
          }
        val slug = articleResponse.body<ArticleResponse>().slug

        post(BookmarksResource()) {
          bearerAuth(token.value)
          contentType(ContentType.Application.Json)
          setBody(BookmarkWrapper(CreateBookmark(slug)))
        }

        val response =
          get(BookmarksResource()) {
            bearerAuth(token.value)
            contentType(ContentType.Application.Json)
          }

        response.status shouldBe HttpStatusCode.OK
        val body = response.body<MultipleArticlesResponse>()
        body.articles.any { it.slug == slug } shouldBe true
      }
    }
  })
