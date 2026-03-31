@file:Suppress("MatchingDeclarationName")

package io.github.nomisrev.routes

import io.github.nomisrev.auth.publicGet
import io.github.nomisrev.repo.TagPersistence
import io.github.nomisrev.service.JwtService
import io.ktor.resources.Resource
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import kotlinx.serialization.Serializable

@Serializable data class TagsResponse(val tags: List<String>)

@Resource("/tags") data class TagsResource(val parent: RootResource = RootResource)

fun Route.tagRoutes(tagPersistence: TagPersistence, jwtService: JwtService) {
  publicGet<TagsResource>(jwtService) { _, _ ->
    val tags = tagPersistence.selectTags()
    call.respond(TagsResponse(tags))
  }
}
