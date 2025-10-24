/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.config;

import static org.junit.jupiter.params.provider.Arguments.*;

import io.camunda.security.configuration.AssertionConfiguration;
import io.camunda.security.configuration.AssertionConfiguration.KidCase;
import io.camunda.security.configuration.AssertionConfiguration.KidDigestAlgorithm;
import io.camunda.security.configuration.AssertionConfiguration.KidEncoding;
import io.camunda.security.configuration.AssertionConfiguration.KidSource;
import io.camunda.security.configuration.KeystoreConfiguration;
import java.lang.reflect.Method;
import java.security.cert.Certificate;
import java.util.stream.Stream;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mockito;

@SuppressWarnings("DataFlowIssue")
class AssertionJwkProviderTest {

  @Test
  void testGenerateDefaultKid() throws Exception {
    final var mockRepository = Mockito.mock(OidcAuthenticationConfigurationRepository.class);
    final var assertionJwkProvider = new AssertionJwkProvider(mockRepository);
    final var keystorePath =
        AssertionJwkProviderTest.class.getClassLoader().getResource("keystore.p12").getPath();
    final var keystoreConfig =
        KeystoreConfiguration.builder()
            .path(keystorePath)
            .password("password")
            .keyAlias("camunda-standalone")
            .keyPassword("password")
            .build();
    final var config =
        AssertionConfiguration.builder().keystoreConfiguration(keystoreConfig).build();
    final var cert = keystoreConfig.loadKeystore().getCertificate(keystoreConfig.getKeyAlias());

    final Method generateKid =
        AssertionJwkProvider.class.getDeclaredMethod(
            "generateKid", Certificate.class, AssertionConfiguration.class);
    generateKid.setAccessible(true);

    final var kid = (String) generateKid.invoke(assertionJwkProvider, cert, config);
    Assertions.assertThat(kid).isEqualTo("opaYc1PqzH6XYGbL3KF4BK1rkNRS4IuMAfh3qPZILHo");
  }

  @ParameterizedTest(name = "{index}: {0} {1} {2} {3}")
  @MethodSource("kidGenerationSettings")
  void testGenerateKid(
      final KidSource kidSource,
      final KidDigestAlgorithm kidDigestAlgorithm,
      final KidEncoding kidEncoding,
      final KidCase kidCase,
      final String expectedKid)
      throws Exception {
    final var mockRepository = Mockito.mock(OidcAuthenticationConfigurationRepository.class);
    final var assertionJwkProvider = new AssertionJwkProvider(mockRepository);
    final var keystorePath =
        AssertionJwkProviderTest.class.getClassLoader().getResource("keystore.p12").getPath();
    final var keystoreConfig =
        KeystoreConfiguration.builder()
            .path(keystorePath)
            .password("password")
            .keyAlias("camunda-standalone")
            .keyPassword("password")
            .build();
    final var config =
        AssertionConfiguration.builder()
            .keystoreConfiguration(keystoreConfig)
            .kidSource(kidSource)
            .kidDigestAlgorithm(kidDigestAlgorithm)
            .kidEncoding(kidEncoding)
            .kidCase(kidCase)
            .build();
    final var cert = keystoreConfig.loadKeystore().getCertificate(keystoreConfig.getKeyAlias());

    final Method generateKid =
        AssertionJwkProvider.class.getDeclaredMethod(
            "generateKid", Certificate.class, AssertionConfiguration.class);
    generateKid.setAccessible(true);

    final var kid = (String) generateKid.invoke(assertionJwkProvider, cert, config);
    Assertions.assertThat(kid).isEqualTo(expectedKid);
  }

