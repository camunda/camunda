/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.shared.management;

import io.camunda.zeebe.gateway.StandaloneGateway;
import java.util.Map;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * Filters incoming requests and forwards them on the fly, without having to use an HTTP redirect
 * code.
 *
 * <p>This is equivalent to the MVC "forward:/route" that was previously used, which does not exist
 * with WebFlux.
 *
 * <p>To use, simply make sure you provide a single "forwardRoutes" bean, e.g. {@link
 * StandaloneGateway#forwardedRoutes()}. The mapping is expected to be SOURCE -> DEST, where the
 * source is the original request path, and the destination is the forwarded path.
 *
 * <p>NOTE: only the path of the URL is modified, nothing else is touched.
 *
 * <p>The filter is not annotated with {@link org.springframework.stereotype.Component} on purpose;
 * instead, each application should create a bean which will provide an instance of this filter.
 */
public class WebFluxForwarder implements WebFilter {

  private final Map<String, String> routes;

  public WebFluxForwarder(final Map<String, String> forwardedRoutes) {
    routes = forwardedRoutes;
  }

  @SuppressWarnings("NullableProblems")
  @Override
  public Mono<Void> filter(final ServerWebExchange exchange, final WebFilterChain chain) {
    if (routes.isEmpty()) {
      return chain.filter(exchange);
    }

    ServerWebExchange result = exchange;
    final var request = exchange.getRequest();
    final var route = request.getPath().pathWithinApplication().toString();
    final var forwardPath = routes.get(route);

    if (forwardPath != null) {
      result = exchange.mutate().request(request.mutate().path(forwardPath).build()).build();
    }

    return chain.filter(result);
  }
}
