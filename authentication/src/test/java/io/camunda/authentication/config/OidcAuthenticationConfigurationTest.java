/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.config;

import static io.camunda.security.configuration.OidcAuthenticationConfiguration.DEFAULT_CLOCK_SKEW;

import io.camunda.security.configuration.AssertionConfiguration;
import io.camunda.security.configuration.AssertionConfiguration.KidDigestAlgorithm;
import io.camunda.security.configuration.AssertionConfiguration.KidEncoding;
import io.camunda.security.configuration.AuthorizeRequestConfiguration;
import io.camunda.security.configuration.KeystoreConfiguration;
import io.camunda.security.configuration.OidcAuthenticationConfiguration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class OidcAuthenticationConfigurationTest {

  @ParameterizedTest(name = "{index}: {0}")
  @MethodSource("oidcAuthentications")
  @DisplayName("Check OIDC configuration is set")
  void testIsSet(
      final String description,
      final OidcAuthenticationConfiguration oidcAuthenticationConfiguration,
      final boolean expected) {
    Assertions.assertThat(oidcAuthenticationConfiguration.isSet())
        .withFailMessage(description)
        .isEqualTo(expected);
  }

  static Stream<Arguments> oidcAuthentications() {
    return Stream.of(
        Arguments.of(
            "audience is set",
            OidcAuthenticationConfiguration.builder().audiences(Set.of("aud")).build(),
            true),
        Arguments.of(
            "authorizationUri is set",
            OidcAuthenticationConfiguration.builder().authorizationUri("auth-uri").build(),
            true),
        Arguments.of(
            "endSessionEndpointUri is set",
            OidcAuthenticationConfiguration.builder()
                .endSessionEndpointUri("end-session-endpoint-uri")
                .build(),
            true),
        Arguments.of(
            "clientId is set",
            OidcAuthenticationConfiguration.builder().clientId("cid").build(),
            true),
        Arguments.of(
            "clientName is set",
            OidcAuthenticationConfiguration.builder().clientName("clientName").build(),
            true),
        Arguments.of(
            "clientSecret is set",
            OidcAuthenticationConfiguration.builder().clientSecret("cs").build(),
            true),
        Arguments.of(
            "idTokenAlgorithm is set",
            OidcAuthenticationConfiguration.builder().idTokenAlgorithm("PS256").build(),
            true),
        Arguments.of(
            "default idTokenAlgorithm is set",
            OidcAuthenticationConfiguration.builder().idTokenAlgorithm("RS256").build(),
            false),
        Arguments.of(
            "authorizeRequestConfiguration is set to null",
            OidcAuthenticationConfiguration.builder().authorizeRequestConfiguration(null).build(),
            true),
        Arguments.of(
            "authorizeRequestConfiguration is set to null",
            OidcAuthenticationConfiguration.builder()
                .authorizeRequestConfiguration(
                    AuthorizeRequestConfiguration.builder().additionalParameter("k1", "v1").build())
                .build(),
            true),
        Arguments.of(
            "grantType is set to empty",
            OidcAuthenticationConfiguration.builder().grantType("").build(),
            true),
        Arguments.of(
            "grantType is set",
            OidcAuthenticationConfiguration.builder().grantType("client_credentials").build(),
            true),
        Arguments.of(
            "clientIdClaim is set",
            OidcAuthenticationConfiguration.builder().clientIdClaim("cclaim").build(),
            true),
        Arguments.of(
            "groupsClaim is set",
            OidcAuthenticationConfiguration.builder().groupsClaim("gclaim").build(),
            true),
        Arguments.of(
            "usernameClaim is set to null",
            OidcAuthenticationConfiguration.builder().usernameClaim(null).build(),
            true),
        Arguments.of(
            "usernameClaim is set empty",
            OidcAuthenticationConfiguration.builder().usernameClaim("").build(),
            true),
        Arguments.of(
            "usernameClaim is set",
            OidcAuthenticationConfiguration.builder().usernameClaim("sub1").build(),
            true),
        Arguments.of(
            "preferUsernameClaim is set",
            OidcAuthenticationConfiguration.builder().preferUsernameClaim(true).build(),
            true),
        Arguments.of(
            "preferUsernameClaim is not set",
            OidcAuthenticationConfiguration.builder().build(),
            false),
        Arguments.of(
            "issuerUri is set",
            OidcAuthenticationConfiguration.builder().issuerUri("issuer").build(),
            true),
        Arguments.of(
            "jwk-url is set",
            OidcAuthenticationConfiguration.builder().jwkSetUri("jwk-url").build(),
            true),
        Arguments.of(
            "scope is set to null",
            OidcAuthenticationConfiguration.builder().scope(null).build(),
            true),
        Arguments.of(
            "scope is set to empty",
            OidcAuthenticationConfiguration.builder().scope(new ArrayList<>()).build(),
            true),
        Arguments.of(
            "scope is set",
            OidcAuthenticationConfiguration.builder().scope(List.of("profile")).build(),
            true),
        Arguments.of(
            "redirectUri is set",
            OidcAuthenticationConfiguration.builder().redirectUri("redirect").build(),
            true),
        Arguments.of(
            "tokeUri is set",
            OidcAuthenticationConfiguration.builder().tokenUri("token").build(),
            true),
        Arguments.of(
            "organizationId is set",
            OidcAuthenticationConfiguration.builder().organizationId("org").build(),
            true),
        Arguments.of(
            "clientAuthenticationMethod is set",
            OidcAuthenticationConfiguration.builder()
                .clientAuthenticationMethod("private_key_jwt")
                .build(),
            true),
        Arguments.of(
            "AssertionConfiguration.path is set",
            OidcAuthenticationConfiguration.builder()
                .assertionConfiguration(
                    AssertionConfiguration.builder()
                        .keystoreConfiguration(
                            KeystoreConfiguration.builder().path("/path/to/keystore.p12").build())
                        .build())
                .build(),
            true),
        Arguments.of(
            "AssertionConfiguration.password is set",
            OidcAuthenticationConfiguration.builder()
                .assertionConfiguration(
                    AssertionConfiguration.builder()
                        .keystoreConfiguration(
                            KeystoreConfiguration.builder().password("keystorepass").build())
                        .build())
                .build(),
            true),
        Arguments.of(
            "AssertionConfiguration.keyAlias is set",
            OidcAuthenticationConfiguration.builder()
                .assertionConfiguration(
                    AssertionConfiguration.builder()
                        .keystoreConfiguration(
                            KeystoreConfiguration.builder().keyAlias("keyalias").build())
                        .build())
                .build(),
            true),
        Arguments.of(
            "AssertionConfiguration.keyPassword is set",
            OidcAuthenticationConfiguration.builder()
                .assertionConfiguration(
                    AssertionConfiguration.builder()
                        .keystoreConfiguration(
                            KeystoreConfiguration.builder().keyPassword("keypass").build())
                        .build())
                .build(),
            true),
        Arguments.of(
            "AssertionConfiguration.kidSource is set",
            OidcAuthenticationConfiguration.builder()
                .assertionConfiguration(
                    AssertionConfiguration.builder()
                        .kidSource(AssertionConfiguration.KidSource.CERTIFICATE)
                        .build())
                .build(),
            true),
        Arguments.of(
            "AssertionConfiguration.kidDigestAlgorithm is set",
            OidcAuthenticationConfiguration.builder()
                .assertionConfiguration(
                    AssertionConfiguration.builder()
                        .kidDigestAlgorithm(KidDigestAlgorithm.SHA1)
                        .build())
                .build(),
            true),
        Arguments.of(
            "AssertionConfiguration.kidEncoding is set",
            OidcAuthenticationConfiguration.builder()
                .assertionConfiguration(
                    AssertionConfiguration.builder().kidEncoding(KidEncoding.HEX).build())
                .build(),
            true),
        Arguments.of(
            "AssertionConfiguration.kidCase is set",
            OidcAuthenticationConfiguration.builder()
                .assertionConfiguration(
                    AssertionConfiguration.builder()
                        .kidEncoding(KidEncoding.HEX)
                        .kidCase(AssertionConfiguration.KidCase.LOWER)
                        .build())
                .build(),
            true),
        Arguments.of(
            "clockSkew is set",
            OidcAuthenticationConfiguration.builder()
                .clockSkew(DEFAULT_CLOCK_SKEW.plusSeconds(1))
                .build(),
            true),
        Arguments.of("default", new OidcAuthenticationConfiguration(), false),
        Arguments.of(
            "default authorizeRequestConfiguration is set",
            OidcAuthenticationConfiguration.builder()
                .authorizeRequestConfiguration(new AuthorizeRequestConfiguration())
                .build(),
            false),
        Arguments.of(
            "default grantType is set",
            OidcAuthenticationConfiguration.builder().grantType("authorization_code").build(),
            false),
        Arguments.of(
            "default usernameClaim is set",
            OidcAuthenticationConfiguration.builder().usernameClaim("sub").build(),
            false),
        Arguments.of(
            "default scope is set",
            OidcAuthenticationConfiguration.builder().scope(List.of("openid", "profile")).build(),
            false),
        Arguments.of(
            "default clientAuthenticationMethod is set",
            OidcAuthenticationConfiguration.builder()
                .clientAuthenticationMethod("client_secret_basic")
                .build(),
            false),
        Arguments.of(
            "default clockSkew is set",
            OidcAuthenticationConfiguration.builder().clockSkew(DEFAULT_CLOCK_SKEW).build(),
            false),
        Arguments.of(
            "AssertionConfiguration values are not set",
            OidcAuthenticationConfiguration.builder()
                .assertionConfiguration(AssertionConfiguration.builder().build())
                .build(),
            false));
  }
}
