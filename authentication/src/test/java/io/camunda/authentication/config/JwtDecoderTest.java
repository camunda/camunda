/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.config;

import static com.nimbusds.jose.JOSEObjectType.JWT;
import static io.camunda.authentication.config.OidcAccessTokenDecoderFactory.AT_JWT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSAlgorithm.Family;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.util.JSONObjectUtils;
import com.nimbusds.jwt.JWTClaimsSet.Builder;
import com.nimbusds.jwt.SignedJWT;
import io.camunda.authentication.config.controllers.WebSecurityConfigTestContext;
import io.camunda.authentication.config.controllers.WebSecurityOidcTestContext;
import java.util.Date;
import java.util.stream.Stream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

@SuppressWarnings("SpringBootApplicationProperties")
@SpringBootTest(
    classes = {
      WebSecurityConfigTestContext.class,
      WebSecurityOidcTestContext.class,
      WebSecurityConfig.class
    })
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class JwtDecoderTest extends AbstractWebSecurityConfigTest {

  abstract static class DecoderTest {
    @RegisterExtension
    static WireMockExtension wireMock =
        WireMockExtension.newInstance().configureStaticDsl(true).build();

    @Autowired private JwtDecoder decoder;

    @ParameterizedTest
    @MethodSource("basicAlgTestParameters")
    public void testDecodeJwtWithNoAlgFieldInJwkResponse(
        final JWSAlgorithm algorithm, final Curve curve) throws JOSEException {
      final JwtKeys jwtKeys = new JwtKeys(algorithm, curve, JWT);

      // remove alg field from mocked server response and assert it was removed to cover this use
      // case
      final String withoutAlg = jwtKeys.publicKey.replaceFirst(",\"alg\":\"[^\"]*\"", "");
      final String authServerResponseBody = "{\"keys\":[" + withoutAlg + "]}";
      assertThat(authServerResponseBody).doesNotContain("alg\":");
      mockAuthServerResponse(authServerResponseBody);

      assertThatCode(() -> decoder.decode(jwtKeys.serializedJwt)).doesNotThrowAnyException();
      wireMock.getRuntimeInfo().getWireMock().verifyThat(1, RequestPatternBuilder.allRequests());
    }

    private static Stream<Arguments> basicAlgTestParameters() {
      return Stream.of(
          Arguments.of(JWSAlgorithm.RS256, null), Arguments.of(JWSAlgorithm.ES256, Curve.P_256));
    }

    @ParameterizedTest
    @MethodSource("supportedJwsAlgorithms")
    public void testDecodeJwtsWithNoTypeHeader(final JWSAlgorithm algorithm, final Curve curve)
        throws JOSEException {
      final JwtKeys jwtKeys = new JwtKeys(algorithm, curve, null);
      final String authServerResponseBody = "{\"keys\":[" + jwtKeys.publicKey + "]}";
      mockAuthServerResponse(authServerResponseBody);

      assertThatCode(() -> decoder.decode(jwtKeys.serializedJwt)).doesNotThrowAnyException();
      wireMock.getRuntimeInfo().getWireMock().verifyThat(1, RequestPatternBuilder.allRequests());
    }

    @ParameterizedTest
    @MethodSource("supportedJwsAlgorithms")
    public void testDecodeJwtsWithAtJwkTypeHeader(final JWSAlgorithm algorithm, final Curve curve)
        throws JOSEException {
      final JwtKeys jwtKeys = new JwtKeys(algorithm, curve, AT_JWT);
      final String authServerResponseBody = "{\"keys\":[" + jwtKeys.publicKey + "]}";
      mockAuthServerResponse(authServerResponseBody);

      assertThatCode(() -> decoder.decode(jwtKeys.serializedJwt)).doesNotThrowAnyException();
      wireMock.getRuntimeInfo().getWireMock().verifyThat(1, RequestPatternBuilder.allRequests());
    }

    @ParameterizedTest
    @MethodSource("supportedJwsAlgorithms")
    public void testDecodeJwtWithJwtTypeHeader(final JWSAlgorithm algorithm, final Curve curve)
        throws JOSEException {
      final JwtKeys jwtKeys = new JwtKeys(algorithm, curve, JWT);
      final String authServerResponseBody = "{\"keys\":[" + jwtKeys.publicKey + "]}";
      mockAuthServerResponse(authServerResponseBody);

      assertThatCode(() -> decoder.decode(jwtKeys.serializedJwt)).doesNotThrowAnyException();
      wireMock.getRuntimeInfo().getWireMock().verifyThat(1, RequestPatternBuilder.allRequests());
    }

    private static Stream<Arguments> supportedJwsAlgorithms() {
      return Stream.of(
          Arguments.of(JWSAlgorithm.RS256, null),
          Arguments.of(JWSAlgorithm.RS384, null),
          Arguments.of(JWSAlgorithm.RS512, null),
          Arguments.of(JWSAlgorithm.ES256, Curve.P_256),
          Arguments.of(JWSAlgorithm.ES384, Curve.P_384),
          Arguments.of(JWSAlgorithm.ES512, Curve.P_521));
    }

    private void mockAuthServerResponse(final String authServerResponseBody) {
      wireMock
          .getRuntimeInfo()
          .getWireMock()
          .register(
              WireMock.get(WireMock.urlMatching(".*/protocol/openid-connect/certs"))
                  .willReturn(
                      WireMock.jsonResponse(authServerResponseBody, HttpStatus.OK.value())));
    }

    private static class JwtKeys {
      JWK jwk;
      String serializedJwt;
      String publicKey;
      JWSSigner jwsSigner;

      JwtKeys(final JWSAlgorithm algorithm, final Curve curve, final JOSEObjectType type)
          throws JOSEException {
        if (Family.RSA.contains(algorithm)) {
          jwk =
              new RSAKeyGenerator(2048)
                  .algorithm(algorithm)
                  .keyID("123")
                  .keyUse(KeyUse.SIGNATURE)
                  .generate();
          jwsSigner = new RSASSASigner((RSAKey) jwk);
        } else if (Family.EC.contains(algorithm)) {
          jwk =
              new ECKeyGenerator(curve)
                  .algorithm(algorithm)
                  .keyID("345")
                  .keyUse(KeyUse.SIGNATURE)
                  .generate();
          jwsSigner = new ECDSASigner((ECKey) jwk);
        } else {
          fail("unsupported algorithm type '%s'", algorithm);
          throw new IllegalStateException("Unreachable");
        }
        serializedJwt = signAndSerialize(jwk, algorithm, type);
        publicKey = jwk.toPublicJWK().toJSONString();
      }

      private String signAndSerialize(
          final JWK jwk, final JWSAlgorithm alg, final JOSEObjectType type) throws JOSEException {
        final SignedJWT jwt =
            new SignedJWT(
                new JWSHeader.Builder(alg).type(type).keyID(jwk.getKeyID()).build(),
                new Builder()
                    .subject("alice")
                    .issuer("http://localhost:" + wireMock.getPort() + "/foo")
                    .expirationTime(new Date(new Date().getTime() + 60 * 1000))
                    .build());
        jwt.sign(jwsSigner);
        return jwt.serialize();
      }
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        "camunda.security.authentication.unprotected-api=false",
        "camunda.security.authentication.method=oidc",
        "camunda.security.authentication.oidc.client-id=example",
        "camunda.security.authentication.oidc.redirect-uri=redirect.example.com",
        "camunda.security.authentication.oidc.authorization-uri=authorization.example.com",
        "camunda.security.authentication.oidc.token-uri=token.example.com",
      })
  class SingleOidcProviderConfiguration extends DecoderTest {

    @DynamicPropertySource
    static void registerWireMockProperties(final DynamicPropertyRegistry registry) {
      registry.add(
          "camunda.security.authentication.oidc.jwk-set-uri",
          () -> "http://localhost:" + wireMock.getPort() + "/protocol/openid-connect/certs");
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        "camunda.security.authentication.unprotected-api=false",
        "camunda.security.authentication.method=oidc",
        "camunda.security.authentication.providers.oidc.foo.client-id=foo",
        "camunda.security.authentication.providers.oidc.foo.redirect-uri=redirect.foo.com",
        "camunda.security.authentication.providers.oidc.foo.authorization-uri=authorization.foo.com",
        "camunda.security.authentication.providers.oidc.foo.token-uri=token.foo.com",
        "camunda.security.authentication.providers.oidc.bar.client-id=bar",
        "camunda.security.authentication.providers.oidc.bar.redirect-uri=redirect.bar.com",
        "camunda.security.authentication.providers.oidc.bar.authorization-uri=authorization.bar.com",
        "camunda.security.authentication.providers.oidc.bar.token-uri=token.bar.com",
      })
  class MultipleOidcProviderConfiguration extends DecoderTest {

    static final String REALM_FOO = "foo";
    static final String REALM_BAR = "bar";
    static final String OPENID_CONFIGURATION =
        "{\"issuer\": \"http://localhost:%s/%s\","
            + "\"token_endpoint\": \"token.%s.com\","
            + "\"jwks_uri\": \"http://localhost:%s/realms/%s/protocol/openid-connect/certs\","
            + "\"subject_types_supported\": [\"public\"]"
            + "}";

    @DynamicPropertySource
    static void registerWireMockProperties(final DynamicPropertyRegistry registry) {
      registry.add(
          "camunda.security.authentication.providers.oidc.foo.issuer-uri",
          () -> "http://localhost:" + wireMock.getPort() + "/" + REALM_FOO);
      final var realmFooConfiguration =
          OPENID_CONFIGURATION.formatted(
              wireMock.getPort(), REALM_FOO, REALM_FOO, wireMock.getPort(), REALM_FOO);
      mockOpenidConfigurationResponse(REALM_FOO, realmFooConfiguration);

      registry.add(
          "camunda.security.authentication.providers.oidc.bar.issuer-uri",
          () -> "http://localhost:" + wireMock.getPort() + "/" + REALM_BAR);
      final var realmBarConfiguration =
          OPENID_CONFIGURATION.formatted(
              wireMock.getPort(), REALM_BAR, REALM_BAR, wireMock.getPort(), REALM_BAR);
      mockOpenidConfigurationResponse(REALM_BAR, realmBarConfiguration);
    }

    static void mockOpenidConfigurationResponse(final String realm, final String response) {
      wireMock
          .getRuntimeInfo()
          .getWireMock()
          .register(
              WireMock.get(
                      WireMock.urlMatching(".*/" + realm + "/.well-known/openid-configuration"))
                  .willReturn(WireMock.jsonResponse(response, HttpStatus.OK.value())));
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        "camunda.security.authentication.unprotected-api=false",
        "camunda.security.authentication.method=oidc",
        "camunda.security.authentication.oidc.client-id=example",
        "camunda.security.authentication.oidc.redirect-uri=redirect.example.com",
        "camunda.security.authentication.oidc.authorization-uri=authorization.example.com",
        "camunda.security.authentication.oidc.token-uri=token.example.com",
      })
  class SingleOidcProviderWithAdditionalJwks {

    @RegisterExtension
    static WireMockExtension wireMock =
        WireMockExtension.newInstance().configureStaticDsl(true).build();

    @Autowired private JwtDecoder decoder;

    @DynamicPropertySource
    static void registerWireMockProperties(final DynamicPropertyRegistry registry) {
      final var issuerUri = "http://localhost:" + wireMock.getPort() + "/issuer";
      registry.add("camunda.security.authentication.oidc.issuer-uri", () -> issuerUri);
      registry.add(
          "camunda.security.authentication.oidc.jwk-set-uri",
          () -> "http://localhost:" + wireMock.getPort() + "/primary/jwks");
      registry.add(
          "camunda.security.authentication.oidc.additional-jwk-set-uris[0]",
          () -> "http://localhost:" + wireMock.getPort() + "/additional/jwks");

      // mock OIDC discovery endpoint so Spring can resolve the issuer during context startup
      final var openidConfig =
          "{\"issuer\": \""
              + issuerUri
              + "\","
              + "\"token_endpoint\": \"token.example.com\","
              + "\"jwks_uri\": \"http://localhost:"
              + wireMock.getPort()
              + "/primary/jwks\","
              + "\"subject_types_supported\": [\"public\"]"
              + "}";
      wireMock
          .getRuntimeInfo()
          .getWireMock()
          .register(
              WireMock.get(WireMock.urlMatching(".*/issuer/.well-known/openid-configuration"))
                  .willReturn(WireMock.jsonResponse(openidConfig, HttpStatus.OK.value())));
    }

    @Test
    void shouldDecodeTokenSignedWithPrimaryJwksKey() throws JOSEException {
      // given
      final var primaryKey =
          new RSAKeyGenerator(2048)
              .keyID("primary-a1")
              .keyUse(KeyUse.SIGNATURE)
              .algorithm(JWSAlgorithm.RS256)
              .generate();

      mockJwksEndpoint("/primary/jwks", primaryKey.toPublicJWK().toJSONString());
      mockJwksEndpoint("/additional/jwks", "{\"keys\":[]}");

      final var jwt = signAndSerialize(primaryKey, "primary-a1");

      // when // then
      assertThatCode(() -> decoder.decode(jwt)).doesNotThrowAnyException();
    }

    @Test
    void shouldDecodeTokenSignedWithAdditionalJwksKey() throws JOSEException {
      // given
      final var additionalKey =
          new RSAKeyGenerator(2048)
              .keyID("additional-b1")
              .keyUse(KeyUse.SIGNATURE)
              .algorithm(JWSAlgorithm.RS256)
              .generate();

      mockJwksEndpoint("/primary/jwks", "{\"keys\":[]}");
      mockJwksEndpoint("/additional/jwks", additionalKey.toPublicJWK().toJSONString());

      final var jwt = signAndSerialize(additionalKey, "additional-b1");

      // when // then
      assertThatCode(() -> decoder.decode(jwt)).doesNotThrowAnyException();
    }

    @Test
    void shouldFailForUnknownKid() throws JOSEException {
      // given
      final var unknownKey =
          new RSAKeyGenerator(2048)
              .keyID("unknown-kid")
              .keyUse(KeyUse.SIGNATURE)
              .algorithm(JWSAlgorithm.RS256)
              .generate();

      mockJwksEndpoint("/primary/jwks", "{\"keys\":[]}");
      mockJwksEndpoint("/additional/jwks", "{\"keys\":[]}");

      final var jwt = signAndSerialize(unknownKey, "unknown-kid");

      // when // then
      assertThatThrownBy(() -> decoder.decode(jwt)).isInstanceOf(JwtException.class);
    }

    @Test
    void shouldFallThroughToAdditionalWhenPrimaryHasNoMatchingKey() throws JOSEException {
      // given
      final var primaryKey =
          new RSAKeyGenerator(2048)
              .keyID("primary-only")
              .keyUse(KeyUse.SIGNATURE)
              .algorithm(JWSAlgorithm.RS256)
              .generate();
      final var additionalKey =
          new RSAKeyGenerator(2048)
              .keyID("additional-only")
              .keyUse(KeyUse.SIGNATURE)
              .algorithm(JWSAlgorithm.RS256)
              .generate();

      // primary exposes primaryKey, additional exposes additionalKey
      mockJwksEndpoint("/primary/jwks", primaryKey.toPublicJWK().toJSONString());
      mockJwksEndpoint("/additional/jwks", additionalKey.toPublicJWK().toJSONString());

      // JWT signed with the additional key
      final var jwt = signAndSerialize(additionalKey, "additional-only");

      // when // then
      assertThatCode(() -> decoder.decode(jwt)).doesNotThrowAnyException();
    }

    private void mockJwksEndpoint(final String path, final String keyJson) {
      final String body;
      if (keyJson.startsWith("{\"keys\"")) {
        body = keyJson;
      } else {
        body = "{\"keys\":[" + keyJson + "]}";
      }
      wireMock
          .getRuntimeInfo()
          .getWireMock()
          .register(
              WireMock.get(WireMock.urlEqualTo(path))
                  .willReturn(WireMock.jsonResponse(body, HttpStatus.OK.value())));
    }

    @Test
    void shouldDecodeTokenAfterKeyRotationAtPrimarySource() throws JOSEException {
      // given - initial key at primary
      final var initialKey =
          new RSAKeyGenerator(2048)
              .keyID("rotated-kid")
              .keyUse(KeyUse.SIGNATURE)
              .algorithm(JWSAlgorithm.RS256)
              .generate();
      mockJwksEndpoint("/primary/jwks", initialKey.toPublicJWK().toJSONString());
      mockJwksEndpoint("/additional/jwks", "{\"keys\":[]}");

      // decode succeeds with initial key
      final var jwt1 = signAndSerialize(initialKey, "rotated-kid");
      assertThatCode(() -> decoder.decode(jwt1)).doesNotThrowAnyException();

      // when - key is rotated: new key with different kid published at primary
      final var rotatedKey =
          new RSAKeyGenerator(2048)
              .keyID("rotated-kid-v2")
              .keyUse(KeyUse.SIGNATURE)
              .algorithm(JWSAlgorithm.RS256)
              .generate();
      mockJwksEndpoint("/primary/jwks", rotatedKey.toPublicJWK().toJSONString());

      final var jwt2 = signAndSerialize(rotatedKey, "rotated-kid-v2");

      // then - decoder re-fetches JWKS and succeeds
      assertThatCode(() -> decoder.decode(jwt2)).doesNotThrowAnyException();
    }

    @Test
    void shouldDecodeTokenSignedWithEcKeyFromAdditionalJwks() throws JOSEException {
      // given — EC key at additional source, primary is empty
      final var ecKey =
          new ECKeyGenerator(Curve.P_256)
              .keyID("ec-additional-kid")
              .keyUse(KeyUse.SIGNATURE)
              .algorithm(JWSAlgorithm.ES256)
              .generate();

      mockJwksEndpoint("/primary/jwks", "{\"keys\":[]}");
      mockJwksEndpoint(
          "/additional/jwks",
          JSONObjectUtils.toJSONString(new JWKSet(ecKey.toPublicJWK()).toJSONObject()));

      final var jwt =
          new SignedJWT(
              new JWSHeader.Builder(JWSAlgorithm.ES256)
                  .type(JWT)
                  .keyID("ec-additional-kid")
                  .build(),
              new Builder()
                  .subject("alice")
                  .issuer("http://localhost:" + wireMock.getPort() + "/issuer")
                  .expirationTime(new Date(new Date().getTime() + 60 * 1000))
                  .build());
      jwt.sign(new ECDSASigner(ecKey));

      // when // then
      assertThatCode(() -> decoder.decode(jwt.serialize())).doesNotThrowAnyException();
    }

    @Test
    void shouldDecodeTokenWhenPrimaryJwksReturnsServerError() throws JOSEException {
      // given — primary returns HTTP 500, additional has the key
      final var additionalKey =
          new RSAKeyGenerator(2048)
              .keyID("error-fallback-kid")
              .keyUse(KeyUse.SIGNATURE)
              .algorithm(JWSAlgorithm.RS256)
              .generate();

      // mock primary to return 500
      wireMock
          .getRuntimeInfo()
          .getWireMock()
          .register(
              WireMock.get(WireMock.urlEqualTo("/primary/jwks"))
                  .willReturn(
                      WireMock.aResponse()
                          .withStatus(HttpStatus.INTERNAL_SERVER_ERROR.value())
                          .withBody("Internal Server Error")));

      mockJwksEndpoint("/additional/jwks", additionalKey.toPublicJWK().toJSONString());

      final var jwt = signAndSerialize(additionalKey, "error-fallback-kid");

      // when // then — should fall through to additional after primary fails
      assertThatCode(() -> decoder.decode(jwt)).doesNotThrowAnyException();
    }

    @Test
    void shouldDecodeAfterKeyRotationAtAdditionalSource() throws JOSEException {
      // given — initial key at additional
      final var initialAdditionalKey =
          new RSAKeyGenerator(2048)
              .keyID("additional-rotate-v1")
              .keyUse(KeyUse.SIGNATURE)
              .algorithm(JWSAlgorithm.RS256)
              .generate();

      mockJwksEndpoint("/primary/jwks", "{\"keys\":[]}");
      mockJwksEndpoint("/additional/jwks", initialAdditionalKey.toPublicJWK().toJSONString());

      final var jwt1 = signAndSerialize(initialAdditionalKey, "additional-rotate-v1");
      assertThatCode(() -> decoder.decode(jwt1)).doesNotThrowAnyException();

      // when — rotate key at additional source
      final var rotatedAdditionalKey =
          new RSAKeyGenerator(2048)
              .keyID("additional-rotate-v2")
              .keyUse(KeyUse.SIGNATURE)
              .algorithm(JWSAlgorithm.RS256)
              .generate();
      mockJwksEndpoint("/additional/jwks", rotatedAdditionalKey.toPublicJWK().toJSONString());

      final var jwt2 = signAndSerialize(rotatedAdditionalKey, "additional-rotate-v2");

      // then — re-fetch should pick up the rotated key
      assertThatCode(() -> decoder.decode(jwt2)).doesNotThrowAnyException();
    }

    @Test
    void shouldFailWhenSameKidExistsAtBothEndpointsAndTokenSignedWithAdditionalKey()
        throws JOSEException {
      // given — both endpoints expose kid="shared-kid" but with different RSA key material.
      // This documents a known limitation of short-circuit key selection:
      // the decoder picks the primary key (wrong material) and signature verification fails.
      final var primaryKeySharedKid =
          new RSAKeyGenerator(2048)
              .keyID("shared-kid")
              .keyUse(KeyUse.SIGNATURE)
              .algorithm(JWSAlgorithm.RS256)
              .generate();
      final var additionalKeySharedKid =
          new RSAKeyGenerator(2048)
              .keyID("shared-kid")
              .keyUse(KeyUse.SIGNATURE)
              .algorithm(JWSAlgorithm.RS256)
              .generate();

      mockJwksEndpoint("/primary/jwks", primaryKeySharedKid.toPublicJWK().toJSONString());
      mockJwksEndpoint("/additional/jwks", additionalKeySharedKid.toPublicJWK().toJSONString());

      // JWT signed with the ADDITIONAL key's material, but same kid="shared-kid"
      final var jwt = signAndSerialize(additionalKeySharedKid, "shared-kid");

      // when // then — decoder fails because primary key material is selected (short-circuit)
      // and signature verification fails with the wrong key
      assertThatThrownBy(() -> decoder.decode(jwt)).isInstanceOf(JwtException.class);
    }

    @Test
    void shouldDecodeTokenWhenSameKidExistsAtBothEndpointsAndTokenSignedWithPrimaryKey()
        throws JOSEException {
      // given — same kid at both endpoints, but token signed with the PRIMARY key.
      // This succeeds because the primary key is selected first (short-circuit) and
      // it matches the signing key.
      final var primaryKeySharedKid =
          new RSAKeyGenerator(2048)
              .keyID("shared-kid-ok")
              .keyUse(KeyUse.SIGNATURE)
              .algorithm(JWSAlgorithm.RS256)
              .generate();
      final var additionalKeySharedKid =
          new RSAKeyGenerator(2048)
              .keyID("shared-kid-ok")
              .keyUse(KeyUse.SIGNATURE)
              .algorithm(JWSAlgorithm.RS256)
              .generate();

      mockJwksEndpoint("/primary/jwks", primaryKeySharedKid.toPublicJWK().toJSONString());
      mockJwksEndpoint("/additional/jwks", additionalKeySharedKid.toPublicJWK().toJSONString());

      // JWT signed with the PRIMARY key
      final var jwt = signAndSerialize(primaryKeySharedKid, "shared-kid-ok");

      // when // then — succeeds: primary key is selected and matches
      assertThatCode(() -> decoder.decode(jwt)).doesNotThrowAnyException();
    }

    private String signAndSerialize(final RSAKey key, final String kid) throws JOSEException {
      final var jwt =
          new SignedJWT(
              new JWSHeader.Builder(JWSAlgorithm.RS256).type(JWT).keyID(kid).build(),
              new Builder()
                  .subject("alice")
                  .issuer("http://localhost:" + wireMock.getPort() + "/issuer")
                  .expirationTime(new Date(new Date().getTime() + 60 * 1000))
                  .build());
      jwt.sign(new RSASSASigner(key));
      return jwt.serialize();
    }
  }

  /**
   * Regression test for <a href="https://github.com/camunda/camunda/issues/50801">#50801</a>:
   * additional-jwk-set-uris must work when issuer-uri is NOT configured (explicit endpoints only).
   */
  @Nested
  @TestPropertySource(
      properties = {
        "camunda.security.authentication.unprotected-api=false",
        "camunda.security.authentication.method=oidc",
        "camunda.security.authentication.oidc.client-id=example",
        "camunda.security.authentication.oidc.redirect-uri=redirect.example.com",
        "camunda.security.authentication.oidc.authorization-uri=authorization.example.com",
        "camunda.security.authentication.oidc.token-uri=token.example.com",
      })
  class SingleOidcProviderWithAdditionalJwksWithoutIssuerUri {

    @RegisterExtension
    static WireMockExtension wireMock =
        WireMockExtension.newInstance().configureStaticDsl(true).build();

    @Autowired private JwtDecoder decoder;

    @DynamicPropertySource
    static void registerWireMockProperties(final DynamicPropertyRegistry registry) {
      // No issuer-uri — only explicit jwk-set-uri and additional-jwk-set-uris
      registry.add(
          "camunda.security.authentication.oidc.jwk-set-uri",
          () -> "http://localhost:" + wireMock.getPort() + "/primary/jwks");
      registry.add(
          "camunda.security.authentication.oidc.additional-jwk-set-uris[0]",
          () -> "http://localhost:" + wireMock.getPort() + "/additional/jwks");
    }

    @Test
    void shouldDecodeTokenSignedWithAdditionalJwksKeyWithoutIssuerUri() throws JOSEException {
      // given
      final var additionalKey =
          new RSAKeyGenerator(2048)
              .keyID("additional-no-issuer")
              .keyUse(KeyUse.SIGNATURE)
              .algorithm(JWSAlgorithm.RS256)
              .generate();

      mockJwksEndpoint("/primary/jwks", "{\"keys\":[]}");
      mockJwksEndpoint("/additional/jwks", additionalKey.toPublicJWK().toJSONString());

      final var jwt = signAndSerialize(additionalKey, "additional-no-issuer");

      // when // then
      assertThatCode(() -> decoder.decode(jwt)).doesNotThrowAnyException();
    }

    @Test
    void shouldDecodeTokenSignedWithPrimaryJwksKeyWithoutIssuerUri() throws JOSEException {
      // given
      final var primaryKey =
          new RSAKeyGenerator(2048)
              .keyID("primary-no-issuer")
              .keyUse(KeyUse.SIGNATURE)
              .algorithm(JWSAlgorithm.RS256)
              .generate();

      mockJwksEndpoint("/primary/jwks", primaryKey.toPublicJWK().toJSONString());
      mockJwksEndpoint("/additional/jwks", "{\"keys\":[]}");

      final var jwt = signAndSerialize(primaryKey, "primary-no-issuer");

      // when // then
      assertThatCode(() -> decoder.decode(jwt)).doesNotThrowAnyException();
    }

    private void mockJwksEndpoint(final String path, final String keyJson) {
      final String body;
      if (keyJson.startsWith("{\"keys\"")) {
        body = keyJson;
      } else {
        body = "{\"keys\":[" + keyJson + "]}";
      }
      wireMock
          .getRuntimeInfo()
          .getWireMock()
          .register(
              WireMock.get(WireMock.urlEqualTo(path))
                  .willReturn(WireMock.jsonResponse(body, HttpStatus.OK.value())));
    }

    private String signAndSerialize(final RSAKey key, final String kid) throws JOSEException {
      final var jwt =
          new SignedJWT(
              new JWSHeader.Builder(JWSAlgorithm.RS256).type(JWT).keyID(kid).build(),
              new Builder()
                  .subject("alice")
                  .expirationTime(new Date(new Date().getTime() + 60 * 1000))
                  .build());
      jwt.sign(new RSASSASigner(key));
      return jwt.serialize();
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        "camunda.security.authentication.unprotected-api=false",
        "camunda.security.authentication.method=oidc",
        "camunda.security.authentication.oidc.client-id=example",
        "camunda.security.authentication.oidc.redirect-uri=redirect.example.com",
        "camunda.security.authentication.oidc.authorization-uri=authorization.example.com",
        "camunda.security.authentication.oidc.token-uri=token.example.com",
      })
  class SingleOidcProviderWithMultipleAdditionalJwks {

    @RegisterExtension
    static WireMockExtension wireMock =
        WireMockExtension.newInstance().configureStaticDsl(true).build();

    @Autowired private JwtDecoder decoder;

    @DynamicPropertySource
    static void registerWireMockProperties(final DynamicPropertyRegistry registry) {
      final var issuerUri = "http://localhost:" + wireMock.getPort() + "/issuer";
      registry.add("camunda.security.authentication.oidc.issuer-uri", () -> issuerUri);
      registry.add(
          "camunda.security.authentication.oidc.jwk-set-uri",
          () -> "http://localhost:" + wireMock.getPort() + "/primary/jwks");
      registry.add(
          "camunda.security.authentication.oidc.additional-jwk-set-uris[0]",
          () -> "http://localhost:" + wireMock.getPort() + "/additional1/jwks");
      registry.add(
          "camunda.security.authentication.oidc.additional-jwk-set-uris[1]",
          () -> "http://localhost:" + wireMock.getPort() + "/additional2/jwks");

      final var openidConfig =
          "{\"issuer\": \""
              + issuerUri
              + "\","
              + "\"token_endpoint\": \"token.example.com\","
              + "\"jwks_uri\": \"http://localhost:"
              + wireMock.getPort()
              + "/primary/jwks\","
              + "\"subject_types_supported\": [\"public\"]"
              + "}";
      wireMock
          .getRuntimeInfo()
          .getWireMock()
          .register(
              WireMock.get(WireMock.urlMatching(".*/issuer/.well-known/openid-configuration"))
                  .willReturn(WireMock.jsonResponse(openidConfig, HttpStatus.OK.value())));
    }

    @Test
    void shouldDecodeTokenWithKeyFromSecondAdditionalSource() throws JOSEException {
      // given — key only at the second additional JWKS endpoint
      final var keyAtSecondAdditional =
          new RSAKeyGenerator(2048)
              .keyID("second-additional-kid")
              .keyUse(KeyUse.SIGNATURE)
              .algorithm(JWSAlgorithm.RS256)
              .generate();

      mockJwksEndpoint("/primary/jwks", "{\"keys\":[]}");
      mockJwksEndpoint("/additional1/jwks", "{\"keys\":[]}");
      mockJwksEndpoint("/additional2/jwks", keyAtSecondAdditional.toPublicJWK().toJSONString());

      final var jwt =
          new SignedJWT(
              new JWSHeader.Builder(JWSAlgorithm.RS256)
                  .type(JWT)
                  .keyID("second-additional-kid")
                  .build(),
              new Builder()
                  .subject("alice")
                  .issuer("http://localhost:" + wireMock.getPort() + "/issuer")
                  .expirationTime(new Date(new Date().getTime() + 60 * 1000))
                  .build());
      jwt.sign(new RSASSASigner(keyAtSecondAdditional));

      // when // then
      assertThatCode(() -> decoder.decode(jwt.serialize())).doesNotThrowAnyException();
    }

    @Test
    void shouldFailWhenKeyNotAtAnySource() throws JOSEException {
      // given — key not present at any of the 3 sources
      final var unknownKey =
          new RSAKeyGenerator(2048)
              .keyID("nowhere-kid")
              .keyUse(KeyUse.SIGNATURE)
              .algorithm(JWSAlgorithm.RS256)
              .generate();

      mockJwksEndpoint("/primary/jwks", "{\"keys\":[]}");
      mockJwksEndpoint("/additional1/jwks", "{\"keys\":[]}");
      mockJwksEndpoint("/additional2/jwks", "{\"keys\":[]}");

      final var jwt =
          new SignedJWT(
              new JWSHeader.Builder(JWSAlgorithm.RS256).type(JWT).keyID("nowhere-kid").build(),
              new Builder()
                  .subject("alice")
                  .issuer("http://localhost:" + wireMock.getPort() + "/issuer")
                  .expirationTime(new Date(new Date().getTime() + 60 * 1000))
                  .build());
      jwt.sign(new RSASSASigner(unknownKey));

      // when // then
      assertThatThrownBy(() -> decoder.decode(jwt.serialize())).isInstanceOf(JwtException.class);
    }

    private void mockJwksEndpoint(final String path, final String keyJson) {
      final String body;
      if (keyJson.startsWith("{\"keys\"")) {
        body = keyJson;
      } else {
        body = "{\"keys\":[" + keyJson + "]}";
      }
      wireMock
          .getRuntimeInfo()
          .getWireMock()
          .register(
              WireMock.get(WireMock.urlEqualTo(path))
                  .willReturn(WireMock.jsonResponse(body, HttpStatus.OK.value())));
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        "camunda.security.authentication.unprotected-api=false",
        "camunda.security.authentication.method=oidc",
        "camunda.security.authentication.providers.oidc.foo.client-id=foo",
        "camunda.security.authentication.providers.oidc.foo.redirect-uri=redirect.foo.com",
        "camunda.security.authentication.providers.oidc.foo.authorization-uri=authorization.foo.com",
        "camunda.security.authentication.providers.oidc.foo.token-uri=token.foo.com",
        "camunda.security.authentication.providers.oidc.bar.client-id=bar",
        "camunda.security.authentication.providers.oidc.bar.redirect-uri=redirect.bar.com",
        "camunda.security.authentication.providers.oidc.bar.authorization-uri=authorization.bar.com",
        "camunda.security.authentication.providers.oidc.bar.token-uri=token.bar.com",
      })
  class MultipleOidcProvidersWithAdditionalJwks {

    static final String REALM_FOO = "foo";
    static final String REALM_BAR = "bar";

    @RegisterExtension
    static WireMockExtension wireMock =
        WireMockExtension.newInstance().configureStaticDsl(true).build();

    @Autowired private JwtDecoder decoder;

    @DynamicPropertySource
    static void registerWireMockProperties(final DynamicPropertyRegistry registry) {
      final var fooIssuerUri = "http://localhost:" + wireMock.getPort() + "/" + REALM_FOO;
      final var barIssuerUri = "http://localhost:" + wireMock.getPort() + "/" + REALM_BAR;

      registry.add(
          "camunda.security.authentication.providers.oidc.foo.issuer-uri", () -> fooIssuerUri);
      registry.add(
          "camunda.security.authentication.providers.oidc.bar.issuer-uri", () -> barIssuerUri);

      // additional JWKS for foo provider
      registry.add(
          "camunda.security.authentication.providers.oidc.foo.additional-jwk-set-uris[0]",
          () -> "http://localhost:" + wireMock.getPort() + "/foo-additional/jwks");

      // OIDC discovery for foo
      final var fooConfig =
          "{\"issuer\": \""
              + fooIssuerUri
              + "\","
              + "\"token_endpoint\": \"token.foo.com\","
              + "\"jwks_uri\": \"http://localhost:"
              + wireMock.getPort()
              + "/realms/foo/protocol/openid-connect/certs\","
              + "\"subject_types_supported\": [\"public\"]"
              + "}";
      mockOpenidConfigurationResponse(REALM_FOO, fooConfig);

      // OIDC discovery for bar
      final var barConfig =
          "{\"issuer\": \""
              + barIssuerUri
              + "\","
              + "\"token_endpoint\": \"token.bar.com\","
              + "\"jwks_uri\": \"http://localhost:"
              + wireMock.getPort()
              + "/realms/bar/protocol/openid-connect/certs\","
              + "\"subject_types_supported\": [\"public\"]"
              + "}";
      mockOpenidConfigurationResponse(REALM_BAR, barConfig);
    }

    @Test
    void shouldDecodeTokenFromFooPrimaryJwks() throws JOSEException {
      // given
      final var fooKey =
          new RSAKeyGenerator(2048)
              .keyID("foo-primary-kid")
              .keyUse(KeyUse.SIGNATURE)
              .algorithm(JWSAlgorithm.RS256)
              .generate();

      mockJwksEndpoint("/realms/foo/protocol/openid-connect/certs", fooKey.toPublicJWK());
      mockJwksEndpoint("/foo-additional/jwks", "{\"keys\":[]}");

      final var jwt = signAndSerialize(fooKey, "foo-primary-kid", REALM_FOO);

      // when // then
      assertThatCode(() -> decoder.decode(jwt)).doesNotThrowAnyException();
    }

    @Test
    void shouldDecodeTokenFromFooAdditionalJwks() throws JOSEException {
      // given
      final var additionalKey =
          new RSAKeyGenerator(2048)
              .keyID("foo-additional-kid")
              .keyUse(KeyUse.SIGNATURE)
              .algorithm(JWSAlgorithm.RS256)
              .generate();

      mockJwksEndpoint("/realms/foo/protocol/openid-connect/certs", "{\"keys\":[]}");
      mockJwksEndpoint("/foo-additional/jwks", additionalKey.toPublicJWK());

      final var jwt = signAndSerialize(additionalKey, "foo-additional-kid", REALM_FOO);

      // when // then
      assertThatCode(() -> decoder.decode(jwt)).doesNotThrowAnyException();
    }

    @Test
    void shouldDecodeTokenFromBarWithoutAdditionalJwks() throws JOSEException {
      // given - bar has no additional JWKS configured
      final var barKey =
          new RSAKeyGenerator(2048)
              .keyID("bar-kid")
              .keyUse(KeyUse.SIGNATURE)
              .algorithm(JWSAlgorithm.RS256)
              .generate();

      mockJwksEndpoint("/realms/bar/protocol/openid-connect/certs", barKey.toPublicJWK());

      final var jwt = signAndSerialize(barKey, "bar-kid", REALM_BAR);

      // when // then
      assertThatCode(() -> decoder.decode(jwt)).doesNotThrowAnyException();
    }

    @Test
    void shouldFailForUnknownIssuer() throws JOSEException {
      // given - token from unknown issuer
      final var unknownKey =
          new RSAKeyGenerator(2048)
              .keyID("unknown-issuer-kid")
              .keyUse(KeyUse.SIGNATURE)
              .algorithm(JWSAlgorithm.RS256)
              .generate();

      final var jwt =
          new SignedJWT(
              new JWSHeader.Builder(JWSAlgorithm.RS256)
                  .type(JWT)
                  .keyID("unknown-issuer-kid")
                  .build(),
              new Builder()
                  .subject("alice")
                  .issuer("http://localhost:" + wireMock.getPort() + "/unknown")
                  .expirationTime(new Date(new Date().getTime() + 60 * 1000))
                  .build());
      jwt.sign(new RSASSASigner(unknownKey));

      // when // then
      assertThatThrownBy(() -> decoder.decode(jwt.serialize())).isInstanceOf(JwtException.class);
    }

    private void mockJwksEndpoint(final String path, final JWK key) {
      mockJwksEndpoint(path, "{\"keys\":[" + key.toPublicJWK().toJSONString() + "]}");
    }

    private void mockJwksEndpoint(final String path, final String body) {
      final String responseBody;
      if (body.startsWith("{\"keys\"")) {
        responseBody = body;
      } else {
        responseBody = "{\"keys\":[" + body + "]}";
      }
      wireMock
          .getRuntimeInfo()
          .getWireMock()
          .register(
              WireMock.get(WireMock.urlEqualTo(path))
                  .willReturn(WireMock.jsonResponse(responseBody, HttpStatus.OK.value())));
    }

    static void mockOpenidConfigurationResponse(final String realm, final String response) {
      wireMock
          .getRuntimeInfo()
          .getWireMock()
          .register(
              WireMock.get(
                      WireMock.urlMatching(".*/" + realm + "/.well-known/openid-configuration"))
                  .willReturn(WireMock.jsonResponse(response, HttpStatus.OK.value())));
    }

    private String signAndSerialize(final RSAKey key, final String kid, final String realm)
        throws JOSEException {
      final var jwt =
          new SignedJWT(
              new JWSHeader.Builder(JWSAlgorithm.RS256).type(JWT).keyID(kid).build(),
              new Builder()
                  .subject("alice")
                  .issuer("http://localhost:" + wireMock.getPort() + "/" + realm)
                  .expirationTime(new Date(new Date().getTime() + 60 * 1000))
                  .build());
      jwt.sign(new RSASSASigner(key));
      return jwt.serialize();
    }
  }
}
