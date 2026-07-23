/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest;

import static io.camunda.optimize.tomcat.OptimizeResourceConstants.REST_API_PATH;

import io.camunda.identity.sdk.authentication.dto.AuthCodeDto;
import io.camunda.optimize.service.security.authentication.AbstractAuthenticationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(REST_API_PATH + AuthenticationRestService.AUTHENTICATION_PATH)
public class AuthenticationRestService {

  public static final String AUTHENTICATION_PATH = "/authentication";
  public static final String LOGOUT = "/logout";
  public static final String CALLBACK = "/callback";

  private final AbstractAuthenticationService authenticationService;

  public AuthenticationRestService(final AbstractAuthenticationService authenticationService) {
    this.authenticationService = authenticationService;
  }

  @GetMapping(path = CALLBACK)
  public void loginCallback(
      final @RequestParam(name = "code", required = false) String code,
      final @RequestParam(name = "state", required = false) String state,
      final @RequestParam(name = "error", required = false) String error,
      final HttpServletRequest request,
      final HttpServletResponse response)
      throws IOException {
    final AuthCodeDto authCode = new AuthCodeDto(code, state, error);
    authenticationService.loginCallback(authCode, getUri(request), response);
  }

  @GetMapping(path = LOGOUT)
  public void logoutUser(final HttpServletRequest request, final HttpServletResponse response) {
    authenticationService.logout(request.getCookies(), response);
    // SPIKE (ADR-0038): under CSL the user is authenticated via a server-side session, so the
    // legacy cookie cleanup above does not actually log them out. Invalidate the session (which
    // removes it from the SessionStorePort) and clear the SecurityContext. No-op in the legacy
    // stateless setup, where there is no session to invalidate. IdP end-session (Keycloak logout)
    // via CSL's oidcLogout is a follow-up.
    final Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication != null) {
      new SecurityContextLogoutHandler().logout(request, response, authentication);
    }
  }

  private URI getUri(final HttpServletRequest request) {
    try {
      return new URI(request.getRequestURL().toString());
    } catch (final URISyntaxException e) {
      throw new RuntimeException(e);
    }
  }
}
