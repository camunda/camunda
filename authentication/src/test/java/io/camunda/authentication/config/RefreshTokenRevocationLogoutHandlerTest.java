/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import io.camunda.security.configuration.AssertionConfiguration;
import io.camunda.security.configuration.KeystoreConfiguration;
import io.camunda.security.configuration.OidcAuthenticationConfiguration;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.logging.LogLevel;
import org.springframework.boot.logging.LoggingSystem;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.RequestMatcher;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

/**
 * Unit tests for {@link RefreshTokenRevocationLogoutHandler}.
 *
 * <p>Two properties matter beyond "a request was sent". First, the request has to authenticate the
 * way the same client authenticates at the token endpoint, or the provider rejects it and the
 * refresh token silently survives the logout. Second, nothing the provider does may surface to the
 * user, whose local session is already gone by the time this handler runs.
 */
@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class RefreshTokenRevocationLogoutHandlerTest {

  private static final String REGISTRATION_ID = "oidc";
  private static final String CLIENT_ID = "client-id";
  private static final String CLIENT_SECRET = "client-secret";
  private static final String REVOCATION_ENDPOINT = "https://idp.example.com/oauth2/revoke";
  private static final String REFRESH_TOKEN_VALUE = "the-refresh-token";

  private static final String HANDLER_LOGGER = RefreshTokenRevocationLogoutHandler.class.getName();

  /**
   * The logger {@code DefaultRestClient} writes its request-body DEBUG line to. Named by string:
   * the class is package-private in {@code org.springframework.web.client}.
   */
  private static final String REST_CLIENT_LOGGER = "org.springframework.web.client";

  private static final String JWT_BEARER_ASSERTION_TYPE =
      "urn:ietf:params:oauth:client-assertion-type:jwt-bearer";

  @Mock private ClientRegistrationRepository clientRegistrationRepository;
  @Mock private OAuth2AuthorizedClientRepository authorizedClientRepository;

  private MockHttpServletRequest request;
  private MockHttpServletResponse response;
  private RestClient.Builder restClientBuilder;
  private MockRestServiceServer server;

  @BeforeEach
  void setUp() {
    request = new MockHttpServletRequest();
    response = new MockHttpServletResponse();
    restClientBuilder = RestClient.builder();
    server = MockRestServiceServer.bindTo(restClientBuilder).bufferContent().build();
  }

  @AfterEach
  void resetLogLevels() {
    setLogLevel(HANDLER_LOGGER, null);
    setLogLevel(REST_CLIENT_LOGGER, null);
  }

  @Test
  void shouldRevokeRefreshTokenWithBasicClientAuthentication() {
    // given a client_secret_basic registration publishing a revocation endpoint
    final var registration = registration(ClientAuthenticationMethod.CLIENT_SECRET_BASIC).build();
    givenAuthorizedSession(registration);
    server
        .expect(requestTo(REVOCATION_ENDPOINT))
        .andExpect(post())
        .andExpect(
            headers(
                headers ->
                    assertThat(headers.getFirst(HttpHeaders.AUTHORIZATION))
                        .isEqualTo(basicAuth(CLIENT_ID, CLIENT_SECRET))))
        .andExpect(
            formBody(
                fields ->
                    assertThat(fields)
                        .containsEntry("token", REFRESH_TOKEN_VALUE)
                        .containsEntry("token_type_hint", "refresh_token")
                        // For this method the credentials belong in the header only; duplicating
                        // them into the body is what RFC 6749 §2.3.1 tells servers to reject.
                        .doesNotContainKeys("client_id", "client_secret")))
        .andRespond(withSuccess());

    // when the user logs out
    handler(null).logout(request, response, authentication(registration));

    // then the refresh token was revoked at the provider
    server.verify();
  }

  @Test
  void shouldRevokeRefreshTokenWithPostClientAuthentication() {
    // given a client_secret_post registration
    final var registration = registration(ClientAuthenticationMethod.CLIENT_SECRET_POST).build();
    givenAuthorizedSession(registration);
    server
        .expect(requestTo(REVOCATION_ENDPOINT))
        .andExpect(
            headers(
                headers ->
                    assertThat(headers.headerNames()).doesNotContain(HttpHeaders.AUTHORIZATION)))
        .andExpect(
            formBody(
                fields ->
                    assertThat(fields)
                        .containsEntry("client_id", CLIENT_ID)
                        .containsEntry("client_secret", CLIENT_SECRET)
                        .containsEntry("token", REFRESH_TOKEN_VALUE)))
        .andRespond(withSuccess());

    // when the user logs out
    handler(null).logout(request, response, authentication(registration));

    // then the credentials travelled in the body instead of the header
    server.verify();
  }

  @Test
  void shouldRevokeRefreshTokenWithPrivateKeyJwtClientAuthentication() throws Exception {
    // given a private_key_jwt registration and a resolvable signing key
    final var registration = registration(ClientAuthenticationMethod.PRIVATE_KEY_JWT).build();
    givenAuthorizedSession(registration);
    server
        .expect(requestTo(REVOCATION_ENDPOINT))
        .andExpect(
            formBody(
                fields ->
                    assertThat(fields)
                        .containsEntry("client_id", CLIENT_ID)
                        .containsEntry("client_assertion_type", JWT_BEARER_ASSERTION_TYPE)
                        .containsEntry("token", REFRESH_TOKEN_VALUE)
                        .doesNotContainKey("client_secret")
                        .hasEntrySatisfying(
                            "client_assertion", assertion -> assertThat(assertion).isNotBlank())))
        .andRespond(withSuccess());

    // when the user logs out
    handler(jwkResolver(rsaJwk())).logout(request, response, authentication(registration));

    // then a signed client assertion authenticated the request
    server.verify();
  }

  /**
   * Asserting only that {@code client_assertion} is non-blank would pass for a JWT signed with the
   * wrong key, the wrong algorithm, or addressed to the wrong audience, all of which a provider
   * rejects and fail-soft hides.
   *
   * <p>The audience is the revocation endpoint, not the token endpoint, and the two have to differ
   * in this test for that to mean anything: {@code
   * NimbusJwtClientAuthenticationParametersConverter} defaults {@code aud} to the token endpoint,
   * so a regression that dropped the override would still pass a test where the two endpoints
   * happened to be equal. Okta checks the audience against the endpoint actually invoked and
   * rejects an assertion addressed to the wrong one.
   */
  @Test
  void shouldAddressTheClientAssertionToTheRevocationEndpointNotTheTokenEndpoint()
      throws Exception {
    // given a private_key_jwt registration and a known signing key, with a revocation endpoint
    // distinct from the token endpoint
    final var jwk = rsaJwk();
    final var registration = registration(ClientAuthenticationMethod.PRIVATE_KEY_JWT).build();
    assertThat(registration.getProviderDetails().getTokenUri()).isNotEqualTo(REVOCATION_ENDPOINT);
    givenAuthorizedSession(registration);
    final var assertion = new AtomicReference<String>();
    server
        .expect(requestTo(REVOCATION_ENDPOINT))
        .andExpect(formBody(fields -> assertion.set(fields.get("client_assertion"))))
        .andRespond(withSuccess());

    // when the user logs out
    handler(jwkResolver(jwk)).logout(request, response, authentication(registration));

    // then the assertion is addressed to the endpoint the request is actually sent to
    server.verify();
    final var jwt = SignedJWT.parse(assertion.get());
    assertThat(jwt.getHeader().getAlgorithm()).isEqualTo(JWSAlgorithm.RS256);
    assertThat(jwt.getHeader().getKeyID()).isEqualTo(jwk.getKeyID());
    final var claims = jwt.getJWTClaimsSet();
    assertThat(claims.getIssuer()).isEqualTo(CLIENT_ID);
    assertThat(claims.getSubject()).isEqualTo(CLIENT_ID);
    assertThat(claims.getAudience()).containsExactly(REVOCATION_ENDPOINT);
    assertThat(claims.getJWTID()).isNotBlank();
    assertThat(claims.getExpirationTime()).isAfter(new Date());
    assertThat(jwt.verify(new RSASSAVerifier(jwk.toRSAKey())))
        .as("assertion must verify against the configured key")
        .isTrue();
  }

  @Test
  void shouldSkipRevocationWhenProviderPublishesNoRevocationEndpoint() {
    // given a provider whose metadata carries no revocation endpoint, as MS Entra's does not
    final var registration =
        registration(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
            .providerConfigurationMetadata(Map.of())
            .build();
    when(clientRegistrationRepository.findByRegistrationId(REGISTRATION_ID))
        .thenReturn(registration);

    // when the user logs out
    handler(null).logout(request, response, authentication(registration));

    // then no request is attempted at all
    server.verify();
  }

  @Test
  void shouldSkipRevocationWhenSessionHasNoRefreshToken() {
    // given a session whose authorized client never received a refresh token
    final var registration = registration(ClientAuthenticationMethod.CLIENT_SECRET_BASIC).build();
    when(clientRegistrationRepository.findByRegistrationId(REGISTRATION_ID))
        .thenReturn(registration);
    when(authorizedClientRepository.loadAuthorizedClient(
            eq(REGISTRATION_ID), any(Authentication.class), any()))
        .thenReturn(new OAuth2AuthorizedClient(registration, "principal", accessToken(), null));

    // when the user logs out
    handler(null).logout(request, response, authentication(registration));

    // then there is nothing to revoke and no request is attempted
    server.verify();
  }

  @Test
  void shouldSkipRevocationWhenAuthenticationIsNotOidc() {
    // given a non-OAuth2 authentication, as the basic-auth chain produces
    final Authentication authentication =
        UsernamePasswordAuthenticationToken.authenticated("demo", "demo", List.of());

    // when the user logs out
    handler(null).logout(request, response, authentication);

    // then no request is attempted
    server.verify();
  }

  @Test
  void shouldSkipRevocationWhenRegistrationIsUnknown() {
    // given an authentication naming a registration the repository does not hold
    final var registration = registration(ClientAuthenticationMethod.CLIENT_SECRET_BASIC).build();
    when(clientRegistrationRepository.findByRegistrationId(REGISTRATION_ID)).thenReturn(null);

    // when the user logs out
    handler(null).logout(request, response, authentication(registration));

    // then no request is attempted
    server.verify();
  }

  @Test
  void shouldSkipRevocationWhenClientAuthenticatesWithClientSecretJwt() {
    // given a registration on client_secret_jwt, which this application cannot sign an assertion
    // for: its permitted methods are client_secret_basic and private_key_jwt, so there is no
    // octet key
    final var registration = registration(ClientAuthenticationMethod.CLIENT_SECRET_JWT).build();
    // A full session, so the only thing standing between this and a request is the guard itself.
    givenAuthorizedSession(registration);

    // when the user logs out
    handler(null).logout(request, response, authentication(registration));

    // then nothing is sent, rather than an assertion the provider would reject
    server.verify();
  }

  @Test
  void shouldSkipRevocationWhenTheAssertionSigningKeyIsUnavailable() {
    // given a private_key_jwt registration but no resolver to sign an assertion with
    final var registration = registration(ClientAuthenticationMethod.PRIVATE_KEY_JWT).build();
    givenAuthorizedSession(registration);
    final var logoutHandler = handler(null);
    final var authentication = authentication(registration);

    // when the user logs out
    assertThatCode(() -> logoutHandler.logout(request, response, authentication))
        .doesNotThrowAnyException();

    // then nothing is sent. Sending it would put a live refresh token on the wire in a request
    // already known to be unauthorized, which is what the client_secret_jwt branch avoids.
    server.verify();
  }

  /**
   * The class promises the token value is never logged. {@code DefaultRestClient#logBody} appends
   * the body object to a DEBUG line before handing it to a message converter, so passing the form
   * as a {@code MultiValueMap} to {@code body(Object)} would put the refresh token and the client
   * secret in that line. This asserts on Spring's own logger, not on our wire format, because the
   * wire format is identical either way; it is the log that differs.
   */
  @Test
  void shouldNeverLogTheRefreshTokenOrClientSecretWhileSendingTheRequest(
      final CapturedOutput output) {
    // given Spring's REST client logging turned up to DEBUG
    setLogLevel(REST_CLIENT_LOGGER, LogLevel.DEBUG);
    final var registration = registration(ClientAuthenticationMethod.CLIENT_SECRET_POST).build();
    givenAuthorizedSession(registration);
    server
        .expect(requestTo(REVOCATION_ENDPOINT))
        .andExpect(
            headers(
                headers ->
                    assertThat(headers.getContentType())
                        .isEqualTo(MediaType.APPLICATION_FORM_URLENCODED)))
        .andExpect(
            formBody(
                fields ->
                    assertThat(fields)
                        .containsEntry("token", REFRESH_TOKEN_VALUE)
                        .containsEntry("client_secret", CLIENT_SECRET)))
        .andRespond(withSuccess());

    // when the user logs out
    handler(null).logout(request, response, authentication(registration));

    // then the request went out, and neither secret reached the log
    server.verify();
    // No "not empty" guard: the fix produces no body log line at all. What keeps this test from
    // passing vacuously is springLogsTheFormBodyWhenItIsPassedToBodyObject below.
    assertThat(output.getAll()).doesNotContain(REFRESH_TOKEN_VALUE).doesNotContain(CLIENT_SECRET);
  }

  /**
   * Pins the hazard the test above avoids, so that one cannot pass vacuously: a wrong logger name,
   * or a future Spring that stops logging request bodies, fails here instead of silently turning
   * the negative assertion into a tautology.
   */
  @Test
  void springLogsTheFormBodyWhenItIsPassedToBodyObject(final CapturedOutput output) {
    // given the same form the handler builds, handed to body(Object) as it was before the fix,
    // with Spring's REST client logging at DEBUG
    setLogLevel(REST_CLIENT_LOGGER, LogLevel.DEBUG);
    final MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
    form.set("token", REFRESH_TOKEN_VALUE);
    server.expect(requestTo(REVOCATION_ENDPOINT)).andRespond(withSuccess());

    // when the request is sent through the message-converter path
    restClientBuilder
        .build()
        .post()
        .uri(REVOCATION_ENDPOINT)
        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
        .body(form)
        .retrieve()
        .onStatus(anyStatus -> true, (req, res) -> {})
        .toBodilessEntity();

    // then the token lands in the log, which is exactly why the handler streams instead
    assertThat(output.getAll()).contains(REFRESH_TOKEN_VALUE);
  }

  @Test
  void shouldFormUrlEncodeReservedCharactersInTheBody() {
    // given a refresh token containing characters that must be escaped in a form body
    final var registration = registration(ClientAuthenticationMethod.CLIENT_SECRET_BASIC).build();
    final var awkwardToken = "a+b&c=d e/f";
    when(clientRegistrationRepository.findByRegistrationId(REGISTRATION_ID))
        .thenReturn(registration);
    when(authorizedClientRepository.loadAuthorizedClient(
            eq(REGISTRATION_ID), any(Authentication.class), any()))
        .thenReturn(
            new OAuth2AuthorizedClient(
                registration,
                "principal",
                accessToken(),
                new OAuth2RefreshToken(awkwardToken, Instant.now())));
    server
        .expect(requestTo(REVOCATION_ENDPOINT))
        .andExpect(formBody(fields -> assertThat(fields).containsEntry("token", awkwardToken)))
        .andRespond(withSuccess());

    // when the user logs out
    handler(null).logout(request, response, authentication(registration));

    // then the provider receives the token verbatim after decoding
    server.verify();
  }

  @Test
  void shouldCompleteLogoutWhenProviderRejectsRevocation() {
    // given a provider that answers the revocation request with 400
    final var registration = registration(ClientAuthenticationMethod.CLIENT_SECRET_BASIC).build();
    givenAuthorizedSession(registration);
    server.expect(requestTo(REVOCATION_ENDPOINT)).andRespond(withBadRequest());
    final var logoutHandler = handler(null);
    final var authentication = authentication(registration);

    // when the user logs out
    // then the rejection is swallowed: the local session is already gone either way
    assertThatCode(() -> logoutHandler.logout(request, response, authentication))
        .doesNotThrowAnyException();
    server.verify();
  }

  /**
   * Regression for the non-2xx check: a 3xx must take the failure path. {@code isError()} is
   * 4xx/5xx only, so a redirect would have been logged as a successful revocation. The client is
   * configured not to follow redirects, so nothing was revoked. The assertion is on the handler's
   * own log because the status is the only observable difference: logout completes either way.
   */
  @Test
  void shouldTreatARedirectFromTheRevocationEndpointAsAFailure(final CapturedOutput output) {
    // given a revocation endpoint answering with a redirect
    setLogLevel(HANDLER_LOGGER, LogLevel.DEBUG);
    final var registration = registration(ClientAuthenticationMethod.CLIENT_SECRET_BASIC).build();
    givenAuthorizedSession(registration);
    server.expect(requestTo(REVOCATION_ENDPOINT)).andRespond(withStatus(HttpStatus.FOUND));
    final var logoutHandler = handler(null);
    final var authentication = authentication(registration);

    // when the user logs out
    assertThatCode(() -> logoutHandler.logout(request, response, authentication))
        .doesNotThrowAnyException();

    // then the request was made and reported as unconfirmed, not as a revocation
    server.verify();
    assertThat(output.getAll())
        .contains("did not confirm the refresh-token revocation")
        .doesNotContain("Revoked the refresh token");
  }

  @Test
  void shouldCompleteLogoutWhenProviderIsUnreachable() {
    // given a provider whose revocation endpoint cannot be reached
    final var registration = registration(ClientAuthenticationMethod.CLIENT_SECRET_BASIC).build();
    givenAuthorizedSession(registration);
    server
        .expect(requestTo(REVOCATION_ENDPOINT))
        .andRespond(
            req -> {
              throw new SocketTimeoutException("connect timed out");
            });
    final var logoutHandler = handler(null);
    final var authentication = authentication(registration);

    // when the user logs out
    // then logout still completes
    assertThatCode(() -> logoutHandler.logout(request, response, authentication))
        .doesNotThrowAnyException();
    // and the request really was attempted: without this the test also passes when an earlier
    // guard returns and no revocation is tried, which is not the path under test.
    server.verify();
  }

  /**
   * A failure while signing the assertion, a missing or unreadable keystore typically, must stay
   * attributable to one provider, and must not carry the exception's own message into the log.
   */
  @Test
  void shouldNameTheRegistrationWhenTheAssertionCannotBeSigned(final CapturedOutput output) {
    // given a private_key_jwt registration whose signing key cannot be resolved
    setLogLevel(HANDLER_LOGGER, LogLevel.DEBUG);
    final var registration = registration(ClientAuthenticationMethod.PRIVATE_KEY_JWT).build();
    givenAuthorizedSession(registration);
    final Function<ClientRegistration, JWK> failing =
        reg -> {
          throw new IllegalStateException("keystore /etc/secret.p12 is unreadable");
        };

    // when the user logs out
    handler(failing).logout(request, response, authentication(registration));

    // then the warning names the registration and omits the exception's message
    assertThat(output.getAll())
        .contains("Failed to revoke the refresh token for OIDC registration '" + REGISTRATION_ID)
        .doesNotContain("/etc/secret.p12");
  }

  /**
   * A rejected revocation has to be distinguishable from a timeout or a TLS failure, or the
   * operator has nothing to act on. The RFC 7009 {@code error} code is the one field worth
   * surfacing from the body.
   */
  @Test
  void shouldReportTheProviderErrorCodeOnARejectedRevocation(final CapturedOutput output) {
    // given a provider rejecting the request the way an IdP does
    setLogLevel(HANDLER_LOGGER, LogLevel.DEBUG);
    final var registration = registration(ClientAuthenticationMethod.CLIENT_SECRET_BASIC).build();
    givenAuthorizedSession(registration);
    server
        .expect(requestTo(REVOCATION_ENDPOINT))
        .andRespond(
            withBadRequest()
                .body("{\"error\":\"invalid_client\",\"error_description\":\"token=abc\"}")
                .contentType(MediaType.APPLICATION_JSON));

    // when the user logs out
    handler(null).logout(request, response, authentication(registration));

    // then the code is named, and the free-text description, which an IdP may echo request
    // parameters into, is not
    assertThat(output.getAll()).contains("error=invalid_client").doesNotContain("token=abc");
  }

  @Test
  void shouldNotReportAnErrorFieldThatIsNotARecognisedCode(final CapturedOutput output) {
    // given a provider whose error field is free text rather than an RFC 7009 code
    setLogLevel(HANDLER_LOGGER, LogLevel.DEBUG);
    final var registration = registration(ClientAuthenticationMethod.CLIENT_SECRET_BASIC).build();
    givenAuthorizedSession(registration);
    server
        .expect(requestTo(REVOCATION_ENDPOINT))
        .andRespond(
            withBadRequest()
                .body("{\"error\":\"rejected: refresh_token=abc for user demo\"}")
                .contentType(MediaType.APPLICATION_JSON));

    // when the user logs out
    handler(null).logout(request, response, authentication(registration));

    // then nothing from the body is logged: the status alone is better than echoing it
    assertThat(output.getAll())
        .contains("did not confirm the refresh-token revocation")
        .doesNotContain("refresh_token=abc")
        .doesNotContain("error=");
  }

  /**
   * Regression for the credential-shaped echo. A shape check on {@code error} accepts any run of
   * letters, so an opaque secret echoed there would be written out verbatim. Only codes that
   * actually exist are repeated.
   */
  @Test
  void shouldNotReportAnErrorFieldThatLooksLikeACredential(final CapturedOutput output) {
    // given a provider echoing something secret-shaped into the error field
    setLogLevel(HANDLER_LOGGER, LogLevel.DEBUG);
    final var registration = registration(ClientAuthenticationMethod.CLIENT_SECRET_BASIC).build();
    givenAuthorizedSession(registration);
    server
        .expect(requestTo(REVOCATION_ENDPOINT))
        .andRespond(
            withBadRequest()
                .body("{\"error\":\"OpaqueRefreshTokenSecret\"}")
                .contentType(MediaType.APPLICATION_JSON));

    // when the user logs out
    handler(null).logout(request, response, authentication(registration));

    // then it is dropped: it is token-shaped, but it is not an error code
    assertThat(output.getAll())
        .contains("did not confirm the refresh-token revocation")
        .doesNotContain("OpaqueRefreshTokenSecret")
        .doesNotContain("error=");
  }

  @Test
  void shouldNameTheRootCauseOfATransportFailure(final CapturedOutput output) {
    // given an endpoint that times out rather than answering
    setLogLevel(HANDLER_LOGGER, LogLevel.DEBUG);
    final var registration = registration(ClientAuthenticationMethod.CLIENT_SECRET_BASIC).build();
    givenAuthorizedSession(registration);
    server
        .expect(requestTo(REVOCATION_ENDPOINT))
        .andRespond(
            req -> {
              throw new SocketTimeoutException("connect timed out");
            });

    // when the user logs out
    handler(null).logout(request, response, authentication(registration));

    // then the cause is named, so a timeout is not indistinguishable from a refused connection
    // or a TLS failure, all of which arrive as the same ResourceAccessException
    assertThat(output.getAll()).contains("SocketTimeoutException");
  }

  /**
   * The failure description repeats one message and no other: the application's own. The cause
   * chain of a failed keystore load is full of JDK messages that name a filesystem path, and an
   * operator-supplied path is as likely to be a secret-bearing value as anything else here.
   */
  @Test
  void shouldRepeatOnlyTheApplicationsOwnMessageFromAFailedKeystoreLoad(
      final CapturedOutput output) {
    // given a private_key_jwt registration whose keystore cannot be read
    setLogLevel(HANDLER_LOGGER, LogLevel.DEBUG);
    final var registration = registration(ClientAuthenticationMethod.PRIVATE_KEY_JWT).build();
    givenAuthorizedSession(registration);
    final var missingKeystore = "/does/not/exist/revocation-test.p12";
    final var jwkProvider = assertionJwkProviderFor(missingKeystore);

    // when the user logs out
    handler(reg -> jwkProvider.createJwk(reg.getRegistrationId()))
        .logout(request, response, authentication(registration));

    // then the application's own message is named, and the JDK's, which quotes the path, is not
    assertThat(output.getAll())
        .contains("IllegalStateException: Unable to load keystore")
        .doesNotContain(missingKeystore);
  }

  /**
   * The registration id reaches every one of these log lines and comes straight from configuration
   * (the {@code providers.oidc.<id>} key, or {@code registration-id}). A CR or LF in it would forge
   * a second line in the WARN it lands in.
   */
  @Test
  void shouldNotLetAConfiguredRegistrationIdForgeALogLine(final CapturedOutput output) {
    // given a registration whose configured id carries a line break
    setLogLevel(HANDLER_LOGGER, LogLevel.DEBUG);
    final var forgingId = "oidc\n\rWARN  forged line";
    final var registration =
        registration(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
            .registrationId(forgingId)
            .build();
    when(clientRegistrationRepository.findByRegistrationId(forgingId)).thenReturn(registration);
    when(authorizedClientRepository.loadAuthorizedClient(
            eq(forgingId), any(Authentication.class), any()))
        .thenReturn(
            new OAuth2AuthorizedClient(
                registration,
                "principal",
                accessToken(),
                new OAuth2RefreshToken(REFRESH_TOKEN_VALUE, Instant.now())));
    server.expect(requestTo(REVOCATION_ENDPOINT)).andRespond(withBadRequest());

    // when the user logs out
    handler(null).logout(request, response, authentication(registration));

    // then the break is escaped rather than written out, so the forged line never appears
    assertThat(output.getAll())
        .contains("oidc\\u000a\\u000dWARN  forged line")
        .doesNotContain("\nWARN  forged line");
  }

  /**
   * Runs the production client, not {@link MockRestServiceServer}. Framing is decided by the
   * request factory, so a mock transport cannot show it: with no {@code Content-Length} the JDK
   * client sends {@code Transfer-Encoding: chunked}, which some IdP front-ends, WAFs and older
   * reverse proxies answer with 400 or 411. Revocation would then fail wherever login works, and
   * only ever show up as a WARN.
   */
  @Test
  void shouldSendAFixedLengthBodyRatherThanAChunkedOne() throws Exception {
    // given a real HTTP endpoint standing in for the IdP
    final var received = new AtomicReference<HttpHeaders>();
    final var httpServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    httpServer.createContext(
        "/revoke",
        exchange -> {
          final var seen = new HttpHeaders();
          exchange.getRequestHeaders().forEach(seen::addAll);
          received.set(seen);
          exchange.sendResponseHeaders(200, -1);
          exchange.close();
        });
    httpServer.start();
    try {
      final var endpoint = "http://127.0.0.1:" + httpServer.getAddress().getPort() + "/revoke";
      final var registration =
          registration(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
              .providerConfigurationMetadata(Map.of("revocation_endpoint", endpoint))
              .build();
      givenAuthorizedSession(registration);

      // when the user logs out, through the handler's own production client
      new RefreshTokenRevocationLogoutHandler(
              clientRegistrationRepository, authorizedClientRepository, null)
          .logout(request, response, authentication(registration));

      // then the IdP saw a fixed-length body
      assertThat(received.get()).as("the revocation request never arrived").isNotNull();
      assertThat(received.get().getContentLength()).isPositive();
      assertThat(received.get().headerNames())
          .noneSatisfy(name -> assertThat(name).isEqualToIgnoringCase("Transfer-Encoding"));
    } finally {
      httpServer.stop(0);
    }
  }

  /**
   * The other half of the non-2xx check, and the other reason the JDK client is used: its redirect
   * policy is {@code NEVER}. A client that follows the redirect would POST the refresh token at
   * whatever the {@code Location} header names and report the second response as a successful
   * revocation, while the revocation endpoint revoked nothing. Needs the production client, because
   * redirect policy belongs to the request factory.
   */
  @Test
  void shouldNotFollowARedirectFromTheRevocationEndpoint() throws Exception {
    // given a revocation endpoint that answers with a redirect somewhere else on the same server
    final var redirectFollowed = new java.util.concurrent.atomic.AtomicBoolean(false);
    final var httpServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    final var base = "http://127.0.0.1:" + httpServer.getAddress().getPort();
    httpServer.createContext(
        "/revoke",
        exchange -> {
          exchange.getResponseHeaders().add("Location", base + "/elsewhere");
          exchange.sendResponseHeaders(302, -1);
          exchange.close();
        });
    httpServer.createContext(
        "/elsewhere",
        exchange -> {
          redirectFollowed.set(true);
          exchange.sendResponseHeaders(200, -1);
          exchange.close();
        });
    httpServer.start();
    try {
      final var registration =
          registration(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
              .providerConfigurationMetadata(Map.of("revocation_endpoint", base + "/revoke"))
              .build();
      givenAuthorizedSession(registration);

      // when the user logs out, through the handler's own production client
      new RefreshTokenRevocationLogoutHandler(
              clientRegistrationRepository, authorizedClientRepository, null)
          .logout(request, response, authentication(registration));

      // then the redirect was not followed, so the token was never re-sent elsewhere
      assertThat(redirectFollowed)
          .as("the refresh token must not be POSTed at a redirect target")
          .isFalse();
    } finally {
      httpServer.stop(0);
    }
  }

  /**
   * The reason the JDK client is used rather than {@code SimpleClientHttpRequestFactory}: its read
   * timeout is a deadline on the whole exchange, applied through {@code
   * HttpRequest.Builder#timeout} and extended to the response stream. {@code
   * SimpleClientHttpRequestFactory}'s is {@code URLConnection}'s socket inactivity timeout, so an
   * IdP that keeps sending a byte every second never trips it and holds the servlet thread serving
   * the logout open for as long as it likes.
   *
   * <p>Deliberately spends the deadline rather than mocking it: the difference between the two
   * factories is only visible against a transport, and a few seconds once is cheaper than shipping
   * a logout that can hang.
   */
  @Test
  void shouldAbandonARevocationThatTricklesPastTheDeadline() throws Exception {
    // given an IdP that answers, then feeds the response body a byte at a time, slowly enough that
    // no single read ever times out
    final var httpServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    final var trickledBytes = 10;
    httpServer.createContext(
        "/revoke",
        exchange -> {
          exchange.sendResponseHeaders(200, trickledBytes);
          try (final var body = exchange.getResponseBody()) {
            for (var i = 0; i < trickledBytes; i++) {
              body.write('.');
              body.flush();
              Thread.sleep(1_000);
            }
          } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
          } catch (final java.io.IOException expectedOnceTheClientGivesUp) {
            // the deadline closed the connection mid-body, which is the point of this test
          }
          exchange.close();
        });
    httpServer.start();
    try {
      final var endpoint = "http://127.0.0.1:" + httpServer.getAddress().getPort() + "/revoke";
      final var registration =
          registration(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
              .providerConfigurationMetadata(Map.of("revocation_endpoint", endpoint))
              .build();
      givenAuthorizedSession(registration);

      // when the user logs out, through the handler's own production client
      final var startedAt = System.nanoTime();
      new RefreshTokenRevocationLogoutHandler(
              clientRegistrationRepository, authorizedClientRepository, null)
          .logout(request, response, authentication(registration));
      final var elapsed = Duration.ofNanos(System.nanoTime() - startedAt);

      // then logout was not held up for the whole trickle. The bound is loose on purpose: the
      // claim is "bounded", and a per-read timeout would let this run for trickledBytes seconds.
      assertThat(elapsed)
          .as("logout must not wait on an IdP that trickles its response")
          .isLessThan(Duration.ofSeconds(trickledBytes - 2L));
    } finally {
      httpServer.stop(0);
    }
  }

  /**
   * The real {@link AssertionJwkProvider}, so that the failure it raises carries its own frame,
   * which is what decides whether the description may repeat its message.
   */
  private static AssertionJwkProvider assertionJwkProviderFor(final String keystorePath) {
    final var keystore = new KeystoreConfiguration();
    keystore.setPath(keystorePath);
    keystore.setPassword("password");
    keystore.setKeyAlias("alias");
    keystore.setKeyPassword("password");
    final var assertion = new AssertionConfiguration();
    assertion.setKeystore(keystore);
    final var oidc = new OidcAuthenticationConfiguration();
    oidc.setAssertion(assertion);
    final var repository = Mockito.mock(OidcAuthenticationConfigurationRepository.class);
    Mockito.when(repository.getOidcAuthenticationConfigurationById(REGISTRATION_ID))
        .thenReturn(oidc);
    return new AssertionJwkProvider(repository);
  }

  private static void setLogLevel(final String logger, final LogLevel level) {
    LoggingSystem.get(ClassLoader.getSystemClassLoader()).setLogLevel(logger, level);
  }

  private RefreshTokenRevocationLogoutHandler handler(
      final Function<ClientRegistration, JWK> jwkResolver) {
    return new RefreshTokenRevocationLogoutHandler(
        clientRegistrationRepository,
        authorizedClientRepository,
        jwkResolver,
        restClientBuilder.build());
  }

  private void givenAuthorizedSession(final ClientRegistration registration) {
    when(clientRegistrationRepository.findByRegistrationId(REGISTRATION_ID))
        .thenReturn(registration);
    when(authorizedClientRepository.loadAuthorizedClient(
            eq(REGISTRATION_ID), any(Authentication.class), any()))
        .thenReturn(
            new OAuth2AuthorizedClient(
                registration,
                "principal",
                accessToken(),
                new OAuth2RefreshToken(REFRESH_TOKEN_VALUE, Instant.now())));
  }

  /**
   * Asserts on the decoded form fields rather than a raw body substring, so percent-encoding cannot
   * make an assertion pass or fail by accident, and so absent fields can be asserted too.
   */
  private static RequestMatcher formBody(final Consumer<Map<String, String>> assertion) {
    return request ->
        assertion.accept(formFields(((MockClientHttpRequest) request).getBodyAsString()));
  }

  private static RequestMatcher requestTo(final String uri) {
    return request -> assertThat(request.getURI()).hasToString(uri);
  }

  private static RequestMatcher post() {
    return request -> assertThat(request.getMethod()).isEqualTo(HttpMethod.POST);
  }

  /** The header counterpart of {@link #formBody}. */
  private static RequestMatcher headers(final Consumer<HttpHeaders> assertion) {
    return request -> assertion.accept(request.getHeaders());
  }

  private static Map<String, String> formFields(final String body) {
    if (body.isEmpty()) {
      return Map.of();
    }
    return Arrays.stream(body.split("&"))
        .map(pair -> pair.split("=", 2))
        .collect(
            Collectors.toMap(
                pair -> URLDecoder.decode(pair[0], StandardCharsets.UTF_8),
                pair -> pair.length > 1 ? URLDecoder.decode(pair[1], StandardCharsets.UTF_8) : "",
                (first, second) -> first));
  }

  private static ClientRegistration.Builder registration(
      final ClientAuthenticationMethod clientAuthenticationMethod) {
    return ClientRegistration.withRegistrationId(REGISTRATION_ID)
        .clientId(CLIENT_ID)
        .clientSecret(CLIENT_SECRET)
        .clientAuthenticationMethod(clientAuthenticationMethod)
        .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
        .redirectUri("{baseUrl}/sso-callback")
        .authorizationUri("https://idp.example.com/oauth2/authorize")
        .tokenUri("https://idp.example.com/oauth2/token")
        .jwkSetUri("https://idp.example.com/oauth2/jwks")
        .userNameAttributeName("sub")
        .scope("openid")
        .providerConfigurationMetadata(Map.of("revocation_endpoint", REVOCATION_ENDPOINT));
  }

  private static OAuth2AccessToken accessToken() {
    return new OAuth2AccessToken(
        OAuth2AccessToken.TokenType.BEARER,
        "the-access-token",
        Instant.now(),
        Instant.now().plusSeconds(300));
  }

  private static OAuth2AuthenticationToken authentication(final ClientRegistration registration) {
    final var idToken =
        new OidcIdToken(
            "id-token", Instant.now(), Instant.now().plusSeconds(300), Map.of("sub", "principal"));
    final var user = new DefaultOidcUser(List.of(new SimpleGrantedAuthority("ROLE_USER")), idToken);
    return new OAuth2AuthenticationToken(
        user, user.getAuthorities(), registration.getRegistrationId());
  }

  private static Function<ClientRegistration, JWK> jwkResolver(final JWK jwk) {
    return registration -> jwk;
  }

  private static JWK rsaJwk() throws Exception {
    final var generator = KeyPairGenerator.getInstance("RSA");
    generator.initialize(2048);
    final var pair = generator.generateKeyPair();
    return new RSAKey.Builder((RSAPublicKey) pair.getPublic())
        .privateKey((RSAPrivateKey) pair.getPrivate())
        .keyUse(KeyUse.SIGNATURE)
        .algorithm(JWSAlgorithm.RS256)
        .keyID("revocation-test")
        .build();
  }

  private static String basicAuth(final String clientId, final String clientSecret) {
    return "Basic "
        + Base64.getEncoder()
            .encodeToString((clientId + ":" + clientSecret).getBytes(StandardCharsets.UTF_8));
  }
}
