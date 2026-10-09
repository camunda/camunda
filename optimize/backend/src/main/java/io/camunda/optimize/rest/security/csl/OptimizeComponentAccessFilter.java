/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest.security.csl;

import io.camunda.security.api.context.CamundaAuthenticationProvider;
import io.camunda.security.api.model.CamundaAuthentication;
import io.camunda.security.api.model.authz.AuthorizationRejection;
import io.camunda.security.api.model.authz.AuthorizationResourceType;
import io.camunda.security.api.model.authz.PermissionType;
import io.camunda.security.core.auth.RequiredAuthorization;
import io.camunda.security.core.port.in.AuthorizationCheckPort;
import io.camunda.security.spring.spi.WebAppAccessDeniedHandlerPort;
import io.camunda.security.spring.spi.WebAppProviderPort;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Checks component access on every request the {@link WebAppProviderPort} claims, and hands a
 * denied request to the {@link WebAppAccessDeniedHandlerPort}. A request without an authentication,
 * or with an anonymous one, passes through: the chain's own rules decide whether it may be served.
 *
 * <p>Optimize runs this instead of CSL's {@code WebAppAuthorizationCheckFilter}, because that
 * filter exempts a request by the shape of its URI, any URI ending in {@code /forbidden} or in a
 * static-asset suffix. Optimize takes the file name of an export from the last path segment, for
 * example {@code /api/export/csv/{reportId}/{fileName}}, so a caller could name the file so that it
 * matches an exemption and reach the data without the check. Optimize needs no exemption: its
 * assets are served from paths where no check runs, and a denial is answered in place instead of
 * being redirected to a page.
 *
 * <p>The check is not gated on {@code camunda.security.authorizations.enabled}. Access to Optimize
 * is not a resource authorization the operator can switch off, it is the permission the
 * installation's Identity grants, and it was enforced regardless of that flag before Optimize moved
 * to CSL.
 */
public final class OptimizeComponentAccessFilter extends OncePerRequestFilter {

  private static final Logger LOG = LoggerFactory.getLogger(OptimizeComponentAccessFilter.class);

  private static final RequiredAuthorization<Void> COMPONENT_ACCESS =
      RequiredAuthorization.of(
          builder ->
              builder
                  .resourceType(AuthorizationResourceType.COMPONENT)
                  .permissionType(PermissionType.ACCESS));

  private final WebAppProviderPort webAppProvider;
  private final AuthorizationCheckPort authorizationCheckPort;
  private final WebAppAccessDeniedHandlerPort accessDeniedHandler;
  private final CamundaAuthenticationProvider authenticationProvider;

  public OptimizeComponentAccessFilter(
      final WebAppProviderPort webAppProvider,
      final AuthorizationCheckPort authorizationCheckPort,
      final WebAppAccessDeniedHandlerPort accessDeniedHandler,
      final CamundaAuthenticationProvider authenticationProvider) {
    this.webAppProvider = webAppProvider;
    this.authorizationCheckPort = authorizationCheckPort;
    this.accessDeniedHandler = accessDeniedHandler;
    this.authenticationProvider = authenticationProvider;
  }

  @Override
  protected void doFilterInternal(
      final HttpServletRequest request,
      final HttpServletResponse response,
      final FilterChain filterChain)
      throws ServletException, IOException {

    final CamundaAuthentication authentication = authenticationProvider.getCamundaAuthentication();
    if (authentication == null || authentication.isAnonymous()) {
      filterChain.doFilter(request, response);
      return;
    }

    final Optional<String> webApp = webAppProvider.webAppFor(request);
    if (webApp.isEmpty()) {
      filterChain.doFilter(request, response);
      return;
    }

    final String component = webApp.get();
    final var result =
        authorizationCheckPort.check(authentication, COMPONENT_ACCESS.withResourceId(component));
    if (result.isRight()) {
      filterChain.doFilter(request, response);
      return;
    }

    final AuthorizationRejection rejection = result.leftValue();
    LOG.debug(
        "Denying access to component '{}' at {}: {}",
        component,
        request.getRequestURI(),
        rejection);
    accessDeniedHandler.handle(request, response, component, authentication);
  }
}
