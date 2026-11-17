/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static io.camunda.authentication.config.controllers.TestApiController.DUMMY_UNPROTECTED_ENDPOINT;
import static io.camunda.authentication.config.controllers.TestApiController.DUMMY_V2_API_ENDPOINT;
import static io.camunda.authentication.config.controllers.TestApiController.DUMMY_WEBAPP_ENDPOINT;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.common.Slf4jNotifier;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import io.camunda.authentication.config.WebSecurityConfig;
import io.camunda.authentication.config.controllers.OidcFlowTestContext;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureWebMvc;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoderInitializationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * An unreachable identity provider must not keep the application from starting. Discovery ran while
 * the security chains were built, so a provider that was down failed the application context.
 * Discovery now happens on first use.
 *
 * <p>The bearer token is not a JWT, so it stays unverifiable whether or not a decoder was built.
 */
@SuppressWarnings({"SpringBootApplicationProperties", "WrongPropertyKeyValueDelimiter"})
@AutoConfigureMockMvc
@AutoConfigureWebMvc
@SpringBootTest(
    classes = {
      OidcFlowTestContext.class,
      WebSecurityConfig.class,
    },
    properties = {
      "camunda.security.authentication.method=oidc",
      "camunda.security.authentication.unprotected-api=false",
      "camunda.security.authentication.oidc.client-id=camunda-client",
      "camunda.security.authentication.oidc.client-secret=camunda-client-secret",
      "camunda.security.authentication.oidc.redirect-uri=http://localhost/sso-callback",
    })
@ActiveProfiles("consolidated-auth")
public class OidcUnreachableIssuerStartupTest {

  @RegisterExtension
  static WireMockExtension wireMock =
      WireMockExtension.newInstance()
          .configureStaticDsl(true)
          .options(wireMockConfig().notifier(new Slf4jNotifier(false)).dynamicPort())
          .build();

  private static final String REALM = "camunda-test";
  private static final String DISCOVERY_ENDPOINT =
      "/realms/" + REALM + "/.well-known/openid-configuration";
  private static final String PROTECTED_RESOURCE_METADATA_ENDPOINT =
      "/.well-known/oauth-protected-resource";

  // the token never gets parsed: building the decoder against the unreachable provider fails first
  private static final String UNVERIFIABLE_TOKEN = "not-a-real-token";

  @Autowired MockMvcTester mockMvcTester;

  @DynamicPropertySource
  static void registerIssuerUri(final DynamicPropertyRegistry registry) {
    registry.add("camunda.security.authentication.oidc.issuer-uri", () -> issuerUri());
  }

  @BeforeEach
  void breakDiscovery() {
    stubFor(get(urlEqualTo(DISCOVERY_ENDPOINT)).willReturn(aResponse().withStatus(500)));
  }

  @Test
  public void shouldStartWhenTheIssuerIsUnreachable() {
    // given an application context that came up against an identity provider failing discovery

    // when an unauthenticated request hits an unprotected endpoint
    final var result = mockMvcTester.get().uri(DUMMY_UNPROTECTED_ENDPOINT).exchange();

    // then it is served, so the context is up rather than restart-looping
    assertThat(result).hasStatusOk();
  }

  /**
   * The outage and the recovery share one test: the application context keeps the registration it
   * resolved, so a separate recovery test would depend on the order the two ran in.
   */
  @Test
  public void shouldFailApiRequestsWhileTheIssuerIsUnreachableAndRecoverWithoutRestart() {
    // when a bearer token is presented while the provider cannot be reached
    final var result = callApiWithToken();

    // then that request fails as an outage rather than as a rejected credential: the decoder
    // cannot be built without the provider, and that failure escapes the chain instead of reaching
    // the bearer entry point, which a servlet container renders as a 500
    assertThat(result).hasFailed().failure().isInstanceOf(JwtDecoderInitializationException.class);

    // when the provider answers again
    stubFor(get(urlEqualTo(DISCOVERY_ENDPOINT)).willReturn(okJson(discoveryDocument())));

    // then the very next request builds the decoder — no restart needed — and the same token is
    // now a rejected credential: a decoder exists to turn it down as malformed
    assertThat(callApiWithToken()).hasStatus(HttpStatus.UNAUTHORIZED);
  }

  @Test
  public void shouldStillRedirectWebappRequestsToTheProvider() {
    // when a browser navigates to a protected webapp path
    final var result = mockMvcTester.get().uri(DUMMY_WEBAPP_ENDPOINT).exchange();

    // then the login redirect is served from configuration alone, without discovery
    assertThat(result)
        .hasStatus(HttpStatus.FOUND)
        .hasHeader("Location", "/oauth2/authorization/oidc");
  }

  @Test
  public void shouldServeProtectedResourceMetadataWhileTheIssuerIsUnreachable() {
    // given the RFC 9728 metadata lists the configured issuers, which needs no discovery
    assertThat(
            mockMvcTester
                .get()
                .uri(PROTECTED_RESOURCE_METADATA_ENDPOINT)
                .accept(MediaType.APPLICATION_JSON)
                .exchange())
        .hasStatusOk()
        .bodyJson()
        .extractingPath("authorization_servers")
        .asInstanceOf(InstanceOfAssertFactories.LIST)
        .containsExactly(issuerUri());
  }

  private MvcTestResult callApiWithToken() {
    return mockMvcTester
        .get()
        .uri(DUMMY_V2_API_ENDPOINT)
        .header("Authorization", "Bearer " + UNVERIFIABLE_TOKEN)
        .accept(MediaType.APPLICATION_JSON)
        .exchange();
  }

  private static String issuerUri() {
    return "http://localhost:" + wireMock.getPort() + "/realms/" + REALM;
  }

  private static String discoveryDocument() {
    return """
        {
            "issuer": "ISSUER",
            "authorization_endpoint": "ISSUER/oauth/authorize",
            "token_endpoint": "ISSUER/oauth/token",
            "userinfo_endpoint": "ISSUER/userinfo",
            "jwks_uri": "ISSUER/.well-known/jwks.json",
            "response_types_supported": ["code"],
            "subject_types_supported": ["public"],
            "id_token_signing_alg_values_supported": ["RS256"]
        }
        """
        .replace("ISSUER", issuerUri());
  }
}
