/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.common.Slf4jNotifier;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import io.camunda.authentication.config.WebSecurityConfig;
import io.camunda.authentication.config.controllers.OidcFlowTestContext;
import io.camunda.security.configuration.OidcAuthenticationConfiguration;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.web.util.UriComponentsBuilder;

@SuppressWarnings({"SpringBootApplicationProperties", "WrongPropertyKeyValueDelimiter"})
@AutoConfigureMockMvc
@AutoConfigureWebMvc
@SpringBootTest(
    classes = {
      OidcFlowTestContext.class,
      WebSecurityConfig.class,
    },
    properties = {
      "camunda.security.authentication.unprotected-api=false",
      "camunda.security.authentication.method=oidc",
      "camunda.security.authentication.oidc.client-id=" + OidcPrivateKeyJwtFlowTest.CLIENT_ID,
      "camunda.security.authentication.oidc.redirect-uri=http://localhost/sso-callback",
      "camunda.security.authentication.oidc.clientAuthenticationMethod="
          + OidcAuthenticationConfiguration.CLIENT_AUTHENTICATION_METHOD_PRIVATE_KEY_JWT,
      "camunda.security.authentication.oidc.assertionKeystore.path= ${user.dir}/src/test/resources/keystore.p12",
      "camunda.security.authentication.oidc.assertionKeystore.password=password",
      "camunda.security.authentication.oidc.assertionKeystore.keyAlias=camunda-standalone",
      "camunda.security.authentication.oidc.assertionKeystore.keyPassword=password",
      // essential for debugging the flow
      //      "logging.level.org.springframework.security=TRACE",
    })
@ActiveProfiles("consolidated-auth")
class OidcPrivateKeyJwtFlowTest {

  @RegisterExtension
  static WireMockExtension wireMock =
      WireMockExtension.newInstance()
          .configureStaticDsl(true)
          .options(wireMockConfig().notifier(new Slf4jNotifier(false)).dynamicPort())
          .failOnUnmatchedRequests(true)
          .build();

  static final String CLIENT_ID = "camunda-test";

  @Autowired MockMvcTester mockMvcTester;

  @DynamicPropertySource
  static void registerWireMockProperties(final DynamicPropertyRegistry registry) {
    registry.add(
        "camunda.security.authentication.oidc.issuer-uri",
        () -> "http://localhost:" + wireMock.getPort() + "/realms/camunda-test");
  }

