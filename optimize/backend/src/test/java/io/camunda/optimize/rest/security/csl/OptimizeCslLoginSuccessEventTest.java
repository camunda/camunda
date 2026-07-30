/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest.security.csl;

import static io.camunda.optimize.rest.security.csl.OptimizeCslLoginSuccessListener.ORIGINAL_USER_ID_CLAIM;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.camunda.optimize.service.security.UserIdMigrationService;
import io.camunda.security.api.context.CamundaAuthenticationProvider;
import io.camunda.security.api.model.CamundaAuthentication;
import jakarta.servlet.FilterChain;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2LoginAuthenticationToken;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.HttpSessionOAuth2AuthorizationRequestRepository;
import org.springframework.security.oauth2.client.web.HttpSessionOAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.client.web.OAuth2LoginAuthenticationFilter;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationExchange;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationResponse;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

/**
 * Wires the real {@link OAuth2LoginAuthenticationFilter} to a real Spring context holding {@link
 * OptimizeCslLoginSuccessListener} and completes an OIDC authorization-code callback, to prove the
 * hook actually fires: Spring Security publishes {@code InteractiveAuthenticationSuccessEvent} on
 * login success, the annotated listener receives it, and the migration runs.
 *
 * <p>Only the token exchange is stubbed (via the {@code AuthenticationManager}), so no IdP is
 * needed. Everything between the filter and the listener is the production wiring.
 */
class OptimizeCslLoginSuccessEventTest {

  private static final String REGISTRATION_ID = "auth0";
  private static final String CALLBACK_PATH = "/login/oauth2/code/" + REGISTRATION_ID;
  private static final String STATE = "state-value";
  private static final String CURRENT_USER_ID = "auth0|new-identity";
  private static final String PREVIOUS_USER_ID = "auth0|old-identity";

  private final UserIdMigrationService userIdMigrationService = mock(UserIdMigrationService.class);
  private final CamundaAuthenticationProvider camundaAuthenticationProvider =
      mock(CamundaAuthenticationProvider.class);

  private AnnotationConfigApplicationContext context;

  @BeforeEach
  void setUp() {
    final CamundaAuthentication camundaAuthentication = mock(CamundaAuthentication.class);
    when(camundaAuthentication.authenticatedUsername()).thenReturn(CURRENT_USER_ID);
    when(camundaAuthenticationProvider.getCamundaAuthentication())
        .thenReturn(camundaAuthentication);

    context = new AnnotationConfigApplicationContext();
    // The listener carries the CCSaaS + CSL-flag conditions, so the context has to satisfy them for
    // the bean to exist at all.
    context.getEnvironment().setActiveProfiles("cloud");
    context
        .getEnvironment()
        .getPropertySources()
        .addFirst(
            new MapPropertySource(
                "csl-flag", Map.of("optimize.security.csl.enabled", Boolean.TRUE.toString())));
    context.registerBean(UserIdMigrationService.class, () -> userIdMigrationService);
    context.registerBean(CamundaAuthenticationProvider.class, () -> camundaAuthenticationProvider);
    context.registerBean(OptimizeCslLoginSuccessListener.class);
    context.refresh();
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
    context.close();
  }

  @Test
  void shouldMigrateUserIdWhenSpringSecurityCompletesTheOidcLogin() throws Exception {
    final MockHttpServletRequest request = callbackRequest();
    final MockHttpServletResponse response = new MockHttpServletResponse();

    loginFilter().doFilter(request, response, mock(FilterChain.class));

    assertThat(SecurityContextHolder.getContext().getAuthentication())
        .describedAs("login must have succeeded for the success event to be published")
        .isNotNull();
    verify(userIdMigrationService).migrateUserIdIfNeeded(CURRENT_USER_ID, PREVIOUS_USER_ID);
  }

