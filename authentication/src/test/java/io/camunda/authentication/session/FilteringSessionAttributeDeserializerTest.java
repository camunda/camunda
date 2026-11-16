/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nimbusds.openid.connect.sdk.op.OIDCProviderMetadata;
import io.camunda.authentication.session.WebSessionMapper.SpringBasedWebSessionAttributeConverter;
import io.camunda.security.auth.CamundaAuthentication;
import jakarta.servlet.http.Cookie;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputFilter;
import java.io.ObjectInputFilter.FilterInfo;
import java.io.ObjectInputFilter.Status;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.net.URI;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.core.convert.support.GenericConversionService;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.FactorGrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthenticationMethod;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUserAuthority;
import org.springframework.security.web.authentication.WebAuthenticationDetails;
import org.springframework.security.web.savedrequest.DefaultSavedRequest;
import org.springframework.security.web.savedrequest.SavedCookie;

class FilteringSessionAttributeDeserializerTest {

  private static final Set<Class<?>> SEEN_CLASSES = ConcurrentHashMap.newKeySet();

  // a discovery document as Keycloak serves it, shortened
  private static final String DISCOVERY_DOCUMENT =
      """
      {
        "issuer": "https://idp.example.com/realms/camunda",
        "authorization_endpoint": "https://idp.example.com/auth",
        "token_endpoint": "https://idp.example.com/token",
        "userinfo_endpoint": "https://idp.example.com/userinfo",
        "jwks_uri": "https://idp.example.com/jwks",
        "scopes_supported": ["openid", "profile", "email"],
        "subject_types_supported": ["public", "pairwise"],
        "id_token_signing_alg_values_supported": ["RS256", "ES256"],
        "response_types_supported": ["code", "id_token"],
        "claims_supported": ["sub", "iss", "name"],
        "frontchannel_logout_supported": true,
        "mtls_endpoint_aliases": {
          "token_endpoint": "https://mtls.idp.example.com/token"
        }
      }
      """;

  private final SpringBasedWebSessionAttributeConverter converter =
      new SpringBasedWebSessionAttributeConverter(new GenericConversionService());

  @Test
  void shouldRoundTripCurrentCamundaAuthenticationUnchanged() {
    // given — a session attribute serialized by the current CSL class (no migration needed)
    final var auth =
        CamundaAuthentication.of(
            b -> b.user("bob").group("admins").tenant("default").claims(Map.of("sub", "bob")));

    // when
    final Object result = roundTrip(auth);

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
    final var restored = (CamundaAuthentication) roundTrip(auth);

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
    final var restored = (CamundaAuthentication) roundTrip(auth);

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
    final var restored = (DefaultSavedRequest) roundTrip(saved);

    // then
    assertThat(restored.getRequestURI()).isEqualTo("/operate/processes");
    assertThat(restored.getCookies()).hasSize(1);
    assertThat(restored.getLocales()).containsExactly(Locale.GERMANY);
    assertThat(restored.getParameterValues("p")).containsExactly("1", "2");
  }

  @Test
  void shouldRoundTripBasicLoginSession() {
    // given — the security context a basic login stores, with the details a real request sets
    final var user =
        new User(
            "erin",
            "{noop}secret",
            List.of(
                new SimpleGrantedAuthority("ROLE_USER"),
                FactorGrantedAuthority.fromAuthority("FACTOR_PASSWORD")));
    final var authentication =
        UsernamePasswordAuthenticationToken.authenticated(user, null, user.getAuthorities());
    authentication.setDetails(new WebAuthenticationDetails("203.0.113.7", "session-id"));
    final var context = new SecurityContextImpl(authentication);

    // when
    final var restored = (SecurityContextImpl) roundTrip(context);

    // then
    assertThat(restored.getAuthentication().getName()).isEqualTo("erin");
    assertThat(restored.getAuthentication().getAuthorities())
        .extracting(a -> a.getAuthority())
        .containsExactlyInAnyOrder("ROLE_USER", "FACTOR_PASSWORD");
    assertThat(restored.getAuthentication().getDetails())
        .isEqualTo(new WebAuthenticationDetails("203.0.113.7", "session-id"));
  }