  @BeforeAll
  static void stubWellKnownForStartup() {
    stubFor(
        get(urlEqualTo("/realms/camunda-test/.well-known/openid-configuration"))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody(wellKnownResponse())));
  }

  @Test
  public void correctConfigurationShouldEnableStartupAndRedirection() {
    final MvcTestResult result =
        mockMvcTester.get().uri("/").accept(MediaType.TEXT_HTML).exchange();
    assertThat(result)
        .hasStatus(HttpStatus.FOUND)
        .hasHeader("Location", "http://localhost/oauth2/authorization/oidc");
  }

  @Test
  public void tokenEndpointShouldReceiveJwt() {
    // having an IdP token endpoint
    stubFor(
        post(urlEqualTo("/realms/camunda-test/oauth/token"))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody(tokenResponse())));

    // and a dummy jwks well-known edpont to prevent Spring from breaking
    stubFor(
        get(urlEqualTo("/realms/camunda-test/.well-known/jwks.json"))
            .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody("{}")));

    // with an established session, we notify Spring Security we want to authenticate
    // which builds and saves an authorizationRequest
    final MockHttpSession session = new MockHttpSession();
    final var redirectResult =
        mockMvcTester
            .get()
            .uri("/oauth2/authorization/oidc")
            .accept(MediaType.TEXT_HTML)
            .session(session)
            .exchange();
    assertThat(redirectResult).hasStatus3xxRedirection();
    final String redirectUrl = redirectResult.getResponse().getHeader("Location");
    assertThat(redirectUrl).isNotNull();
    final var queryParams =
        UriComponentsBuilder.fromUriString(redirectUrl).build().getQueryParams();
    // and we get a state reference that can be matched upon the redirect from the IdP
    assertThat(queryParams).containsKey("state");

    // when inducing an auth code for token exchange
    final var state =
        URLDecoder.decode(
            Objects.requireNonNull(queryParams.getFirst("state")), StandardCharsets.UTF_8);
    mockMvcTester
        .get()
        .uri("/sso-callback")
        .accept(MediaType.TEXT_HTML)
        .session(session)
        .queryParam("code", "test_authorization_code")
        .queryParam("state", state)
        .queryParam("session_state", "test_session_state")
        .queryParam("iss", "http://localhost:" + wireMock.getPort() + "/realms/camunda-test")
        .exchange();

    // then the IdP receives the authorization code
    // with the Spring Security client authenticating using private_key_jwt
    // in the form of a client id and client assertion
    verify(
        1,
        postRequestedFor(urlEqualTo("/realms/camunda-test/oauth/token"))
            .withHeader("Content-Type", containing("application/x-www-form-urlencoded"))
            .withRequestBody(matching(".*grant_type=authorization_code.*"))
            .withRequestBody(matching(".*code=test_authorization_code.*"))
            .withRequestBody(matching(".*client_id=" + CLIENT_ID + ".*"))
            .withRequestBody(
                matching(
                    ".*client_assertion_type=urn%3Aietf%3Aparams%3Aoauth%3Aclient-assertion-type%3Ajwt-bearer.*"))
            // client_assertion=<A JWT>
            .withRequestBody(
                matching(
                    ".*client_assertion=[A-Za-z0-9-_=]+\\.[A-Za-z0-9-_=]+\\.?[A-Za-z0-9-_.+/=]*.*")));
  }

  private static String wellKnownResponse() {
    return """
            {
                "issuer": "http://localhost:000000/realms/camunda-test",
                "authorization_endpoint": "http://localhost:000000/realms/camunda-test/oauth/authorize",
                "token_endpoint": "http://localhost:000000/realms/camunda-test/oauth/token",
                "userinfo_endpoint": "http://localhost:000000/realms/camunda-test/userinfo",
                "jwks_uri": "http://localhost:000000/realms/camunda-test/.well-known/jwks.json",
                "response_types_supported": [
                    "code",
                    "token",
                    "id_token",
                    "code token",
                    "code id_token",
                    "token id_token",
                    "code token id_token",
                    "none"
                ],
                "subject_types_supported": [
                    "public"
                ],
                "id_token_signing_alg_values_supported": [
                    "RS256"
                ],
                "scopes_supported": [
                    "openid",
                    "email",
                    "profile"
                ]
            }
        """
        .replaceAll("000000", String.valueOf(wireMock.getPort()));
  }

  private static String tokenResponse() {
    return """
            {
                "access_token": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkNhbXVuZG8gQ2FtdW5kb3Zza2kiLCJhZG1pbiI6dHJ1ZSwiaWF0IjoxNzU2OTQwMjk5fQ.Tih2EbvzaCkREp4yNaP7Y_opD1YyO3bE95te66bVpTXtOOl9jL8Ovv2nCNm44HMpQPZVyVlR3bDd3tcWf6mRolkeFMrhe2mkz0xr_-WptpgkigrIKVhWZzjz6YEawJpYFNH6pwn74WVPlnruyzeqnItdcbM-0dQ9gsIOPPgubajKnTs1qA6NwkOWU9AI6Y5aQcflRpPewkPMHKL-KIe0lIGfLUaQhUZEAbQC7u6Pujx8l2cuom-xpWAeYHCoNfKbsssUWHY3DZ4yZGk6vQe_dLt5iYxlVcp-SSOnOKNd6EU_Rf4cD0zNWTX44YfwYM5ZYBBJd9QiwSj_xmzLLbBFmA",
                "expires_in": 3600,
                "token_type": "Bearer",
                "id_token": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkNhbXVuZG8gQ2FtdW5kb3Zza2kiLCJhZG1pbiI6dHJ1ZSwiaWF0IjoxNzU2OTQwMjk5fQ.Tih2EbvzaCkREp4yNaP7Y_opD1YyO3bE95te66bVpTXtOOl9jL8Ovv2nCNm44HMpQPZVyVlR3bDd3tcWf6mRolkeFMrhe2mkz0xr_-WptpgkigrIKVhWZzjz6YEawJpYFNH6pwn74WVPlnruyzeqnItdcbM-0dQ9gsIOPPgubajKnTs1qA6NwkOWU9AI6Y5aQcflRpPewkPMHKL-KIe0lIGfLUaQhUZEAbQC7u6Pujx8l2cuom-xpWAeYHCoNfKbsssUWHY3DZ4yZGk6vQe_dLt5iYxlVcp-SSOnOKNd6EU_Rf4cD0zNWTX44YfwYM5ZYBBJd9QiwSj_xmzLLbBFmA"
            }
        """;
  }
}