  static Stream<Arguments> kidGenerationSettings() {
    return Stream.of(
        of(
            KidSource.CERTIFICATE,
            KidDigestAlgorithm.SHA1,
            KidEncoding.BASE64URL,
            null,
            "3qC6yDtfSqnrgI1SgvyrAxcILBI"),
        of(
            KidSource.CERTIFICATE,
            KidDigestAlgorithm.SHA1,
            KidEncoding.BASE64URL,
            null,
            "3qC6yDtfSqnrgI1SgvyrAxcILBI"),
        of(
            KidSource.CERTIFICATE,
            KidDigestAlgorithm.SHA1,
            KidEncoding.HEX,
            KidCase.UPPER,
            "DEA0BAC83B5F4AA9EB808D5282FCAB0317082C12"),
        of(
            KidSource.CERTIFICATE,
            KidDigestAlgorithm.SHA1,
            KidEncoding.HEX,
            KidCase.LOWER,
            "dea0bac83b5f4aa9eb808d5282fcab0317082c12"),
        of(
            KidSource.CERTIFICATE,
            KidDigestAlgorithm.SHA256,
            KidEncoding.BASE64URL,
            null,
            "gCC_MwKDLUCxMYUlm95bDX8ol6nNHhCohhudSkJAJhQ"),
        of(
            KidSource.CERTIFICATE,
            KidDigestAlgorithm.SHA256,
            KidEncoding.BASE64URL,
            null,
            "gCC_MwKDLUCxMYUlm95bDX8ol6nNHhCohhudSkJAJhQ"),
        of(
            KidSource.CERTIFICATE,
            KidDigestAlgorithm.SHA256,
            KidEncoding.HEX,
            KidCase.UPPER,
            "8020BF3302832D40B13185259BDE5B0D7F2897A9CD1E10A8861B9D4A42402614"),
        of(
            KidSource.CERTIFICATE,
            KidDigestAlgorithm.SHA256,
            KidEncoding.HEX,
            KidCase.LOWER,
            "8020bf3302832d40b13185259bde5b0d7f2897a9cd1e10a8861b9d4a42402614"),
        of(
            KidSource.PUBLIC_KEY,
            KidDigestAlgorithm.SHA1,
            KidEncoding.BASE64URL,
            null,
            "c3_39mARI3tpCxRcmhiGylohUYQ"),
        of(
            KidSource.PUBLIC_KEY,
            KidDigestAlgorithm.SHA1,
            KidEncoding.BASE64URL,
            null,
            "c3_39mARI3tpCxRcmhiGylohUYQ"),
        of(
            KidSource.PUBLIC_KEY,
            KidDigestAlgorithm.SHA1,
            KidEncoding.HEX,
            KidCase.UPPER,
            "737FF7F66011237B690B145C9A1886CA5A215184"),
        of(
            KidSource.PUBLIC_KEY,
            KidDigestAlgorithm.SHA1,
            KidEncoding.HEX,
            KidCase.LOWER,
            "737ff7f66011237b690b145c9a1886ca5a215184"),
        of(
            KidSource.PUBLIC_KEY,
            KidDigestAlgorithm.SHA256,
            KidEncoding.BASE64URL,
            null,
            "opaYc1PqzH6XYGbL3KF4BK1rkNRS4IuMAfh3qPZILHo"),
        of(
            KidSource.PUBLIC_KEY,
            KidDigestAlgorithm.SHA256,
            KidEncoding.BASE64URL,
            null,
            "opaYc1PqzH6XYGbL3KF4BK1rkNRS4IuMAfh3qPZILHo"),
        of(
            KidSource.PUBLIC_KEY,
            KidDigestAlgorithm.SHA256,
            KidEncoding.HEX,
            KidCase.UPPER,
            "A296987353EACC7E976066CBDCA17804AD6B90D452E08B8C01F877A8F6482C7A"),
        of(
            KidSource.PUBLIC_KEY,
            KidDigestAlgorithm.SHA256,
            KidEncoding.HEX,
            KidCase.LOWER,
            "a296987353eacc7e976066cbdca17804ad6b90d452e08b8c01f877a8f6482c7a"));
  }
}
