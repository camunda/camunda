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
 * The secret ids a store can read: every id that starts with its prefix, or only its container
 * secret if it has one.
 *
 * @param backend the backend the store reads, such as an AWS region; {@code null} if left to the
 *     environment, which may match any other
 * @param prefix the prefix of every secret id the store reads
 * @param container the one secret the store reads, if it reads secrets out of a container
 * @param alias one more secret the store can read, when its prefix is the start of a full ARN
 */
record StoreScope(
    @Nullable String backend, String prefix, @Nullable String container, @Nullable String alias) {

  private static final int HTTPS_PORT = 443;
  private static final Pattern PROJECT_NUMBER = Pattern.compile("[0-9]+");
  private static final Pattern SECRET_ARN =
      Pattern.compile("arn:[^:]+:secretsmanager:[^:]*:[^:]*:secret:(.*)");
  private static final Pattern ARN_SUFFIX = Pattern.compile("-[0-9A-Za-z]{6}$");
  private static final Pattern PARTIAL_ARN_SUFFIX = Pattern.compile("(.*)-[0-9A-Za-z]{0,5}");

  StoreScope(
      final @Nullable String backend, final String prefix, final @Nullable String container) {
    this(backend, prefix, container, null);
  }

  static StoreScope of(final FileStore store) {
    final var directory = realPath(Path.of(store.getPath())).toString();
    return new StoreScope(
        null, directory.endsWith(File.separator) ? directory : directory + File.separator, null);
  }

  static StoreScope of(final EnvStore store) {
    return new StoreScope(null, store.getPrefix().toUpperCase(Locale.ROOT), null);
  }

  /**
   * A blank region is left to the environment. An ARN is compared as the secret name it ends with,
   * and a full ARN's random suffix can complete a prefix that ends partway into one.
   */
  static StoreScope of(final AwsSecretsManagerStore store) {
    final var region = store.getRegion();
    final var pathPrefix = store.getPathPrefix();
    final var container = store.getContainerSecretId();
    final var prefix = pathPrefix == null || pathPrefix.isBlank() ? "" : pathPrefix;
    final var backend = region == null || region.isBlank() ? null : region;
    if (container != null) {
      return new StoreScope(backend, "", containerName(prefix + container));
    }
    final var name = secretName(prefix);
    final var partialSuffix = PARTIAL_ARN_SUFFIX.matcher(name);
    final var alias =
        SECRET_ARN.matcher(prefix).matches() && partialSuffix.matches()
            ? partialSuffix.group(1)
            : null;
    return new StoreScope(backend, name, null, alias);
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
    if (reaches(other.alias) || other.reaches(alias)) {
      return true;
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

  private boolean reaches(final @Nullable String name) {
    if (name == null) {
      return false;
    }
    return container == null ? name.startsWith(prefix) : (prefix + container).equals(name);
  }

  /**
   * The directory a file store reads, with symlinks resolved. Only the part that exists can be
   * resolved, and a {@code ..} is only collapsed after the symlinks before it.
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

  private static String secretName(final String secretId) {
    final var arn = SECRET_ARN.matcher(secretId);
    return arn.matches() ? arn.group(1) : secretId;
  }

  /** The secret name in a container secret id, without the random suffix a full ARN ends in. */
  private static String containerName(final String secretId) {
    final var arn = SECRET_ARN.matcher(secretId);
    return arn.matches() ? ARN_SUFFIX.matcher(arn.group(1)).replaceFirst("") : secretId;
  }

  /**
   * The project a GCP store reads, or {@code null} for a project number, which can't be matched to
   * a project id.
   */
  private static @Nullable String project(final @Nullable String projectId) {
    return projectId == null || PROJECT_NUMBER.matcher(projectId).matches() ? null : projectId;
  }

  /**
   * The vault's host and port, so that URLs differing only in case or an explicit default port
   * match. A URL that doesn't parse is kept as written, and building its store rejects it.
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
