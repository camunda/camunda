/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.config;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static io.camunda.authentication.config.controllers.TestApiController.DUMMY_UNPROTECTED_ENDPOINT;
import static io.camunda.authentication.config.controllers.TestApiController.DUMMY_V2_API_ENDPOINT;
import static io.camunda.authentication.config.controllers.TestApiController.DUMMY_WEBAPP_ENDPOINT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;

import com.github.tomakehurst.wiremock.common.Slf4jNotifier;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import io.camunda.authentication.config.controllers.OidcFlowTestContext;
import io.camunda.authentication.config.controllers.WebSecurityConfigTestContext;
import io.camunda.authentication.filters.AbstractAdminUserCheckFilter;
import io.camunda.authentication.filters.AbstractWebComponentAuthorizationCheckFilter;
import io.camunda.security.auth.CamundaAuthentication;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureWebMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * Regression tests for filter auto-registration in servlet container.
 *
 * <p>Both filters must only run when explicitly added to the WEBAPP security chain and must not be
 * globally applied to API or unprotected paths.
 */
class WebSecurityFilterAutoRegistrationRegressionTest {

  @Nested
  @SuppressWarnings({"SpringBootApplicationProperties", "WrongPropertyKeyValueDelimiter"})
  @AutoConfigureMockMvc
  @AutoConfigureWebMvc
  @SpringBootTest(
      classes = {
        BasicFilterInvocationTestConfig.class,
        WebSecurityConfig.class,
      },
      properties = {
        "camunda.security.authentication.method=basic",
        "camunda.security.authentication.unprotected-api=false",
      })
  @ActiveProfiles("consolidated-auth")
  class BasicAuthMode {

    @Autowired MockMvcTester mockMvcTester;
    @Autowired AbstractAdminUserCheckFilter adminUserCheckFilter;
    @Autowired AbstractWebComponentAuthorizationCheckFilter webComponentAuthorizationCheckFilter;

    @Test
    void shouldNotApplyWebappFiltersToApiPaths() throws ServletException, IOException {
      final MvcTestResult apiResult =
          mockMvcTester
              .get()
              .uri(DUMMY_V2_API_ENDPOINT)
              .accept(MediaType.APPLICATION_JSON)
              .exchange();
      assertThat(apiResult).hasStatus(HttpStatus.UNAUTHORIZED);

      verify(adminUserCheckFilter, never()).doFilter(any(), any(), any());
      verify(webComponentAuthorizationCheckFilter, never()).doFilter(any(), any(), any());
    }

    @Test
    void shouldNotApplyWebappFiltersToUnprotectedPaths() throws ServletException, IOException {
      final MvcTestResult unprotectedResult =
          mockMvcTester
              .get()
              .uri(DUMMY_UNPROTECTED_ENDPOINT)
              .accept(MediaType.TEXT_HTML)
              .exchange();
      assertThat(unprotectedResult).hasStatus(HttpStatus.OK);

      verify(adminUserCheckFilter, never()).doFilter(any(), any(), any());
      verify(webComponentAuthorizationCheckFilter, never()).doFilter(any(), any(), any());
    }

    @Test
    void shouldApplyWebappFiltersOnlyToWebappChain() throws ServletException, IOException {
      final CamundaAuthentication auth =
          new CamundaAuthentication(
              "test-user", null, false, List.of(), List.of(), List.of(), List.of(), Map.of());
      SecurityContextHolder.getContext()
          .setAuthentication(new TestingAuthenticationToken(auth, null));

      final MvcTestResult webappResult =
          mockMvcTester.get().uri(DUMMY_WEBAPP_ENDPOINT).accept(MediaType.TEXT_HTML).exchange();
      assertThat(webappResult).hasStatus(HttpStatus.OK);

      verify(adminUserCheckFilter).doFilter(any(), any(), any());
      verify(webComponentAuthorizationCheckFilter).doFilter(any(), any(), any());

      clearInvocations(adminUserCheckFilter, webComponentAuthorizationCheckFilter);
      SecurityContextHolder.clearContext();
    }
  }

  @Nested
  @AutoConfigureMockMvc
  @AutoConfigureWebMvc
  @SpringBootTest(
      classes = {
        OidcFilterInvocationTestConfig.class,
        WebSecurityConfig.class,
      },
      properties = {
        "camunda.security.authentication.unprotected-api=false",
        "camunda.security.authentication.method=oidc",
        "camunda.security.authentication.oidc.client-id=dummy-client",
        "camunda.security.authentication.oidc.client-secret=dummy-secret",
        "camunda.security.authentication.oidc.redirect-uri=http://localhost/sso-callback",
      })
  @ActiveProfiles("consolidated-auth")
  class OidcAuthMode {

    @Autowired MockMvcTester mockMvcTester;
    @Autowired AbstractWebComponentAuthorizationCheckFilter webComponentAuthorizationCheckFilter;

    static final String REALM = "dummy";
    static final String ENDPOINT_WELL_KNOWN_OIDC =
        "/realms/" + REALM + "/.well-known/openid-configuration";
    static final String ENDPOINT_WELL_KNOWN_JWKS = "/realms/" + REALM + "/.well-known/jwks.json";

    @RegisterExtension
    static WireMockExtension wireMock =
        WireMockExtension.newInstance()
            .configureStaticDsl(true)
            .options(wireMockConfig().notifier(new Slf4jNotifier(false)).dynamicPort())
            .failOnUnmatchedRequests(true)
            .build();

