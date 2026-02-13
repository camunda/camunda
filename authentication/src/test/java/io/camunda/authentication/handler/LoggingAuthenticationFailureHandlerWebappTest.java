/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.handler;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.github.tomakehurst.wiremock.common.Slf4jNotifier;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import io.camunda.authentication.config.WebSecurityConfig;
import io.camunda.authentication.config.controllers.OidcFlowTestContext;
import io.camunda.security.configuration.OidcAuthenticationConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.LoggerFactory;
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
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * Tests that the {@link LoggingAuthenticationFailureHandler} correctly handles JWT decoding
 * failures in the webapp security filter chain (protected by {@code
 * ConditionalOnSecondaryStorageEnabled}).
 *
 * <p>This test uses Logback's {@code ListAppender} to programmatically capture log events and
 * verify that transient JWKS endpoint failures are logged at WARN level (not ERROR), preventing
 * unnecessary alerts in production monitoring systems.
 *
 * @see <a href="https://github.com/camunda/camunda/issues/35925">GitHub Issue #35925</a>
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
      "camunda.security.authentication.unprotected-api=false",
      "camunda.security.authentication.method=oidc",
      "camunda.security.authentication.oidc.client-id="
          + LoggingAuthenticationFailureHandlerWebappTest.CLIENT_ID,
      "camunda.security.authentication.oidc.redirect-uri=http://localhost/sso-callback",
      "camunda.security.authentication.oidc.clientAuthenticationMethod="
          + OidcAuthenticationConfiguration.CLIENT_AUTHENTICATION_METHOD_PRIVATE_KEY_JWT,
      "camunda.security.authentication.oidc.assertion.keystore.path= ${user.dir}/src/test/resources/keystore.p12",
      "camunda.security.authentication.oidc.assertion.keystore.password=password",
      "camunda.security.authentication.oidc.assertion.keystore.keyAlias=camunda-standalone",
      "camunda.security.authentication.oidc.assertion.keystore.keyPassword=password",
      "camunda.security.authentication.oidc.resource=https://api.example.com/app1/, https://api.example.com/app2/",
      // Enable secondary storage to activate the webapp security filter chain
      "camunda.database.type=rdbms",
      "logging.level.io.camunda.authentication.config=DEBUG",
      "logging.level.org.springframework.security=TRACE",
    })
@ActiveProfiles("consolidated-auth")
class LoggingAuthenticationFailureHandlerWebappTest {

  @RegisterExtension
  static WireMockExtension wireMock =
      WireMockExtension.newInstance()
          .configureStaticDsl(true)
          .options(wireMockConfig().notifier(new Slf4jNotifier(false)).dynamicPort())
          .failOnUnmatchedRequests(true)
          .build();

  static final String CLIENT_ID = "camunda-webapp-client";
  static final String REALM = "camunda-test-webapp";
  static final String ENDPOINT_WELL_KNOWN_JWKS = "/realms/" + REALM + "/.well-known/jwks.json";
  static final String ENDPOINT_WELL_KNOWN_OIDC =
      "/realms/" + REALM + "/.well-known/openid-configuration";
  static final String WEBAPP_ENDPOINT = "/identity/users";

  @Autowired MockMvcTester mockMvcTester;

  private ListAppender<ILoggingEvent> logAppender;
  private Logger handlerLogger;

  @BeforeEach
  void setupLogCapture() {
    // Attach a ListAppender to the handler's logger to capture log events
    handlerLogger = (Logger) LoggerFactory.getLogger(LoggingAuthenticationFailureHandler.class);
    logAppender = new ListAppender<>();
    logAppender.start();
    handlerLogger.addAppender(logAppender);
  }

  @AfterEach
  void cleanupLogCapture() {
    // Detach and stop the appender
    if (handlerLogger != null && logAppender != null) {
      handlerLogger.detachAppender(logAppender);
      logAppender.stop();
    }
  }

  @BeforeEach
  void stubJwksEndpointFailure() {
    // Simulate JWKS endpoint returning HTTP 500 error
    stubFor(
        get(urlEqualTo(ENDPOINT_WELL_KNOWN_JWKS))
            .willReturn(aResponse().withStatus(HttpStatus.INTERNAL_SERVER_ERROR.value())));
  }

