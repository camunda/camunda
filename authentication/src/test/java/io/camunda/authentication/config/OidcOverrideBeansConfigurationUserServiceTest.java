/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.sun.net.httpserver.HttpServer;
import io.camunda.security.spring.CamundaSecurityLibraryProperties;
import io.camunda.security.spring.oidc.FailSoftOidcUserService;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.ObservationRegistry;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

/**
 * Verifies the {@code OidcUserService} bean the host registers for the OIDC webapp chain. Because
 * it is a {@code @ConditionalOnMissingBean} override of the security library's default, it must
 * keep the library's fail-soft UserInfo behaviour (ADR-0028) while retaining the host's
 * instrumentation of the UserInfo request.
 *
 * <p>A real {@link HttpServer} stands in for the IdP's {@code /userinfo} endpoint, so these tests
 * exercise the bean's actual HTTP client rather than a mocked delegate.
 */
class OidcOverrideBeansConfigurationUserServiceTest {

  private static final String SUBJECT = "user-subject";

  private final OidcOverrideBeansConfiguration configuration =
      new OidcOverrideBeansConfiguration(new CamundaSecurityLibraryProperties());

  private HttpServer idp;
  private volatile int userInfoStatus;
  private volatile String userInfoBody;
  private final AtomicInteger userInfoCalls = new AtomicInteger();

  @BeforeEach
  void startIdp() throws IOException {
    idp = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    idp.createContext(
        "/userinfo",
        exchange -> {
          userInfoCalls.incrementAndGet();
          final var body = userInfoBody.getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().add("Content-Type", "application/json");
          exchange.sendResponseHeaders(userInfoStatus, body.length == 0 ? -1 : body.length);
          if (body.length > 0) {
            exchange.getResponseBody().write(body);
          }
          exchange.close();
        });
    idp.start();
  }

  @AfterEach
  void stopIdp() {
    idp.stop(0);
  }

  @Test
  void shouldContinueLoginWithIdTokenClaimsWhenUserInfoRejectsTheAccessToken() {
    // given an IdP like Entra that rejects an app-audience access token at /userinfo
    userInfoStatus = 401;
    userInfoBody = "{\"error\":{\"code\":\"InvalidAuthenticationToken\"}}";
    final var service = userService(ObservationRegistry.NOOP);

    // when
    final OidcUser user = service.loadUser(userRequest(Map.of()));

    // then login continues on the ID token claims
    assertThat(userInfoCalls).hasValue(1);
    assertThat(user.getSubject()).isEqualTo(SUBJECT);
    assertThat(user.getClaims()).containsEntry("preferred_username", "alice");
  }

  @Test
  void shouldContinueLoginWhenUserInfoResponseIsMalformed() {
    // given
    userInfoStatus = 200;
    userInfoBody = "";
    final var service = userService(ObservationRegistry.NOOP);

    // when
    final OidcUser user = service.loadUser(userRequest(Map.of()));

    // then
    assertThat(user.getSubject()).isEqualTo(SUBJECT);
  }

  @Test
  void shouldFailLoginWhenUserInfoIsRequiredAndTheCallFails() {
    // given
    userInfoStatus = 401;
    userInfoBody = "{}";
    final var service = userService(ObservationRegistry.NOOP);

    // when / then
    assertThatThrownBy(
            () ->
                service.loadUser(
                    userRequest(
                        Map.of(FailSoftOidcUserService.USER_INFO_REQUIRED_METADATA_KEY, true))))
        .isInstanceOf(OAuth2AuthenticationException.class);
  }

  @Test
  void shouldMergeUserInfoClaimsWhenUserInfoSucceeds() {
    // given
    userInfoStatus = 200;
    userInfoBody = "{\"sub\":\"" + SUBJECT + "\",\"groups\":[\"admins\"]}";
    final var service = userService(ObservationRegistry.NOOP);

    // when
    final OidcUser user = service.loadUser(userRequest(Map.of()));

    // then
    assertThat(user.getClaims()).containsKey("groups").containsEntry("sub", SUBJECT);
  }

  @Test
  void shouldRejectUserInfoResponseWithMismatchingSubject() {
    // given a successful response about a different user, which is a protocol violation
    userInfoStatus = 200;
    userInfoBody = "{\"sub\":\"someone-else\"}";
    final var service = userService(ObservationRegistry.NOOP);

    // when / then it must not be swallowed by the fail-soft fallback
    assertThatThrownBy(() -> service.loadUser(userRequest(Map.of())))
        .isInstanceOf(OAuth2AuthenticationException.class);
  }

  @Test
  void shouldObserveUserInfoRequestsForMetrics() {
    // given
    userInfoStatus = 200;
    userInfoBody = "{\"sub\":\"" + SUBJECT + "\"}";
    final List<String> observed = new CopyOnWriteArrayList<>();
    final var registry = ObservationRegistry.create();
    registry
        .observationConfig()
        .observationHandler(
            new ObservationHandler<>() {
              @Override
              public boolean supportsContext(final Observation.Context context) {
                return true;
              }

              @Override
              public void onStop(final Observation.Context context) {
                observed.add(context.getName());
              }
            });
    final var service = userService(registry);

    // when
    service.loadUser(userRequest(Map.of()));

    // then
    assertThat(observed).contains("camunda_authentication_external_requests");
  }

  private OidcUserService userService(final ObservationRegistry observationRegistry) {
    @SuppressWarnings("unchecked")
    final ObjectProvider<ObservationRegistry> provider = mock(ObjectProvider.class);
    when(provider.getIfAvailable(any())).thenReturn(observationRegistry);
    return configuration.oidcUserService(provider);
  }

  private OidcUserRequest userRequest(final Map<String, Object> providerMetadata) {
    final var registration =
        ClientRegistration.withRegistrationId("oidc")
            .clientId("client")
            .clientSecret("secret")
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUri("https://redirect.example.com/sso-callback")
            .scope("openid", "profile")
            .authorizationUri("https://authorization.example.com")
            .tokenUri("https://token.example.com")
            .jwkSetUri("https://jwks.example.com")
            .userInfoUri("http://127.0.0.1:" + idp.getAddress().getPort() + "/userinfo")
            .userNameAttributeName("sub")
            .providerConfigurationMetadata(providerMetadata)
            .build();
    final var now = Instant.now();
    final var accessToken =
        new OAuth2AccessToken(
            OAuth2AccessToken.TokenType.BEARER,
            "access-token",
            now,
            now.plusSeconds(60),
            Set.of("openid", "profile"));
    final var idToken =
        new OidcIdToken(
            "id-token",
            now,
            now.plusSeconds(60),
            Map.of(
                "sub",
                SUBJECT,
                "iss",
                "https://issuer.example.com",
                "preferred_username",
                "alice"));
    return new OidcUserRequest(registration, accessToken, idToken);
  }
}
