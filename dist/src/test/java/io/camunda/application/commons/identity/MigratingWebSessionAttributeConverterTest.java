/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.security.api.model.CamundaAuthentication;
import jakarta.servlet.http.Cookie;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.FactorGrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUserAuthority;
import org.springframework.security.web.savedrequest.DefaultSavedRequest;
import org.springframework.security.web.savedrequest.SavedCookie;

class MigratingWebSessionAttributeConverterTest {

  private final MigratingWebSessionAttributeConverter converter =
      new MigratingWebSessionAttributeConverter();

  @Test
  void shouldDeserializeSessionSerializedWithOldCamundaAuthenticationFqn() throws IOException {
    // given — a payload serialized by the old monorepo class at io.camunda.security.auth
    final var oldAuth =
        new io.camunda.security.auth.CamundaAuthentication(
            "alice",
            null,
            false,
            List.of("group1"),
            List.of("role1"),
            List.of("tenant1"),
            List.of(),
            Map.of("claim", "value"));
    final byte[] payload = serialize(oldAuth);

    // when
    final Object result = converter.deserialize(payload);

    // then — result is the new CSL class, with all fields preserved
    assertThat(result).isInstanceOf(CamundaAuthentication.class);
    final var auth = (CamundaAuthentication) result;
    assertThat(auth.authenticatedUsername()).isEqualTo("alice");
    assertThat(auth.authenticatedClientId()).isNull();
    assertThat(auth.anonymousUser()).isFalse();
    assertThat(auth.authenticatedGroupIds()).containsExactly("group1");
    assertThat(auth.authenticatedRoleIds()).containsExactly("role1");
    assertThat(auth.authenticatedTenantIds()).containsExactly("tenant1");
    assertThat(auth.authenticatedMappingRuleIds()).isEmpty();
    assertThat(auth.claims()).containsEntry("claim", "value");
  }

  @Test
  void shouldRoundTripCurrentCamundaAuthenticationUnchanged() {
    // given — a session attribute serialized by the current CSL class (no migration needed)
    final var auth =
        CamundaAuthentication.of(
            b -> b.user("bob").group("admins").tenant("default").claims(Map.of("sub", "bob")));
    final byte[] payload = converter.serialize(auth);

    // when
    final Object result = converter.deserialize(payload);

    // then
    assertThat(result).isInstanceOf(CamundaAuthentication.class);
    final var restored = (CamundaAuthentication) result;
    assertThat(restored.authenticatedUsername()).isEqualTo("bob");
    assertThat(restored.authenticatedGroupIds()).containsExactly("admins");
    assertThat(restored.authenticatedTenantIds()).containsExactly("default");
    assertThat(restored.claims()).containsEntry("sub", "bob");
  }

  @Test
  void shouldRoundTripCamundaAuthenticationWithTypedClaimValues() {
    // given — claim values of the kinds an identity provider returns
    final var claims = new LinkedHashMap<String, Object>();
    claims.put("string", "value");
    claims.put("long", 42L);
    claims.put("integer", 7);
    claims.put("double", 1.5d);
    claims.put("decimal", new java.math.BigDecimal("12.50"));
    claims.put("bigInteger", new java.math.BigInteger("12345678901234567890"));
    claims.put("boolean", true);
    claims.put("list", List.of("a", "b"));
    claims.put("nested", new HashMap<>(Map.of("key", List.of("x"))));
    final var auth = CamundaAuthentication.of(b -> b.user("carol").claims(claims));

    // when
    final var restored = (CamundaAuthentication) converter.deserialize(converter.serialize(auth));

    // then
    assertThat(restored.claims()).containsAllEntriesOf(claims);
  }

  @Test
  void shouldRoundTripImmutableCollectionsOfEverySize() {
    // given — the JDK uses a different nested class for each size, so claim values of any size
    // must be accepted
    final var claims = new LinkedHashMap<String, Object>();
    claims.put("list0", List.of());
    claims.put("list1", List.of("a"));
    claims.put("list2", List.of("a", "b"));
    claims.put("list3", List.of("a", "b", "c"));
    claims.put("set0", Set.of());
    claims.put("set1", Set.of("a"));
    claims.put("set2", Set.of("a", "b"));
    claims.put("set3", Set.of("a", "b", "c"));
    claims.put("map0", Map.of());
    claims.put("map1", Map.of("k", "v"));
    claims.put("map2", Map.of("k", "v", "l", "w"));
    claims.put("map3", Map.of("k", "v", "l", "w", "m", "x"));
    final var auth = CamundaAuthentication.of(b -> b.user("frank").claims(claims));

    // when
    final var restored = (CamundaAuthentication) converter.deserialize(converter.serialize(auth));

    // then
    assertThat(restored.claims()).containsAllEntriesOf(claims);
  }

  @Test
  void shouldRoundTripRequestSavedByTheRequestCache() {
    // given — the request Spring Security saves before redirecting to the login
    final var saved =
        new DefaultSavedRequest.Builder()
            .setContextPath("")
            .setServletPath("/operate/processes")
            .setRequestURI("/operate/processes")
            .setRequestURL("http://localhost:8080/operate/processes")
            .setScheme("http")
            .setServerName("localhost")
            .setServerPort(8080)
            .setMethod("GET")
            .setCookies(List.of(new SavedCookie(new Cookie("c", "v"))))
            .setLocales(List.of(Locale.GERMANY))
            .setHeaders(new TreeMap<>(Map.of("Accept", List.of("text/html"))))
            .setParameters(new HashMap<>(Map.of("p", new String[] {"1", "2"})))
            .build();

    // when
    final var restored = (DefaultSavedRequest) converter.deserialize(converter.serialize(saved));

    // then
    assertThat(restored.getRequestURI()).isEqualTo("/operate/processes");
    assertThat(restored.getCookies()).hasSize(1);
    assertThat(restored.getLocales()).containsExactly(Locale.GERMANY);
    assertThat(restored.getParameterValues("p")).containsExactly("1", "2");
  }

