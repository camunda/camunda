/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.config;

import io.camunda.security.configuration.AssertionKeystoreConfiguration;
import io.camunda.security.configuration.AuthorizeRequestConfiguration;
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
    Assertions.assertThat(oidcAuthenticationConfiguration.isSet()).isEqualTo(expected);
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
            "clientId is set",
            OidcAuthenticationConfiguration.builder().clientId("cid").build(),
            true),
        Arguments.of(
            "clientSecret is set",
            OidcAuthenticationConfiguration.builder().clientSecret("cs").build(),
            true),
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
            "AssertionKeystoreConfiguration.path is set",
            OidcAuthenticationConfiguration.builder()
                .assertionKeystoreConfiguration(
                    AssertionKeystoreConfiguration.builder().path("/path/to/keystore.p12").build())
                .build(),
            true),
        Arguments.of(
            "AssertionKeystoreConfiguration.password is set",
            OidcAuthenticationConfiguration.builder()
                .assertionKeystoreConfiguration(
                    AssertionKeystoreConfiguration.builder().password("keystorepass").build())
                .build(),
            true),
        Arguments.of(
            "AssertionKeystoreConfiguration.keyAlias is set",
            OidcAuthenticationConfiguration.builder()
                .assertionKeystoreConfiguration(
                    AssertionKeystoreConfiguration.builder().keyAlias("alias").build())
                .build(),
            true),
        Arguments.of(
            "AssertionKeystoreConfiguration.keyPassword is set",
            OidcAuthenticationConfiguration.builder()
                .assertionKeystoreConfiguration(
                    AssertionKeystoreConfiguration.builder().keyPassword("keypass").build())
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
            "AssertionKeystoreConfiguration values are not set",
            OidcAuthenticationConfiguration.builder()
                .assertionKeystoreConfiguration(AssertionKeystoreConfiguration.builder().build())
                .build(),
            false));
  }
}
