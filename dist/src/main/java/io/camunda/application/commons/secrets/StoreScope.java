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
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;
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

  private static final int HTTPS_PORT = 443;
  private static final Pattern PROJECT_NUMBER = Pattern.compile("[0-9]+");

  static StoreScope of(final FileStore store) {
    final var directory = realPath(Path.of(store.getPath())).toString();
    return new StoreScope(
        null, directory.endsWith(File.separator) ? directory : directory + File.separator, null);
  }

  static StoreScope of(final EnvStore store) {
    return new StoreScope(null, store.getPrefix().toUpperCase(Locale.ROOT), null);
  }

  static StoreScope of(final AwsSecretsManagerStore store) {
    final var pathPrefix = store.getPathPrefix();
    return new StoreScope(
        store.getRegion(),
        pathPrefix == null || pathPrefix.isBlank() ? "" : pathPrefix,
        store.getContainerSecretId());
  }

  static StoreScope of(final GcpSecretManagerStore store) {
    return new StoreScope(
        project(store.getProjectId()),
        Objects.requireNonNullElse(store.getPathPrefix(), ""),
        store.getContainerSecretId());
  }

  /** Key Vault secret names are case-insensitive, so they are compared in lower case. */
  static StoreScope of(final AzureKeyVaultStore store) {
    final var vaultUrl = store.getVaultUrl();
    final var container = store.getContainerSecretId();
    return new StoreScope(
        vaultUrl == null ? null : vault(vaultUrl),
        Objects.requireNonNullElse(store.getPathPrefix(), "").toLowerCase(Locale.ROOT),
        container == null ? null : container.toLowerCase(Locale.ROOT));
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

  /**
   * The directory a file store reads, with symlinks resolved as the store resolves them. Only the
   * part of the path that exists can be resolved, so the rest is kept as written. A {@code ..} is
   * only collapsed after the symlinks before it are resolved, as the filesystem does.
   */
  private static Path realPath(final Path directory) {
    final var absolute = directory.toAbsolutePath();
    @Nullable Path existing = absolute;
    while (existing != null && !Files.exists(existing)) {
      existing = existing.getParent();
    }
    if (existing == null) {
      return absolute.normalize();
    }
    try {
      return existing.toRealPath().resolve(existing.relativize(absolute)).normalize();
    } catch (final IOException unreadable) {
      return absolute.normalize();
    }
  }

  /**
   * The project a GCP store reads, or {@code null} if it may be any project: a project number names
   * the same project as a project id, and the two can't be matched here.
   */
  private static @Nullable String project(final @Nullable String projectId) {
    return projectId == null || PROJECT_NUMBER.matcher(projectId).matches() ? null : projectId;
  }

  /**
   * The vault a URL names, as its host and port, so that URLs that differ only in case or in an
   * explicit default port name the same vault. A URL that doesn't parse is kept as written, to be
   * rejected when its store is built.
   */
  private static String vault(final String vaultUrl) {
    final URI uri;
    try {
      uri = new URI(vaultUrl);
    } catch (final URISyntaxException invalid) {
      return vaultUrl;
    }
    if (uri.getHost() == null) {
      return vaultUrl;
    }
    return uri.getHost().toLowerCase(Locale.ROOT)
        + ":"
        + (uri.getPort() == -1 ? HTTPS_PORT : uri.getPort());
  }
}
