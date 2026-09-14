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
import java.nio.charset.StandardCharsets;
import java.util.Base64;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/**
 * An OIDC deployment whose identity provider is unreachable when the application boots used to fail
 * its application context, so the deployment restart-looped until the provider was back. Issuer
 * discovery now happens on first use instead, which keeps the application up and self-healing.
 *
 * <p>Every test starts with discovery answering 500; {@link
 * #shouldServeProtectedResourceMetadataOnceTheIssuerAnswers()} restores it to prove the deployment
 * recovers without a restart. The JWK set is never served, so a bearer token stays unverifiable
 * whatever order the tests run in.
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

  private static final String REALM = "camunda-test";
  private static final String DISCOVERY_ENDPOINT =
      "/realms/" + REALM + "/.well-known/openid-configuration";
  private static final String PROTECTED_RESOURCE_METADATA_ENDPOINT =
      "/.well-known/oauth-protected-resource";

  @RegisterExtension
  static WireMockExtension wireMock =
      WireMockExtension.newInstance()
          .configureStaticDsl(true)
          .options(wireMockConfig().notifier(new Slf4jNotifier(false)).dynamicPort())
          .build();

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

  @Test
  public void shouldRejectApiRequestsWhileTheIssuerIsUnreachable() {
    // when a bearer token is presented while the provider cannot be reached
    final var result =
        mockMvcTester
            .get()
            .uri(DUMMY_V2_API_ENDPOINT)
            .header("Authorization", "Bearer " + unverifiableJwt())
            .accept(MediaType.APPLICATION_JSON)
            .exchange();

    // then only that request fails: Spring Security reports a provider it cannot reach as a server
    // error rather than as a rejected credential, which is what an outage after startup produces
    // too
    assertThat(result).hasStatus5xxServerError();
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
  public void shouldServeProtectedResourceMetadataOnceTheIssuerAnswers() {
    // given the RFC 9728 metadata cannot be built while discovery fails: its authorization-server
    // list comes from the resolved client registrations
    assertThat(
            mockMvcTester
                .get()
                .uri(PROTECTED_RESOURCE_METADATA_ENDPOINT)
                .accept(MediaType.APPLICATION_JSON)
                .exchange())
        .hasFailed();

    // when the provider answers again
    stubFor(get(urlEqualTo(DISCOVERY_ENDPOINT)).willReturn(okJson(discoveryDocument())));

    // then the very next request is served — no restart needed
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

  private static String unverifiableJwt() {
    // base64url of {"alg":"RS256"} and {"sub":"test"} with a bogus signature: shaped like a JWT so
    // the decoder gets as far as needing the provider, but not a credential anywhere
    final var encoder = Base64.getUrlEncoder().withoutPadding();
    return encoder.encodeToString("{\"alg\":\"RS256\"}".getBytes(StandardCharsets.UTF_8))
        + "."
        + encoder.encodeToString("{\"sub\":\"test\"}".getBytes(StandardCharsets.UTF_8))
        + ".not-a-signature";
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