  @Test
  void shouldRoundTripBasicLoginSession() {
    // given — the security context a basic login stores
    final var user =
        new User(
            "erin",
            "{noop}secret",
            List.of(
                new SimpleGrantedAuthority("ROLE_USER"),
                FactorGrantedAuthority.fromAuthority("FACTOR_PASSWORD")));
    final var context =
        new SecurityContextImpl(
            UsernamePasswordAuthenticationToken.authenticated(user, null, user.getAuthorities()));

    // when
    final var restored = (SecurityContextImpl) converter.deserialize(converter.serialize(context));

    // then
    assertThat(restored.getAuthentication().getName()).isEqualTo("erin");
    assertThat(restored.getAuthentication().getAuthorities())
        .extracting(a -> a.getAuthority())
        .containsExactlyInAnyOrder("ROLE_USER", "FACTOR_PASSWORD");
  }

  @Test
  void shouldRoundTripOidcLoginSession() {
    // given — the session attributes an OIDC login stores: the security context and the
    // authorized client
    final var idToken =
        new OidcIdToken(
            "id-token",
            Instant.now(),
            Instant.now().plusSeconds(60),
            Map.of("sub", "dave", "iss", "https://idp.example.com"));
    final var userInfo = new OidcUserInfo(Map.of("sub", "dave"));
    final var user =
        new DefaultOidcUser(
            List.of(new OidcUserAuthority(idToken, userInfo)), idToken, userInfo, "sub");
    final var token =
        new OAuth2AuthenticationToken(user, List.of(new SimpleGrantedAuthority("ROLE_USER")), "id");
    final var context = new SecurityContextImpl(token);

    final var registration =
        ClientRegistration.withRegistrationId("id")
            .clientId("client")
            .clientSecret("secret")
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUri("https://camunda.example.com/sso-callback")
            .authorizationUri("https://idp.example.com/auth")
            .tokenUri("https://idp.example.com/token")
            .userInfoUri("https://idp.example.com/userinfo")
            .jwkSetUri("https://idp.example.com/jwks")
            .scope("openid")
            .build();
    final var client =
        new OAuth2AuthorizedClient(
            registration,
            "dave",
            new OAuth2AccessToken(
                OAuth2AccessToken.TokenType.BEARER,
                "access",
                Instant.now(),
                Instant.now().plusSeconds(60)),
            new OAuth2RefreshToken("refresh", Instant.now()));

    // when
    final var restoredContext =
        (SecurityContextImpl) converter.deserialize(converter.serialize(context));
    final var restoredClient =
        (OAuth2AuthorizedClient) converter.deserialize(converter.serialize(client));

    // then
    assertThat(restoredContext.getAuthentication().getName()).isEqualTo(token.getName());
    assertThat(restoredClient.getPrincipalName()).isEqualTo("dave");
    assertThat(restoredClient.getClientRegistration().getRegistrationId()).isEqualTo("id");
  }

  @Test
  void shouldRejectClassOutsideTheAllowlistWithoutInstantiatingIt() throws IOException {
    // given — a payload for a class that is serializable but not a session attribute type
    ForbiddenSessionAttribute.deserialized = false;
    final byte[] payload = serialize(new ForbiddenSessionAttribute());

    // when / then — the class is rejected before it is read
    assertThatThrownBy(() -> converter.deserialize(payload))
        .hasRootCauseInstanceOf(java.io.InvalidClassException.class);
    assertThat(ForbiddenSessionAttribute.deserialized).isFalse();
  }

  @Test
  void shouldRejectForbiddenClassNestedInAllowedContainer() throws IOException {
    // given — an allowed container that holds a class outside the allowlist
    ForbiddenSessionAttribute.deserialized = false;
    final byte[] payload = serialize(new HashMap<>(Map.of("key", new ForbiddenSessionAttribute())));

    // when / then
    assertThatThrownBy(() -> converter.deserialize(payload))
        .hasRootCauseInstanceOf(java.io.InvalidClassException.class);
    assertThat(ForbiddenSessionAttribute.deserialized).isFalse();
  }

  @Test
  void shouldRejectInvalidSerializationStream() {
    // given
    final byte[] payload = "NOT-VALID-JAVA-SERIALIZATION".getBytes();

    // when / then
    assertThatThrownBy(() -> converter.deserialize(payload)).isInstanceOf(RuntimeException.class);
  }

  private static byte[] serialize(final Object obj) throws IOException {
    final var baos = new ByteArrayOutputStream();
    try (final var oos = new ObjectOutputStream(baos)) {
      oos.writeObject(obj);
    }
    return baos.toByteArray();
  }

  private static final class ForbiddenSessionAttribute implements Serializable {
    private static volatile boolean deserialized;

    private void readObject(final ObjectInputStream in) {
      deserialized = true;
    }
  }
}
