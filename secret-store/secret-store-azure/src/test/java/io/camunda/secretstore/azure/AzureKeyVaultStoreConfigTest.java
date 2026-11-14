/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.secretstore.azure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Duration;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

class AzureKeyVaultStoreConfigTest {

  @Test
  void shouldDefaultEverySettingButTheVaultUrl() {
    // when
    final var config = AzureKeyVaultStoreConfig.of("https://my-vault.vault.azure.net");

    // then
    assertThat(config.vaultUrl()).isEqualTo("https://my-vault.vault.azure.net");
    assertThat(config.pathPrefix()).isNull();
    assertThat(config.containerSecretId()).isNull();
    assertThat(config.managedIdentityClientId()).isNull();
    assertThat(config.authorityHost()).isNull();
    assertThat(config.maxRetries()).isEqualTo(3);
    assertThat(config.callTimeout()).isEqualTo(Duration.ofSeconds(5));
    assertThat(config.attemptTimeout()).isEqualTo(Duration.ofSeconds(2));
  }

  @TestFactory
  Stream<DynamicTest> shouldRefuseASettingThatCanNeverWork() {
    return Stream.of(
            refused("a missing vault URL", s -> s.vaultUrl = null, "vaultUrl must be set"),
            refused("a blank vault URL", s -> s.vaultUrl = " ", "vaultUrl must be set"),
            refused(
                "an http vault URL",
                s -> s.vaultUrl = "http://my-vault.vault.azure.net",
                "Azure Key Vault requires HTTPS"),
            refused(
                "a relative vault URL",
                s -> s.vaultUrl = "my-vault.vault.azure.net",
                "vaultUrl must be an absolute https URL"),
            refused(
                "a malformed vault URL",
                s -> s.vaultUrl = "https://my vault",
                "vaultUrl must be an absolute https URL"),
            refused(
                "a vault URL without a host",
                s -> s.vaultUrl = "https:///",
                "vaultUrl must be an absolute https URL"),
            refused(
                "a vault URL whose host has an underscore",
                s -> s.vaultUrl = "https://my_vault.vault.azure.net",
                "vaultUrl must have a host name of only letters, digits, hyphens and dots"),
            refused(
                "credentials before a host with an underscore",
                s -> s.vaultUrl = "https://user:pass@my_vault.vault.azure.net",
                "vaultUrl must have a host name of only letters, digits, hyphens and dots"),
            refused(
                "a vault URL with credentials",
                s -> s.vaultUrl = "https://user:pass@my-vault.vault.azure.net",
                "vaultUrl must be an absolute https URL with only a host and optional port"),
            refused(
                "a vault URL with a path",
                s -> s.vaultUrl = "https://my-vault.vault.azure.net/secrets",
                "vaultUrl must be an absolute https URL with only a host and optional port"),
            refused(
                "a vault URL with a query",
                s -> s.vaultUrl = "https://my-vault.vault.azure.net?api-version=7.4",
                "vaultUrl must be an absolute https URL with only a host and optional port"),
            refused(
                "a path prefix with a slash",
                s -> s.pathPrefix = "camunda/",
                "pathPrefix must contain only [0-9A-Za-z-] to form valid Key Vault secret names,"
                    + " but has an invalid character at index 7"),
            refused(
                "a path prefix with an underscore",
                s -> s.pathPrefix = "camunda_",
                "pathPrefix must contain only [0-9A-Za-z-]"),
            refused(
                "a path prefix leaving no room for a name",
                s -> s.pathPrefix = "a".repeat(127),
                "pathPrefix must be at most 126 characters so a secret name fits after it, but was"
                    + " 127"),
            refused(
                "an empty container secret",
                s -> s.containerSecretId = "",
                "containerSecretId must not be empty"),
            refused(
                "an empty container secret behind a prefix",
                s -> {
                  s.pathPrefix = "camunda-";
                  s.containerSecretId = "";
                },
                "containerSecretId must not be empty"),
            refused(
                "a container secret outside the name charset",
                s -> s.containerSecretId = "all.secrets",
                "containerSecretId must contain only [0-9A-Za-z-] to form valid Key Vault secret"
                    + " names, but has an invalid character at index 3"),
            refused(
                "a container secret too long once prefixed",
                s -> {
                  s.pathPrefix = "a".repeat(100);
                  s.containerSecretId = "b".repeat(28);
                },
                "pathPrefix + containerSecretId must be at most 127 characters to form a Key Vault"
                    + " secret name, but was 128"),
            refused(
                "a blank managed identity client id",
                s -> s.managedIdentityClientId = " ",
                "managedIdentityClientId must not be blank"),
            refused(
                "a blank authority host",
                s -> s.authorityHost = " ",
                "authorityHost must not be blank"),
            refused(
                "an authority host whose host has an underscore",
                s -> s.authorityHost = "https://login_host.microsoftonline.us",
                "authorityHost must have a host name of only letters, digits, hyphens and dots"),
            refused(
                "an http authority host",
                s -> s.authorityHost = "http://login.microsoftonline.us",
                "authorityHost must be an absolute https URL"),
            refused("negative retries", s -> s.maxRetries = -1, "maxRetries must not be negative"),
            refused(
                "a missing call timeout",
                s -> s.callTimeout = null,
                "callTimeout must be positive"),
            refused(
                "a zero call timeout",
                s -> s.callTimeout = Duration.ZERO,
                "callTimeout must be positive"),
            refused(
                "a missing attempt timeout",
                s -> s.attemptTimeout = null,
                "attemptTimeout must be positive"),
            refused(
                "a negative attempt timeout",
                s -> s.attemptTimeout = Duration.ofSeconds(-1),
                "attemptTimeout must be positive"),
            refused(
                "an attempt timeout longer than the call timeout",
                s -> s.attemptTimeout = Duration.ofSeconds(6),
                "attemptTimeout must not be longer than callTimeout"))
        .map(
            refused ->
                DynamicTest.dynamicTest(
                    refused.description(),
                    () ->
                        assertThatIllegalArgumentException()
                            .isThrownBy(() -> build(refused.change()))
                            .withMessageContaining(refused.message())));
  }

