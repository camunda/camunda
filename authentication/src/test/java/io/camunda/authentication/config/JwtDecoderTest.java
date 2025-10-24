/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.config;

import static com.nimbusds.jose.JOSEObjectType.JWT;
import static io.camunda.authentication.config.WebSecurityConfig.AT_JWT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
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
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet.Builder;
import com.nimbusds.jwt.SignedJWT;
import io.camunda.authentication.config.controllers.WebSecurityConfigTestContext;
import io.camunda.authentication.config.controllers.WebSecurityOidcTestContext;
import java.util.Date;
import java.util.stream.Stream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.JwtDecoder;
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
}
