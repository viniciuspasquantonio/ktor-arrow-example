package io.github.nomisrev.auth

import io.github.nomisrev.service.JwtService
import io.ktor.server.resources.delete
import io.ktor.server.resources.get
import io.ktor.server.resources.post
import io.ktor.server.resources.put
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingContext

/** PublicRead: permite convidado; se houver JWT válido, expõe userId. */
inline fun <reified R : Any> Route.publicGet(
  jwtService: JwtService,
  crossinline body: suspend RoutingContext.(R, JwtContext?) -> Unit,
) {
  get<R> { resource ->
    optionalJwtAuth(jwtService) { jwtContext -> body(this, resource, jwtContext) }
  }
}

/** AuthRead: exige JWT válido; fornece JwtContext. */
inline fun <reified R : Any> Route.authGet(
  jwtService: JwtService,
  crossinline body: suspend RoutingContext.(R, JwtContext) -> Unit,
) {
  get<R> { resource -> jwtAuth(jwtService) { jwtContext -> body(this, resource, jwtContext) } }
}

/** Write: exige JWT válido; fornece JwtContext. */
inline fun <reified R : Any> Route.authPost(
  jwtService: JwtService,
  crossinline body: suspend RoutingContext.(R, JwtContext) -> Unit,
) {
  post<R> { resource -> jwtAuth(jwtService) { jwtContext -> body(this, resource, jwtContext) } }
}

/** Write: exige JWT válido; fornece JwtContext. */
inline fun <reified R : Any> Route.authPut(
  jwtService: JwtService,
  crossinline body: suspend RoutingContext.(R, JwtContext) -> Unit,
) {
  put<R> { resource -> jwtAuth(jwtService) { jwtContext -> body(this, resource, jwtContext) } }
}

/** Write: exige JWT válido; fornece JwtContext. */
inline fun <reified R : Any> Route.authDelete(
  jwtService: JwtService,
  crossinline body: suspend RoutingContext.(R, JwtContext) -> Unit,
) {
  delete<R> { resource -> jwtAuth(jwtService) { jwtContext -> body(this, resource, jwtContext) } }
}