  @Test
  void shouldRoundTripOidcLoginSession() throws Exception {
    // given — the claims as Spring's converters and the identity provider produce them: Instant for
    // the timestamps, URL for the issuer, booleans, lists and nested maps
    final var issuer = URI.create("https://idp.example.com/realms/camunda").toURL();
    final var idToken =
        new OidcIdToken(
            "id-token",
            Instant.now(),
            Instant.now().plusSeconds(60),
            Map.of(
                "iss", issuer,
                "sub", "dave",
                "aud", List.of("camunda"),
                "iat", Instant.now(),
                "exp", Instant.now().plusSeconds(60),
                "auth_time", Instant.now(),
                "email_verified", true));
    final var userInfo =
        new OidcUserInfo(
            Map.of(
                "sub", "dave",
                "email_verified", false,
                "updated_at", Instant.now(),
                "groups", List.of("admins", "operators"),
                "address", Map.of("country", "DE")));
    final var user =
        new DefaultOidcUser(
            List.of(new OidcUserAuthority(idToken, userInfo)), idToken, userInfo, "sub");
    final var token =
        new OAuth2AuthenticationToken(user, List.of(new SimpleGrantedAuthority("ROLE_USER")), "id");
    token.setDetails(new WebAuthenticationDetails("203.0.113.7", "session-id"));
    final var context = new SecurityContextImpl(token);

    // the registration as provider discovery builds it, with the parsed discovery document as
    // metadata, nested objects and arrays included
    final var discoveryMetadata = discoveryMetadata();
    final var registration =
        ClientRegistration.withRegistrationId("id")
            .clientId("client")
            .clientSecret("secret")
            .clientName("Camunda")
            .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUri("https://camunda.example.com/sso-callback")
            .issuerUri(issuer.toString())
            .authorizationUri("https://idp.example.com/auth")
            .tokenUri("https://idp.example.com/token")
            .userInfoUri("https://idp.example.com/userinfo")
            .userInfoAuthenticationMethod(AuthenticationMethod.HEADER)
            .userNameAttributeName("sub")
            .jwkSetUri("https://idp.example.com/jwks")
            .scope("openid", "profile")
            .providerConfigurationMetadata(discoveryMetadata)
            .build();
    final var client =
        new OAuth2AuthorizedClient(
            registration,
            "dave",
            new OAuth2AccessToken(
                OAuth2AccessToken.TokenType.BEARER,
                "access",
                Instant.now(),
                Instant.now().plusSeconds(60),
                Set.of("openid", "profile")),
            new OAuth2RefreshToken("refresh", Instant.now()));

    // a registration configured by hand: no discovery metadata, and an access token without scopes
    final var manualRegistration =
        ClientRegistration.withRegistrationId("manual")
            .clientId("client")
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUri("https://camunda.example.com/sso-callback")
            .authorizationUri("https://idp.example.com/auth")
            .tokenUri("https://idp.example.com/token")
            .build();
    final var manualClient =
        new OAuth2AuthorizedClient(
            manualRegistration,
            "dave",
            new OAuth2AccessToken(
                OAuth2AccessToken.TokenType.BEARER,
                "access",
                Instant.now(),
                Instant.now().plusSeconds(60)));

    // when
    final var restoredContext = (SecurityContextImpl) roundTrip(context);
    final var restoredClient = (OAuth2AuthorizedClient) roundTrip(client);
    final var restoredManualClient = (OAuth2AuthorizedClient) roundTrip(manualClient);

    // then
    final var restoredToken = (OAuth2AuthenticationToken) restoredContext.getAuthentication();
    final var restoredUser = (DefaultOidcUser) restoredToken.getPrincipal();
    assertThat(restoredToken.getName()).isEqualTo(token.getName());
    assertThat(restoredToken.getDetails())
        .isEqualTo(new WebAuthenticationDetails("203.0.113.7", "session-id"));
    assertThat(restoredUser.getIdToken().getClaims()).containsEntry("iss", issuer);
    assertThat(restoredUser.getUserInfo().getClaims()).containsEntry("email_verified", false);
    assertThat(restoredClient.getPrincipalName()).isEqualTo("dave");
    assertThat(restoredManualClient.getAccessToken().getScopes()).isEmpty();
    assertThat(
            restoredClient.getClientRegistration().getProviderDetails().getConfigurationMetadata())
        .containsEntry("issuer", issuer.toString());
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

  /**
   * Fails when an allowlist entry matches none of the classes the round trips above put into a
   * stream, so a typo or a class that no session contains any more does not stay in the list.
   */
  @AfterAll
  static void verifyEveryAllowedClassIsExercised() {
    final var unused =
        FilteringSessionAttributeDeserializer.ALLOWED_CLASSES.stream()
            .filter(
                entry -> {
                  final var filter = ObjectInputFilter.Config.createFilter(entry);
                  return SEEN_CLASSES.stream()
                      .noneMatch(c -> filter.checkInput(filterInfo(c)) == Status.ALLOWED);
                })
            .toList();

    assertThat(unused).as("allowlist entries that no round trip in this test produces").isEmpty();
  }

  private Object roundTrip(final Object value) {
    final byte[] payload = converter.serialize(value);
    recordClasses(payload);
    return converter.deserialize(payload);
  }

  // Reads the payload with a filter that only records, the way the converter's own filter sees it.
  // Array types are recorded by their component type, as the JDK pattern matcher checks them.
  private static void recordClasses(final byte[] payload) {
    try (final var in = new ObjectInputStream(new ByteArrayInputStream(payload))) {
      in.setObjectInputFilter(
          info -> {
            Class<?> type = info.serialClass();
            while (type != null && type.isArray()) {
              type = type.getComponentType();
            }
            if (type != null && !type.isPrimitive()) {
              SEEN_CLASSES.add(type);
            }
            return Status.UNDECIDED;
          });
      in.readObject();
    } catch (final IOException | ClassNotFoundException e) {
      throw new IllegalStateException(e);
    }
  }

  private static FilterInfo filterInfo(final Class<?> type) {
    return new FilterInfo() {
      @Override
      public Class<?> serialClass() {
        return type;
      }

      @Override
      public long arrayLength() {
        return -1;
      }

      @Override
      public long depth() {
        return 1;
      }

      @Override
      public long references() {
        return 1;
      }

      @Override
      public long streamBytes() {
        return 1;
      }
    };
  }

  // toJSONObject() returns a net.minidev.json.JSONObject, as the metadata of a real provider
  // discovery does. dist does not declare json-smart, so a direct call would fail the dependency
  // analysis, and the method is called reflectively instead.
  @SuppressWarnings("unchecked")
  private static Map<String, Object> discoveryMetadata() throws Exception {
    final var metadata = OIDCProviderMetadata.parse(DISCOVERY_DOCUMENT);
    return (Map<String, Object>)
        OIDCProviderMetadata.class.getMethod("toJSONObject").invoke(metadata);
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
