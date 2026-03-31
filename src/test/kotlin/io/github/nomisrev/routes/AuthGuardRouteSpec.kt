package io.github.nomisrev.routes

import io.github.nomisrev.withServer
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.plugins.resources.delete
import io.ktor.client.plugins.resources.get
import io.ktor.client.plugins.resources.post
import io.ktor.client.plugins.resources.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType

class AuthGuardRouteSpec :
  BehaviorSpec({
    Given("PublicRead endpoints") {
      When("calling GET /api/tags without authorization") {
        Then("it should return 200 OK") {
          withServer {
            val response = get(TagsResource())
            response.status shouldBe HttpStatusCode.OK
          }
        }
      }

      When("calling GET /api/profiles/{username} without authorization") {
        Then("it should return 200 OK or 404 (but not 401)") {
          withServer {
            val response = get(ProfilesResource.Username(username = "someuser"))
            // Either OK or UnprocessableEntity (if user doesn't exist), but not Unauthorized
            println("Profile response: ${response.status}")
            (response.status == HttpStatusCode.OK ||
              response.status == HttpStatusCode.UnprocessableEntity) shouldBe true
          }
        }
      }

      When("calling GET /api/articles/{slug} without authorization") {
        Then("it should return 200 OK or 404 (but not 401)") {
          withServer {
            val response = get(ArticlesResource.Slug(slug = "some-slug"))
            println("Article response: ${response.status}")
            (response.status == HttpStatusCode.OK ||
              response.status == HttpStatusCode.UnprocessableEntity) shouldBe true
          }
        }
      }
    }

    Given("AuthRead endpoints") {
      When("calling GET /api/user without authorization") {
        Then("it should return 401 Unauthorized") {
          withServer {
            val response = get(UserResource())
            response.status shouldBe HttpStatusCode.Unauthorized
          }
        }
      }

      When("calling GET /api/article/feed without authorization") {
        Then("it should return 401 Unauthorized") {
          withServer {
            val response = get(ArticleResource.Feed(offsetParam = 0, limitParam = 20))
            response.status shouldBe HttpStatusCode.Unauthorized
          }
        }
      }

      When("calling GET /api/articles/{slug}/comments without authorization") {
        Then("it should return 401 Unauthorized") {
          withServer {
            val response = get(ArticlesResource.Comments(slug = "some-slug"))
            response.status shouldBe HttpStatusCode.Unauthorized
          }
        }
      }
    }

    Given("Write endpoints") {
      When("calling PUT /api/user without authorization") {
        Then("it should return 401 Unauthorized") {
          withServer {
            val response =
              put(UserResource()) {
                contentType(ContentType.Application.Json)
                setBody(UserWrapper(UpdateUser(username = "new")))
              }
            response.status shouldBe HttpStatusCode.Unauthorized
          }
        }
      }

      When("calling POST /api/articles without authorization") {
        Then("it should return 401 Unauthorized") {
          withServer {
            val response =
              post(ArticlesResource()) {
                contentType(ContentType.Application.Json)
                setBody(ArticleWrapper(NewArticle("title", "desc", "body", emptyList())))
              }
            response.status shouldBe HttpStatusCode.Unauthorized
          }
        }
      }

      When("calling PUT /api/articles/{slug} without authorization") {
        Then("it should return 401 Unauthorized") {
          withServer {
            val response =
              put(ArticlesResource.Slug(slug = "some-slug")) {
                contentType(ContentType.Application.Json)
                setBody(ArticleWrapper(UpdateArticle(title = "new")))
              }
            response.status shouldBe HttpStatusCode.Unauthorized
          }
        }
      }

      When("calling DELETE /api/articles/{slug} without authorization") {
        Then("it should return 401 Unauthorized") {
          withServer {
            val response = delete(ArticlesResource.Slug(slug = "some-slug"))
            response.status shouldBe HttpStatusCode.Unauthorized
          }
        }
      }

      When("calling POST /api/articles/{slug}/favorite without authorization") {
        Then("it should return 401 Unauthorized") {
          withServer {
            val response =
              post(
                ArticlesResource.Slug.Favorite(parent = ArticlesResource.Slug(slug = "some-slug"))
              )
            response.status shouldBe HttpStatusCode.Unauthorized
          }
        }
      }

      When("calling DELETE /api/articles/{slug}/favorite without authorization") {
        Then("it should return 401 Unauthorized") {
          withServer {
            val response =
              delete(
                ArticlesResource.Slug.Favorite(parent = ArticlesResource.Slug(slug = "some-slug"))
              )
            response.status shouldBe HttpStatusCode.Unauthorized
          }
        }
      }

      When("calling POST /api/articles/{slug}/comments without authorization") {
        Then("it should return 401 Unauthorized") {
          withServer {
            val response =
              post(ArticlesResource.Comments(slug = "some-slug")) {
                contentType(ContentType.Application.Json)
                setBody(NewComment("comment body"))
              }
            response.status shouldBe HttpStatusCode.Unauthorized
          }
        }
      }

      When("calling DELETE /api/articles/{slug}/comments/{id} without authorization") {
        Then("it should return 401 Unauthorized") {
          withServer {
            val response =
              delete(
                ArticlesResource.Comments.Id(
                  parent = ArticlesResource.Comments(slug = "some-slug"),
                  id = 1,
                )
              )
            response.status shouldBe HttpStatusCode.Unauthorized
          }
        }
      }

      When("calling POST /api/profiles/{username}/follow without authorization") {
        Then("it should return 401 Unauthorized") {
          withServer {
            val response = post(ProfilesResource.Follow(username = "someuser"))
            response.status shouldBe HttpStatusCode.Unauthorized
          }
        }
      }

      When("calling DELETE /api/profiles/{username}/follow without authorization") {
        Then("it should return 401 Unauthorized") {
          withServer {
            val response = delete(ProfilesResource.Follow(username = "someuser"))
            response.status shouldBe HttpStatusCode.Unauthorized
          }
        }
      }
    }
  })