    @DynamicPropertySource
    static void registerWireMockProperties(final DynamicPropertyRegistry registry) {
      registry.add(
          "camunda.security.authentication.oidc.issuer-uri",
          () -> "http://localhost:" + wireMock.getPort() + "/realms/" + REALM);
    }

    @BeforeAll
    static void stubOidcDiscoveryEndpoint() {
      stubFor(
          get(urlEqualTo(ENDPOINT_WELL_KNOWN_OIDC))
              .willReturn(
                  aResponse()
                      .withHeader("Content-Type", "application/json")
                      .withBody(wellKnownResponse())));

      stubFor(
          get(urlEqualTo(ENDPOINT_WELL_KNOWN_JWKS))
              .willReturn(
                  aResponse()
                      .withHeader("Content-Type", "application/json")
                      .withBody(
                          """
                          {"keys":[{"kty":"RSA","kid":"test-key","n":"AQAB","e":"AQAB"}]}
                          """)));
    }

    private static String wellKnownResponse() {
      return """
          {
            "issuer": "http://localhost:000000/realms/dummy",
            "authorization_endpoint": "http://localhost:000000/realms/dummy/protocol/openid-connect/auth",
            "token_endpoint": "http://localhost:000000/realms/dummy/protocol/openid-connect/token",
            "userinfo_endpoint": "http://localhost:000000/realms/dummy/protocol/openid-connect/userinfo",
            "jwks_uri": "http://localhost:000000/realms/dummy/.well-known/jwks.json",
            "response_types_supported": ["code"],
            "subject_types_supported": ["public"],
            "id_token_signing_alg_values_supported": ["RS256"],
            "scopes_supported": ["openid","profile"]
          }
          """
          .replace("000000", String.valueOf(wireMock.getPort()));
    }

    @Test
    void shouldNotApplyWebappFiltersToApiPaths() throws ServletException, IOException {
      final MvcTestResult apiResult =
          mockMvcTester
              .get()
              .uri(DUMMY_V2_API_ENDPOINT)
              .accept(MediaType.APPLICATION_JSON)
              .exchange();
      assertThat(apiResult).hasStatus(HttpStatus.UNAUTHORIZED);

      verify(webComponentAuthorizationCheckFilter, never()).doFilter(any(), any(), any());
    }

    @Test
    void shouldNotApplyWebappFiltersToUnprotectedPaths() throws ServletException, IOException {
      final MvcTestResult unprotectedResult =
          mockMvcTester
              .get()
              .uri(DUMMY_UNPROTECTED_ENDPOINT)
              .accept(MediaType.TEXT_HTML)
              .exchange();
      assertThat(unprotectedResult).hasStatus(HttpStatus.OK);

      verify(webComponentAuthorizationCheckFilter, never()).doFilter(any(), any(), any());
    }

    @Test
    void shouldApplyWebappFilterOnlyToWebappChain() throws ServletException, IOException {
      final CamundaAuthentication auth =
          new CamundaAuthentication(
              "test-user", null, false, List.of(), List.of(), List.of(), List.of(), Map.of());
      final var principalAuth = new TestingAuthenticationToken(auth, null);
      principalAuth.setAuthenticated(true);

      final MvcTestResult webappResult =
          mockMvcTester
              .get()
              .uri(DUMMY_WEBAPP_ENDPOINT)
              .with(authentication(principalAuth))
              .accept(MediaType.TEXT_HTML)
              .exchange();

      assertThat(webappResult).hasStatus(HttpStatus.OK);
      verify(webComponentAuthorizationCheckFilter).doFilter(any(), any(), any());
      clearInvocations(webComponentAuthorizationCheckFilter);
    }
  }

  @TestConfiguration
  @Import(WebSecurityConfigTestContext.class)
  static class BasicFilterInvocationTestConfig {

    @Bean
    @Primary
    AbstractAdminUserCheckFilter adminUserCheckFilter() {
      return Mockito.spy(new DelegatingAdminUserCheckFilter());
    }

    @Bean
    @Primary
    AbstractWebComponentAuthorizationCheckFilter webComponentAuthorizationCheckFilter() {
      return Mockito.spy(new DelegatingWebComponentAuthorizationCheckFilter());
    }
  }

  @TestConfiguration
  @Import(OidcFlowTestContext.class)
  static class OidcFilterInvocationTestConfig {

    @Bean
    @Primary
    AbstractWebComponentAuthorizationCheckFilter webComponentAuthorizationCheckFilter() {
      return Mockito.spy(new DelegatingWebComponentAuthorizationCheckFilter());
    }
  }

  static final class DelegatingAdminUserCheckFilter extends AbstractAdminUserCheckFilter {
    @Override
    protected void doFilterInternal(
        final HttpServletRequest request,
        final HttpServletResponse response,
        final FilterChain filterChain)
        throws ServletException, IOException {
      filterChain.doFilter(request, response);
    }
  }

  static final class DelegatingWebComponentAuthorizationCheckFilter
      extends AbstractWebComponentAuthorizationCheckFilter {
    @Override
    protected void doFilterInternal(
        final HttpServletRequest request,
        final HttpServletResponse response,
        final FilterChain filterChain)
        throws ServletException, IOException {
      System.out.println(
          "WebComponentAuthorizationCheckFilter invoked for request: " + request.getRequestURI());
      filterChain.doFilter(request, response);
    }
  }
}