  @Test
  void shouldNotRepeatTheRefusedValueInTheMessage() {
    // given a value that may be a credential pasted into the wrong setting
    final Consumer<Settings> settings =
        s -> s.vaultUrl = "https://app:s3cr3t@my-vault.vault.azure.net/?key=s3cr3t";

    // when / then
    assertThatIllegalArgumentException()
        .isThrownBy(() -> build(settings))
        .withMessageNotContaining("s3cr3t")
        .withNoCause();
  }

  @Test
  void shouldNotRepeatAMalformedValueInTheMessage() {
    // given
    final Consumer<Settings> settings = s -> s.vaultUrl = "https://s3cr3t value";

    // when / then
    assertThatIllegalArgumentException()
        .isThrownBy(() -> build(settings))
        .withMessageNotContaining("s3cr3t")
        .withNoCause();
  }

  @Test
  void shouldAcceptAVaultUrlWithATrailingSlashAndAPort() {
    // given the form the Azure portal shows, and an explicit port
    final Consumer<Settings> settings =
        s -> {
          s.vaultUrl = "https://my-vault.vault.azure.net:443/";
          s.authorityHost = "https://login.microsoftonline.com/";
        };

    // when / then
    assertThatCode(() -> build(settings)).doesNotThrowAnyException();
  }

  @Test
  void shouldAcceptASovereignCloudVaultAndAuthorityHost() {
    // given a vault and authority host outside the public cloud, with every setting at its limit
    final Consumer<Settings> settings =
        s -> {
          s.vaultUrl = "https://x.vault.usgovcloudapi.net";
          s.authorityHost = "https://login.microsoftonline.us";
          s.pathPrefix = "a".repeat(100);
          s.containerSecretId = "b".repeat(27);
          s.managedIdentityClientId = "client-id";
          s.maxRetries = 0;
          s.attemptTimeout = Duration.ofSeconds(5);
        };

    // when / then
    assertThatCode(() -> build(settings)).doesNotThrowAnyException();
  }

  @Test
  void shouldAcceptThePathPrefixLengthThatLeavesOneCharacterForTheName() {
    // given the longest prefix a Key Vault secret name can still hold one character after
    final Consumer<Settings> settings = s -> s.pathPrefix = "a".repeat(126);

    // when / then
    assertThatCode(() -> build(settings)).doesNotThrowAnyException();
  }

  private static Refused refused(
      final String description, final Consumer<Settings> change, final String message) {
    return new Refused(description, change, message);
  }

  private static AzureKeyVaultStoreConfig build(final Consumer<Settings> change) {
    final var settings = new Settings();
    change.accept(settings);
    return settings.build();
  }

  private record Refused(String description, Consumer<Settings> change, String message) {}

  /** Starts valid and holds every setting unchecked, so a test can hand any combination over. */
  private static final class Settings {
    @Nullable String vaultUrl = "https://my-vault.vault.azure.net";
    @Nullable String pathPrefix;
    @Nullable String containerSecretId;
    @Nullable String managedIdentityClientId;
    @Nullable String authorityHost;
    int maxRetries = AzureKeyVaultStoreConfig.DEFAULT_MAX_RETRIES;
    @Nullable Duration callTimeout = AzureKeyVaultStoreConfig.DEFAULT_CALL_TIMEOUT;
    @Nullable Duration attemptTimeout = AzureKeyVaultStoreConfig.DEFAULT_ATTEMPT_TIMEOUT;

    AzureKeyVaultStoreConfig build() {
      return new AzureKeyVaultStoreConfig(
          vaultUrl,
          pathPrefix,
          containerSecretId,
          managedIdentityClientId,
          authorityHost,
          maxRetries,
          callTimeout,
          attemptTimeout);
    }
  }
}