  /**
   * Tests that JWT decoding failures in the webapp security filter chain are logged at WARN level,
   * not ERROR level.
   *
   * <p>This test simulates a scenario where the JWKS endpoint is unreachable (returns 500 error),
   * causing JWT validation to fail. The expected behavior is:
   *
   * <ol>
   *   <li>The request is rejected with HTTP 401 Unauthorized
   *   <li>The {@code LoggingAuthenticationFailureHandler} logs at WARN level
   *   <li>No ERROR-level log entries are produced by the handler
   * </ol>
   *
   * <p>Without the fix, Spring Security's default behavior would log this at ERROR level, causing
   * unnecessary alerts in production monitoring systems like Google Cloud Error Reporting.
   */
  @Test
  void shouldLogWebappJwtDecodingFailureAtWarnLevel() {
    // Given: a random access token that will fail to decode due to JWKS fetch failure
    final String accessToken = accessToken();

    // When: accessing a webapp endpoint with the bearer token
    final MvcTestResult result =
        mockMvcTester
            .get()
            .uri(WEBAPP_ENDPOINT)
            .accept(MediaType.APPLICATION_JSON)
            .header("Authorization", "Bearer " + accessToken)
            .exchange();

    // Then: the request should fail with 500 (internal server error from handler)
    assertThat(result).hasStatus(HttpStatus.INTERNAL_SERVER_ERROR);

    // And: the handler should have logged at WARN level (not ERROR)
    final var logEvents = logAppender.list;
    assertThat(logEvents)
        .as("Handler should log at WARN level for authentication failures")
        .isNotEmpty()
        .anySatisfy(
            event -> {
              assertThat(event.getLevel()).isEqualTo(Level.WARN);
              assertThat(event.getLoggerName())
                  .isEqualTo(LoggingAuthenticationFailureHandler.class.getName());
            });

    // And: NO log events should be at ERROR level
    assertThat(logEvents)
        .as("Handler should NOT log at ERROR level")
        .noneSatisfy(event -> assertThat(event.getLevel()).isEqualTo(Level.ERROR));
  }

  /**
   * Tests that JWKS endpoint failures are logged at WARN level.
   *
   * <p>This test validates that the {@code
   * withObjectPostProcessor(postProcessBearerTokenFailureHandler())} configuration is correctly
   * applied to the {@code oauth2ResourceServer} in the {@code oidcWebappSecurity} bean.
   */
  @Test
  void shouldLogJwksEndpointFailureAtWarnLevel() {
    // Given: a JWT token
    final String accessToken = accessToken();

    // When: the JWKS endpoint returns a 500 error
    final MvcTestResult result =
        mockMvcTester
            .get()
            .uri(WEBAPP_ENDPOINT)
            .accept(MediaType.APPLICATION_JSON)
            .header("Authorization", "Bearer " + accessToken)
            .exchange();

    // Then: authentication should fail gracefully with 500
    assertThat(result).hasStatus(HttpStatus.INTERNAL_SERVER_ERROR);

    // And: the handler should have logged authentication failures at WARN level
    final var logEvents = logAppender.list;
    assertThat(logEvents)
        .as("Handler should log JWKS fetch failures at WARN level")
        .isNotEmpty()
        .anySatisfy(
            event -> {
              assertThat(event.getLevel()).isEqualTo(Level.WARN);
              assertThat(event.getFormattedMessage())
                  .contains("A technical authentication problem occurred");
            });

    // And: NO authentication-related errors should be logged at ERROR level
    assertThat(logEvents)
        .as("No ERROR-level logs should be produced")
        .noneSatisfy(event -> assertThat(event.getLevel()).isEqualTo(Level.ERROR));
  }

  @DynamicPropertySource
  static void registerWireMockProperties(final DynamicPropertyRegistry registry) {
    registry.add(
        "camunda.security.authentication.oidc.issuer-uri",
        () -> "http://localhost:" + wireMock.getPort() + "/realms/" + REALM);
  }

  @BeforeAll
  static void stubIdpEndpoints() {
    stubFor(
        get(urlEqualTo(ENDPOINT_WELL_KNOWN_OIDC))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody(wellKnownResponse())));
  }

  private static String wellKnownResponse() {
    return """
            {
                "issuer": "http://localhost:000000/realms/KEYCLOAKREALM",
                "authorization_endpoint": "http://localhost:000000/realms/KEYCLOAKREALM/oauth/authorize",
                "token_endpoint": "http://localhost:000000/realms/KEYCLOAKREALM/oauth/token",
                "userinfo_endpoint": "http://localhost:000000/realms/KEYCLOAKREALM/userinfo",
                "jwks_uri": "http://localhost:000000/realms/KEYCLOAKREALM/.well-known/jwks.json",
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
        .replaceAll("000000", String.valueOf(wireMock.getPort()))
        .replaceAll("KEYCLOAKREALM", REALM);
  }

  /**
   * Returns a sample JWT access token for testing. This token is intentionally malformed or will
   * fail validation due to the JWKS endpoint being unavailable.
   */
  private static String accessToken() {
    return "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkNhbXVuZGEgV2ViYXBwIFVzZXIiLCJhZG1pbiI6dHJ1ZSwiaWF0IjoxNzU2OTQwMjk5fQ.Tih2EbvzaCkREp4yNaP7Y_opD1YyO3bE95te66bVpTXtOOl9jL8Ovv2nCNm44HMpQPZVyVlR3bDd3tcWf6mRolkeFMrhe2mkz0xr_-WptpgkigrIKVhWZzjz6YEawJpYFNH6pwn74WVPlnruyzeqnItdcbM-0dQ9gsIOPPgubajKnTs1qA6NwkOWU9AI6Y5aQcflRpPewkPMHKL-KIe0lIGfLUaQhUZEAbQC7u6Pujx8l2cuom-xpWAeYHCoNfKbsssUWHY3DZ4yZGk6vQe_dLt5iYxlVcp-SSOnOKNd6EU_Rf4cD0zNWTX44YfwYM5ZYBBJd9QiwSj_xmzLLbBFmA";
  }
}
