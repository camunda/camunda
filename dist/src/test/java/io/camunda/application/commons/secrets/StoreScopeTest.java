/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.secrets;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.configuration.Secrets.AwsSecretsManagerStore;
import io.camunda.configuration.Secrets.AzureKeyVaultStore;
import io.camunda.configuration.Secrets.EnvStore;
import io.camunda.configuration.Secrets.FileStore;
import io.camunda.configuration.Secrets.GcpSecretManagerStore;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class StoreScopeTest {

  @ParameterizedTest
  @CsvSource(
      nullValues = "-",
      value = {
        "camunda/, -, camunda/internal/, -, true",
        "camunda/internal/, -, camunda/, -, true",
        "-, -, camunda/internal/, -, true",
        "-, -, camunda/, config, true",
        "camunda/, config, camunda/, config, true",
        "-, camunda/config, camunda/, config, true",
        "camunda/jobs/, -, camunda/config/, -, false",
        "camunda/, jobs, camunda/, config, false",
        "camunda/jobs/, -, camunda/, config, false",
      })
  void shouldOverlapWhenOneStoreCanReachASecretOfTheOther(
      final String firstPrefix,
      final String firstContainer,
      final String secondPrefix,
      final String secondContainer,
      final boolean overlaps) {
    // given
    final var first = StoreScope.of(aws(firstPrefix, firstContainer, null));
    final var second = StoreScope.of(aws(secondPrefix, secondContainer, null));

    // when
    final var result = first.overlaps(second);

    // then
    assertThat(result).isEqualTo(overlaps);
  }

  @ParameterizedTest
  @CsvSource(
      nullValues = "-",
      value = {"eu-west-1, eu-west-1, true", "eu-west-1, us-east-1, false", "-, us-east-1, true"})
  void shouldOnlyOverlapOnTheSameBackend(
      final String firstRegion, final String secondRegion, final boolean overlaps) {
    // given
    final var first = StoreScope.of(aws("camunda/", null, firstRegion));
    final var second = StoreScope.of(aws("camunda/internal/", null, secondRegion));

    // when
    final var result = first.overlaps(second);

    // then
    assertThat(result).isEqualTo(overlaps);
  }

  @ParameterizedTest
  @CsvSource({
    "/etc/secrets, /etc/secrets/config, true",
    "/etc/secrets/, /etc/secrets, true",
    "/etc/secrets, /etc/secrets2, false",
    "/etc/jobs, /etc/config, false"
  })
  void shouldOverlapWhenOneDirectoryIsWithinTheOther(
      final String firstPath, final String secondPath, final boolean overlaps) {
    // given
    final var first = StoreScope.of(file(firstPath));
    final var second = StoreScope.of(file(secondPath));

    // when
    final var result = first.overlaps(second);

    // then
    assertThat(result).isEqualTo(overlaps);
  }

  @Test
  void shouldOverlapWhenADirectoryIsASymlinkIntoTheOther(@TempDir final Path directory)
      throws IOException {
    // given
    final var jobs = Files.createDirectories(directory.resolve("jobs"));
    Files.createDirectories(jobs.resolve("config"));
    final var config =
        Files.createSymbolicLink(directory.resolve("config"), jobs.resolve("config"));
    final var first = StoreScope.of(file(jobs.toString()));
    final var second = StoreScope.of(file(config.toString()));

    // when
    final var result = first.overlaps(second);

    // then
    assertThat(result).isTrue();
  }

  @Test
  void shouldOverlapWhenAMissingDirectoryIsWithinASymlinkedOne(@TempDir final Path directory)
      throws IOException {
    // given
    final var jobs = Files.createDirectories(directory.resolve("jobs"));
    final var link = Files.createSymbolicLink(directory.resolve("link"), jobs);
    final var first = StoreScope.of(file(jobs.toString()));
    final var second = StoreScope.of(file(link.resolve("config").toString()));

    // when
    final var result = first.overlaps(second);

    // then
    assertThat(result).isTrue();
  }

  @Test
  void shouldResolveSymlinksBeforeAParentReference(@TempDir final Path directory)
      throws IOException {
    // given
    final var jobs = Files.createDirectories(directory.resolve("jobs"));
    final var nested = Files.createDirectories(jobs.resolve("nested"));
    final var link = Files.createSymbolicLink(directory.resolve("link"), nested);
    final var first = StoreScope.of(file(jobs.toString()));
    final var second = StoreScope.of(file(link + "/../config"));

    // when
    final var result = first.overlaps(second);

    // then
    assertThat(result).isTrue();
  }

  @ParameterizedTest
  @CsvSource({
    "my-project, 123456789012, true",
    "123456789012, 210987654321, true",
    "my-project, other-project, false"
  })
  void shouldTreatAGcpProjectNumberAsPossiblyAnyProject(
      final String firstProject, final String secondProject, final boolean overlaps) {
    // given
    final var first = StoreScope.of(gcp(firstProject, null));
    final var second = StoreScope.of(gcp(secondProject, "camunda-internal-"));

    // when
    final var result = first.overlaps(second);

    // then
    assertThat(result).isEqualTo(overlaps);
  }

  @Test
  void shouldTreatABlankAwsRegionAsAnyRegion() {
    // given
    final var first = StoreScope.of(aws("camunda/", null, " "));
    final var second = StoreScope.of(aws("camunda/internal/", null, "eu-west-1"));

    // when
    final var result = first.overlaps(second);

    // then
    assertThat(result).isTrue();
  }

  @ParameterizedTest
  @CsvSource(
      nullValues = "-",
      value = {
        "arn:aws:secretsmanager:eu-west-1:123456789012:secret:camunda/, -, camunda/internal/, -,"
            + " true",
        "-, arn:aws:secretsmanager:eu-west-1:123456789012:secret:camunda/config, camunda/, -, true",
        "arn:aws:secretsmanager:eu-west-1:123456789012:secret:camunda/jobs/, -, camunda/config/, -,"
            + " false"
      })
  void shouldCompareAnAwsArnAsTheSecretNameItHolds(
      final String firstPrefix,
      final String firstContainer,
      final String secondPrefix,
      final String secondContainer,
      final boolean overlaps) {
    // given
    final var first = StoreScope.of(aws(firstPrefix, firstContainer, null));
    final var second = StoreScope.of(aws(secondPrefix, secondContainer, null));

    // when
    final var result = first.overlaps(second);

    // then
    assertThat(result).isEqualTo(overlaps);
  }

  @Test
  void shouldTreatABlankAwsPathPrefixAsNoPrefix() {
    // given
    final var first = StoreScope.of(aws(" ", null, null));
    final var second = StoreScope.of(aws("camunda/internal/", null, null));

    // when
    final var result = first.overlaps(second);

    // then
    assertThat(result).isTrue();
  }

  @ParameterizedTest
  @CsvSource({
    "https://vault.vault.azure.net, https://Vault.vault.azure.net/, true",
    "https://vault.vault.azure.net, https://vault.vault.azure.net:443, true",
    "https://vault.vault.azure.net, https://vault.vault.azure.net:8443, false",
    "https://vault.vault.azure.net, https://other.vault.azure.net, false"
  })
  void shouldTellAzureVaultsApartByHostAndPort(
      final String firstUrl, final String secondUrl, final boolean overlaps) {
    // given
    final var first = StoreScope.of(azure(firstUrl, "camunda-"));
    final var second = StoreScope.of(azure(secondUrl, "camunda-internal-"));

    // when
    final var result = first.overlaps(second);

    // then
    assertThat(result).isEqualTo(overlaps);
  }

  @Test
  void shouldCompareAzureSecretNamesIgnoringCase() {
    // given
    final var first = StoreScope.of(azure("https://vault.vault.azure.net", "Camunda-"));
    final var second = StoreScope.of(azure("https://vault.vault.azure.net", "camunda-internal-"));

    // when
    final var result = first.overlaps(second);

    // then
    assertThat(result).isTrue();
  }

  @ParameterizedTest
  @CsvSource({
    "APP_SECRET_, app_secret_config_, true",
    "APP_SECRET_CONFIG_, APP_SECRET_, true",
    "JOB_SECRET_, CONFIG_SECRET_, false"
  })
  void shouldCompareEnvironmentPrefixesIgnoringCase(
      final String firstPrefix, final String secondPrefix, final boolean overlaps) {
    // given
    final var first = StoreScope.of(env(firstPrefix));
    final var second = StoreScope.of(env(secondPrefix));

    // when
    final var result = first.overlaps(second);

    // then
    assertThat(result).isEqualTo(overlaps);
  }

  private static AwsSecretsManagerStore aws(
      final String prefix, final String container, final String region) {
    final var store = new AwsSecretsManagerStore();
    store.setPathPrefix(prefix);
    store.setContainerSecretId(container);
    store.setRegion(region);
    return store;
  }

  private static AzureKeyVaultStore azure(final String vaultUrl, final String prefix) {
    final var store = new AzureKeyVaultStore();
    store.setVaultUrl(vaultUrl);
    store.setPathPrefix(prefix);
    return store;
  }

  private static GcpSecretManagerStore gcp(final String projectId, final String prefix) {
    final var store = new GcpSecretManagerStore();
    store.setProjectId(projectId);
    store.setPathPrefix(prefix);
    return store;
  }

  private static FileStore file(final String path) {
    final var store = new FileStore();
    store.setPath(path);
    return store;
  }

  private static EnvStore env(final String prefix) {
    final var store = new EnvStore();
    store.setPrefix(prefix);
    return store;
  }
}
