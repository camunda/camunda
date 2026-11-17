/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.secrets;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.configuration.Secrets.AzureKeyVaultStore;
import io.camunda.secretstore.azure.AzureKeyVaultStoreConfig;
import java.time.Duration;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The configuration module validates an Azure store with property names in its messages, and the
 * store module validates the record it builds for any other caller. Neither module can depend on
 * the other, so this test, in the module that depends on both, feeds them the same settings: when
 * one accepts what the other refuses, an operator gets a generic message, or none, for a setting
 * that cannot work.
 */
class AzureStoreValidationParityTest {

  @ParameterizedTest(name = "{0}")
  @MethodSource("settings")
  void shouldRefuseTheSameSettingsInBothModules(
      final String description, final Consumer<AzureKeyVaultStore> change, final boolean valid) {
    // given
    final var store = new AzureKeyVaultStore();
    store.setVaultUrl("https://my-vault.vault.azure.net");
    change.accept(store);

    // when
    final var refusedByConfiguration = refuses(() -> store.validate("store"));
    final var refusedByStoreModule = refuses(() -> toStoreConfig(store));

    // then
    assertThat(refusedByConfiguration).as("refused by the configuration module").isEqualTo(!valid);
    assertThat(refusedByStoreModule).as("refused by the store module").isEqualTo(!valid);
  }

  static Stream<Arguments> settings() {
    return Stream.of(
        valid("only a vault URL", s -> {}),
        valid("a trailing slash and a port", s -> s.setVaultUrl("https://v.vault.azure.net:443/")),
        valid(
            "every setting at its limit",
            s -> {
              s.setAuthorityHost("https://login.microsoftonline.us");
              s.setPathPrefix("a".repeat(100));
              s.setContainerSecretId("b".repeat(27));
              s.setManagedIdentityClientId("client-id");
              s.setMaxRetries(0);
              s.setAttemptTimeout(Duration.ofSeconds(5));
            }),
        valid("a call timeout below the default attempt timeout", s -> s.setCallTimeout(ms(500))),
        invalid("an http vault URL", s -> s.setVaultUrl("http://v.vault.azure.net")),
        invalid("a relative vault URL", s -> s.setVaultUrl("v.vault.azure.net")),
        invalid("a malformed vault URL", s -> s.setVaultUrl("https://my vault")),
        invalid("a vault URL without a host", s -> s.setVaultUrl("https:///")),
        invalid(
            "an underscore in the host", s -> s.setVaultUrl("https://my_vault.vault.azure.net")),
        invalid("credentials", s -> s.setVaultUrl("https://u:p@v.vault.azure.net")),
        invalid("a path", s -> s.setVaultUrl("https://v.vault.azure.net/secrets")),
        invalid("a query", s -> s.setVaultUrl("https://v.vault.azure.net?api-version=7.4")),
        invalid("a fragment", s -> s.setVaultUrl("https://v.vault.azure.net#f")),
        invalid("an http authority host", s -> s.setAuthorityHost("http://login.microsoft.us")),
        invalid("a blank authority host", s -> s.setAuthorityHost(" ")),
        invalid("a blank managed identity", s -> s.setManagedIdentityClientId(" ")),
        invalid("a blank container secret", s -> s.setContainerSecretId(" ")),
        invalid("a path prefix with a slash", s -> s.setPathPrefix("camunda/")),
        invalid("a path prefix leaving no room", s -> s.setPathPrefix("a".repeat(127))),
        invalid("a container secret with a dot", s -> s.setContainerSecretId("all.secrets")),
        invalid(
            "a container secret too long once prefixed",
            s -> {
              s.setPathPrefix("a".repeat(100));
              s.setContainerSecretId("b".repeat(28));
            }),
        invalid("negative retries", s -> s.setMaxRetries(-1)),
        invalid("a zero call timeout", s -> s.setCallTimeout(Duration.ZERO)),
        invalid("a negative attempt timeout", s -> s.setAttemptTimeout(ms(-1))),
        invalid(
            "an attempt timeout longer than the call timeout",
            s -> s.setAttemptTimeout(Duration.ofSeconds(6))));
  }

  private static Arguments valid(
      final String description, final Consumer<AzureKeyVaultStore> change) {
    return Arguments.of(description, change, true);
  }

  private static Arguments invalid(
      final String description, final Consumer<AzureKeyVaultStore> change) {
    return Arguments.of(description, change, false);
  }

  private static Duration ms(final long millis) {
    return Duration.ofMillis(millis);
  }

  /** Builds the record the way {@code SecretStoreConfiguration#azureStore} does. */
  private static AzureKeyVaultStoreConfig toStoreConfig(final AzureKeyVaultStore store) {
    return new AzureKeyVaultStoreConfig(
        store.getVaultUrl(),
        store.getPathPrefix(),
        store.getContainerSecretId(),
        store.getManagedIdentityClientId(),
        store.getAuthorityHost(),
        store.getMaxRetries(),
        store.getCallTimeout(),
        store.getAttemptTimeout());
  }

  private static boolean refuses(final Runnable validation) {
    try {
      validation.run();
      return false;
    } catch (final IllegalArgumentException e) {
      return true;
    }
  }
}