  @Test
  void shouldCompleteTheLoginEvenWhenTheMigrationHookFails() throws Exception {
    // given — resolving the CSL user throws, as it does when no CamundaAuthenticationConverter
    // matches. The event is published before the success handler runs, so a propagated exception
    // would turn a successful login into an error.
    when(camundaAuthenticationProvider.getCamundaAuthentication())
        .thenThrow(new IllegalStateException("no matching converter"));

    final MockHttpServletRequest request = callbackRequest();
    final MockHttpServletResponse response = new MockHttpServletResponse();

    // when
    loginFilter().doFilter(request, response, mock(FilterChain.class));

    // then — the session is established and the success handler still redirected
    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
    assertThat(response.getStatus()).isEqualTo(302);
    verifyNoInteractions(userIdMigrationService);
  }

  private OAuth2LoginAuthenticationFilter loginFilter() {
    final ClientRegistration clientRegistration = clientRegistration();
    final OAuth2LoginAuthenticationFilter filter =
        new OAuth2LoginAuthenticationFilter(
            new InMemoryClientRegistrationRepository(clientRegistration),
            new HttpSessionOAuth2AuthorizedClientRepository(),
            OAuth2LoginAuthenticationFilter.DEFAULT_FILTER_PROCESSES_URI);
    // Stands in for the token exchange and userinfo call an IdP would answer.
    filter.setAuthenticationManager(
        authentication ->
            new OAuth2LoginAuthenticationToken(
                clientRegistration,
                new OAuth2AuthorizationExchange(authorizationRequest(), authorizationResponse()),
                oidcUser(),
                AuthorityUtils.createAuthorityList("ROLE_USER"),
                accessToken()));
    filter.setApplicationEventPublisher(context);
    return filter;
  }

  private MockHttpServletRequest callbackRequest() {
    final MockHttpServletRequest request = new MockHttpServletRequest("GET", CALLBACK_PATH);
    request.setServletPath(CALLBACK_PATH);
    request.addParameter(OAuth2ParameterNames.CODE, "authorization-code");
    request.addParameter(OAuth2ParameterNames.STATE, STATE);
    new HttpSessionOAuth2AuthorizationRequestRepository()
        .saveAuthorizationRequest(authorizationRequest(), request, new MockHttpServletResponse());
    return request;
  }

  private static ClientRegistration clientRegistration() {
    return ClientRegistration.withRegistrationId(REGISTRATION_ID)
        .clientId("optimize")
        .clientSecret("secret")
        .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
        .redirectUri("{baseUrl}" + CALLBACK_PATH)
        .scope("openid")
        .authorizationUri("https://idp.example.com/authorize")
        .tokenUri("https://idp.example.com/token")
        .jwkSetUri("https://idp.example.com/jwks")
        .build();
  }

  private static OAuth2AuthorizationRequest authorizationRequest() {
    return OAuth2AuthorizationRequest.authorizationCode()
        .authorizationUri("https://idp.example.com/authorize")
        .clientId("optimize")
        .redirectUri("http://localhost" + CALLBACK_PATH)
        .scope("openid")
        .state(STATE)
        .attributes(Map.of(OAuth2ParameterNames.REGISTRATION_ID, REGISTRATION_ID))
        .build();
  }

  private static OAuth2AuthorizationResponse authorizationResponse() {
    return OAuth2AuthorizationResponse.success("authorization-code")
        .redirectUri("http://localhost" + CALLBACK_PATH)
        .state(STATE)
        .build();
  }

  private static OidcUser oidcUser() {
    final OidcIdToken idToken =
        new OidcIdToken(
            "id-token",
            Instant.now(),
            Instant.now().plusSeconds(300),
            Map.of("sub", CURRENT_USER_ID, ORIGINAL_USER_ID_CLAIM, PREVIOUS_USER_ID));
    return new DefaultOidcUser(AuthorityUtils.createAuthorityList("ROLE_USER"), idToken);
  }

  private static OAuth2AccessToken accessToken() {
    return new OAuth2AccessToken(
        OAuth2AccessToken.TokenType.BEARER,
        "access-token",
        Instant.now(),
        Instant.now().plusSeconds(300),
        java.util.Set.copyOf(List.of("openid")));
  }
}
