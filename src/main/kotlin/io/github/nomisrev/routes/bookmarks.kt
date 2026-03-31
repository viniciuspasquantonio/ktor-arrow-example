package io.github.nomisrev.routes

import arrow.core.raise.either
import io.github.nomisrev.auth.jwtAuth
import io.github.nomisrev.service.BookmarkService
import io.github.nomisrev.service.JwtService
import io.ktor.http.HttpStatusCode
import io.ktor.resources.Resource
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.resources.delete
import io.ktor.server.resources.get
import io.ktor.server.resources.post
import io.ktor.server.routing.Route
import kotlinx.serialization.Serializable

@Resource("/bookmarks")
data class BookmarksResource(
  val offsetParam: Int = 0,
  val limitParam: Int = 20,
  val parent: RootResource = RootResource,
) {
  @Resource("{slug}")
  data class Slug(val parent: BookmarksResource = BookmarksResource(), val slug: String)
}

@Serializable data class CreateBookmark(val slug: String)

@Serializable data class BookmarkWrapper<T : Any>(val bookmark: T)

fun Route.bookmarkRoutes(bookmarkService: BookmarkService, jwtService: JwtService) {
  post<BookmarksResource> {
    jwtAuth(jwtService) { (_, userId) ->
      either {
          val request = call.receive<BookmarkWrapper<CreateBookmark>>()
          bookmarkService.addBookmark(userId, request.bookmark.slug).bind()
        }
        .map { SingleArticleResponse(it) }
        .respond(HttpStatusCode.Created)
    }
  }

  delete<BookmarksResource.Slug> { resource ->
    jwtAuth(jwtService) { (_, userId) ->
      either { bookmarkService.removeBookmark(userId, resource.slug).bind() }
        .map { SingleArticleResponse(it) }
        .respond(HttpStatusCode.OK)
    }
  }

  get<BookmarksResource> { resource ->
    jwtAuth(jwtService) { (_, userId) ->
      either {
          bookmarkService
            .listBookmarks(userId, resource.limitParam.toLong(), resource.offsetParam.toLong())
            .bind()
        }
        .respond(HttpStatusCode.OK)
    }
  }
}
