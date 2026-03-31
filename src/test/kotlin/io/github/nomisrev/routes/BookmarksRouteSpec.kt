package io.github.nomisrev.routes

import io.github.nomisrev.KotestProject
import io.github.nomisrev.auth.JwtToken
import io.github.nomisrev.service.CreateArticle
import io.github.nomisrev.service.Login
import io.github.nomisrev.service.RegisterUser
import io.github.nomisrev.withServer
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.annotation.Ignored
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlin.properties.Delegates
import kotlinx.serialization.Serializable

@Ignored
class BookmarksRouteSpec :
  BehaviorSpec({
    val validUsername = "bkuser-${java.util.UUID.randomUUID().toString().take(8)}"
    val validEmail = "$validUsername@domain.com"
    val validPw = "123456789"

    val validTags = setOf("arrow", "kotlin", "ktor", "sqldelight")
    val validTitle = "Artigo para bookmark"
    val validDescription = "Descrição"
    val validBody = "Conteúdo"

    var token: JwtToken by Delegates.notNull()

    beforeTest {
      val regResult =
        KotestProject.dependencies
          .get()
          .userService
          .register(RegisterUser(validUsername, validEmail, validPw))
      println("Register result: $regResult")
      regResult.shouldBeRight()

      token =
        KotestProject.dependencies
          .get()
          .userService
          .login(Login(validEmail, validPw))
          .shouldBeRight()
          .first
    }

    Given("um usuário autenticado e um artigo existente") {
      When("ele adiciona e lista bookmarks") {
        Then("o artigo aparece na lista e pode ser removido") {
          withServer { dependencies ->
            val userId = dependencies.jwtService.verifyJwtToken(token).shouldBeRight()
            val article =
              dependencies.articleService
                .createArticle(
                  CreateArticle(userId, validTitle, validDescription, validBody, validTags)
                )
                .shouldBeRight()

            val addResponse =
              post("/api/bookmarks") {
                bearerAuth(token.value)
                contentType(ContentType.Application.Json)
                setBody(BookmarkWrapper(CreateBookmark(slug = article.slug)))
              }

            addResponse.status shouldBe HttpStatusCode.Created

            val listResponse =
              get("/api/bookmarks") {
                bearerAuth(token.value)
                contentType(ContentType.Application.Json)
              }

            listResponse.status shouldBe HttpStatusCode.OK
            val body = listResponse.body<MultipleArticlesResponse>()
            body.articlesCount shouldBe 1
            body.articles.first().slug shouldBe article.slug

            val deleteResponse =
              delete("/api/bookmarks/${article.slug}") {
                bearerAuth(token.value)
                contentType(ContentType.Application.Json)
              }

            deleteResponse.status shouldBe HttpStatusCode.NoContent

            val listAfterDelete =
              get("/api/bookmarks") {
                bearerAuth(token.value)
                contentType(ContentType.Application.Json)
              }

            listAfterDelete.status shouldBe HttpStatusCode.OK
            val after = listAfterDelete.body<MultipleArticlesResponse>()
            after.articlesCount shouldBe 0
            after.articles shouldBe emptyList()
          }
        }
      }
    }
  })

@Serializable private data class BookmarkWrapper<T : Any>(val bookmark: T)

@Serializable private data class CreateBookmark(val slug: String)
