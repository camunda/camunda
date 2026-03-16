/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gatekeeper.spring.unit.oidc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nimbusds.jose.jwk.RSAKey;
import io.camunda.gatekeeper.config.AssertionConfig;
import io.camunda.gatekeeper.config.AuthenticationConfig;
import io.camunda.gatekeeper.config.OidcConfig;
import io.camunda.gatekeeper.model.identity.AuthenticationMethod;
import io.camunda.gatekeeper.spring.oidc.AssertionJwkProvider;
import io.camunda.gatekeeper.spring.oidc.OidcAuthenticationConfigurationRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class AssertionJwkProviderTest {

  private static Path keystorePath;

  @BeforeAll
  static void createTestKeystore() throws Exception {
    keystorePath = Files.createTempFile("test-keystore-", ".p12");
    Files.delete(keystorePath);
    final var process =
        new ProcessBuilder(
                "keytool",
                "-genkeypair",
                "-alias",
                "test-key",
                "-keyalg",
                "RSA",
                "-keysize",
                "2048",
                "-validity",
                "1",
                "-keystore",
                keystorePath.toString(),
                "-storetype",
                "PKCS12",
                "-storepass",
                "changeit",
                "-keypass",
                "changeit",
                "-dname",
                "CN=Test")
            .redirectErrorStream(true)
            .start();
    process.waitFor();
  }

  @Test
  void createJwkReturnsValidRsaKey() throws Exception {
    final var provider = createProvider(buildAssertionConfig(null, null, null));

    final var jwk = provider.createJwk("oidc");

    assertThat(jwk).isInstanceOf(RSAKey.class);
    final var rsaKey = (RSAKey) jwk;
    assertThat(rsaKey.getKeyID()).isNotNull().isNotBlank();
    assertThat(rsaKey.toRSAPrivateKey()).isNotNull();
    assertThat(rsaKey.getX509CertChain()).isNotEmpty();
  }

  @Test
  void createJwkThrowsForUnknownRegistrationId() {
    final var provider = createProvider(buildAssertionConfig(null, null, null));

    assertThatThrownBy(() -> provider.createJwk("unknown"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("No OIDC configuration found for registration ID: unknown");
  }

  @Test
  void createJwkThrowsWhenAssertionNotConfigured() {
    final var oidcConfig = buildOidcConfig("no-assertion-reg", null);
    final var authConfig =
        new AuthenticationConfig(
            AuthenticationMethod.OIDC, Duration.ofSeconds(30), false, oidcConfig);
    final var repo = new OidcAuthenticationConfigurationRepository(authConfig);
    final var provider = new AssertionJwkProvider(repo);

    assertThatThrownBy(() -> provider.createJwk("oidc"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("No assertion/keystore configuration found");
  }

  @Test
  void createJwkWithHexEncodingAndUpperCase() {
    final var provider =
        createProvider(
            buildAssertionConfig(
                AssertionConfig.KidEncoding.HEX, AssertionConfig.KidCase.UPPER, null));

    final var jwk = provider.createJwk("oidc");

    final var rsaKey = (RSAKey) jwk;
    assertThat(rsaKey.getKeyID()).isNotNull().matches("[0-9A-F]+");
  }

  private static AssertionJwkProvider createProvider(final AssertionConfig assertionConfig) {
    final var oidcConfig = buildOidcConfig("oidc", assertionConfig);
    final var authConfig =
        new AuthenticationConfig(
            AuthenticationMethod.OIDC, Duration.ofSeconds(30), false, oidcConfig);
    final var repo = new OidcAuthenticationConfigurationRepository(authConfig);
    return new AssertionJwkProvider(repo);
  }

  private static OidcConfig buildOidcConfig(
      final String registrationId, final AssertionConfig assertionConfig) {
    return new OidcConfig(
        "https://issuer.example.com",
        "client-id",
        "client-secret",
        null,
        List.of(),
        null,
        null,
        null,
        "sub",
        null,
        null,
        false,
        null,
        List.of(),
        null,
        Duration.ofSeconds(60),
        true,
        "authorization_code",
        "private_key_jwt",
        registrationId,
        assertionConfig);
  }

  private static AssertionConfig buildAssertionConfig(
      final AssertionConfig.KidEncoding encoding,
      final AssertionConfig.KidCase kidCase,
      final AssertionConfig.KidDigestAlgorithm digestAlgorithm) {
    return new AssertionConfig(
        keystorePath.toString(),
        "changeit",
        "test-key",
        "changeit",
        AssertionConfig.KidSource.PUBLIC_KEY,
        digestAlgorithm,
        encoding,
        kidCase);
  }
}
