/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.secrets;

import io.camunda.configuration.Secrets.AwsSecretsManagerStore;
import io.camunda.configuration.Secrets.AzureKeyVaultStore;
import io.camunda.configuration.Secrets.EnvStore;
import io.camunda.configuration.Secrets.FileStore;
import io.camunda.configuration.Secrets.GcpSecretManagerStore;
import java.io.File;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * The secret ids a store can reach: a secret name is appended to the store's prefix, so a store
 * reaches every id that starts with its prefix, or only its container secret if it has one.
 *
 * @param backend the backend the store reads, such as an AWS region; {@code null} when it is left
 *     to the environment, so it may be the same as any other
 * @param prefix the prefix of every secret id the store reaches
 * @param container the one secret the store reads, if it reads secrets out of a container
 */
record StoreScope(@Nullable String backend, String prefix, @Nullable String container) {

  static StoreScope of(final FileStore store) {
    final var directory = Path.of(store.getPath()).toAbsolutePath().normalize().toString();
    return new StoreScope(
        null, directory.endsWith(File.separator) ? directory : directory + File.separator, null);
  }

  static StoreScope of(final EnvStore store) {
    return new StoreScope(null, store.getPrefix().toUpperCase(Locale.ROOT), null);
  }

  static StoreScope of(final AwsSecretsManagerStore store) {
    return new StoreScope(
        store.getRegion(),
        Objects.requireNonNullElse(store.getPathPrefix(), ""),
        store.getContainerSecretId());
  }

  static StoreScope of(final GcpSecretManagerStore store) {
    return new StoreScope(
        store.getProjectId(),
        Objects.requireNonNullElse(store.getPathPrefix(), ""),
        store.getContainerSecretId());
  }

  static StoreScope of(final AzureKeyVaultStore store) {
    final var vaultUrl = store.getVaultUrl();
    return new StoreScope(
        vaultUrl == null ? null : vaultUrl.replaceAll("/+$", ""),
        Objects.requireNonNullElse(store.getPathPrefix(), ""),
        store.getContainerSecretId());
  }

  /** Whether the two stores, of the same type, can read a secret in common. */
  boolean overlaps(final StoreScope other) {
    if (backend != null && other.backend != null && !backend.equals(other.backend)) {
      return false;
    }
    if (container != null && other.container != null) {
      return (prefix + container).equals(other.prefix + other.container);
    }
    if (container != null) {
      return (prefix + container).startsWith(other.prefix);
    }
    if (other.container != null) {
      return (other.prefix + other.container).startsWith(prefix);
    }
    return prefix.startsWith(other.prefix) || other.prefix.startsWith(prefix);
  }
}
