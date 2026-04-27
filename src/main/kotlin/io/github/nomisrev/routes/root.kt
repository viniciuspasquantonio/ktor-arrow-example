package io.github.nomisrev.routes

import io.github.nomisrev.env.Dependencies
import io.ktor.resources.Resource
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing

fun Application.routes(deps: Dependencies) = routing {
  get("/api/health") { call.respond(mapOf("status" to "ok", "version" to "1.0.0")) }
  userRoutes(deps.userService, deps.jwtService)
  tagRoutes(deps.tagPersistence)
  articleRoutes(deps.articleService, deps.jwtService)
  commentRoutes(deps.userService, deps.articleService, deps.jwtService)
  profileRoutes(deps.userPersistence, deps.jwtService)
  commentRoutes(deps.articleService, deps.jwtService)
}

@Resource("/api") data object RootResource
