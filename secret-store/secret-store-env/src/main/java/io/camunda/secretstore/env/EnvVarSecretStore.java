/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.secretstore.env;

import static io.camunda.secretstore.SecretErrorCode.INVALID_REF;
import static io.camunda.secretstore.SecretErrorCode.NOT_FOUND;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toUnmodifiableMap;

import io.camunda.secretstore.LocallyCachedSecretStore;
import io.camunda.secretstore.SecretResolutionResult;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A secret store backed by the process environment: secret {@code name} is held in the variable
 * {@code <prefix><name>}.
 *
 * <p>The environment is snapshotted at construction, keeping only the variables that carry the
 * prefix, keyed by the name that follows it, so the store never holds the platform's own settings
 * or credentials and a name can only ever reach a prefixed variable. The JVM cannot see changes to
 * its own environment after start, and Kubernetes does not update a running container's environment
 * either, so rotating a secret held here requires a restart.
 *
 * <p>Names match exactly, case included, on every OS. A variable with an empty value is not a
 * secret and resolves as {@link io.camunda.secretstore.SecretErrorCode#NOT_FOUND not found}. Values
 * are returned exactly as the environment holds them, a trailing newline included, unlike the file
 * store. A name outside {@link #NAME_PATTERN} is refused as an {@link
 * io.camunda.secretstore.SecretErrorCode#INVALID_REF invalid reference} without a lookup, even
 * where a variable of that name exists: Docker and Kubernetes pass names such as {@code tls.crt}
 * unchanged, and a backtick-escaped reference in a job can carry one, but the secrets API cannot.
 *
 * <p>The whole snapshot is already in memory, so the store implements {@link
 * LocallyCachedSecretStore} itself rather than sit behind a second cache: every lookup is local,
 * and the configured cache TTL and maximum size do not apply to it.
 *
 * <p>Listing secret names is not implemented yet: {@link #list()} returns no names.
 *
 * <p>The prefix is mandatory, must be a {@link #PREFIX_FORMAT well-formed} variable-name prefix
 * ending in an underscore, and must not start with a {@link #RESERVED_PREFIXES reserved prefix}.
 * Without it, anyone who can deploy a process could read every variable of the node, such as
 * database passwords, OIDC client secrets or cloud keys, through a {@code camunda.secrets.<name>}
 * reference.
 */
public final class EnvVarSecretStore implements LocallyCachedSecretStore {

  /**
   * The names this store looks up: the characters both an environment variable name and a secret
   * reference can hold. Kept in step with the engine's and the secrets API's charset by {@code
   * SecretReferenceCharsetSyncTest}.
   */
  public static final Pattern NAME_PATTERN = Pattern.compile("[\\p{Alnum}_-]+");

  /**
   * A prefix starts with a letter, holds only letters, digits and underscores, and ends with an
   * underscore. The trailing underscore bounds the prefix to whole name segments, so {@code
   * SECRET_} cannot also match {@code SECRETARY_*}, and it rules out a prefix cut short of a
   * reserved one, such as {@code CAMUNDA} or {@code C}. Rejecting whitespace and other characters
   * no variable name carries turns a mistyped prefix into a startup error instead of a store that
   * silently finds nothing.
   */
  static final Pattern PREFIX_FORMAT = Pattern.compile("[A-Za-z][A-Za-z0-9_]*_");

  /**
   * Prefixes of variables the platform, its runtime or the services it ships with read for their
   * own settings and credentials. A secret prefix starting with one of these, ignoring case, is
   * refused: {@code CAMUNDA_APP_} would expose {@code CAMUNDA_APP_*} settings.
   *
   * <p>This list guards only those well-known settings and credentials; any other variable that
   * happens to start with the configured prefix, such as a {@code DB_PASSWORD} under a {@code DB_}
   * prefix, stays reachable. Operators should dedicate a prefix to secrets rather than reuse a
   * broad one.
   */
  static final List<String> RESERVED_PREFIXES =
      List.of(
          "CAMUNDA_",
          "ZEEBE_",
          "OPERATE_",
          "TASKLIST_",
          "OPTIMIZE_",
          "IDENTITY_",
          "ELASTICSEARCH_",
          "OPENSEARCH_",
          "KEYCLOAK_",
          "SPRING_",
          "SERVER_",
          "MANAGEMENT_",
          "LOGGING_",
          "JAVA_",
          "JDK_",
          "AWS_",
          "AZURE_",
          "GOOGLE_",
          "KUBERNETES_");

  private static final Logger LOG = LoggerFactory.getLogger(EnvVarSecretStore.class);

  private final Map<String, String> secrets;
  private final String prefix;

  /**
   * Package-private so that production code builds the store through {@link
   * #fromConfig(EnvVarSecretStoreConfig)}, as it does every other store.
   *
   * @throws IllegalArgumentException if the prefix is blank, malformed, or starts with a {@link
   *     #RESERVED_PREFIXES reserved prefix}
   */
  EnvVarSecretStore(final Map<String, String> environment, final String prefix) {
    requireValidPrefix(prefix);
    secrets =
        environment.entrySet().stream()
            .filter(variable -> variable.getKey().startsWith(prefix))
            .filter(variable -> !variable.getValue().isEmpty())
            .collect(
                toUnmodifiableMap(
                    variable -> variable.getKey().substring(prefix.length()), Map.Entry::getValue));
    this.prefix = prefix;
  }

  /**
   * Creates a store over a snapshot of the configured environment.
   *
   * @throws IllegalArgumentException if the prefix is blank, malformed, or starts with a {@link
   *     #RESERVED_PREFIXES reserved prefix}
   */
  public static EnvVarSecretStore fromConfig(final EnvVarSecretStoreConfig config) {
    return new EnvVarSecretStore(config.environment(), config.prefix());
  }

  @Override
  public Map<String, SecretResolutionResult> resolve(final Set<String> names) {
    if (names.isEmpty()) {
      return Map.of();
    }
    LOG.debug(
        "Resolving {} secrets from environment variables prefixed '{}'", names.size(), prefix);
    return names.stream().collect(toMap(name -> name, this::resolveOne));
  }

  @Override
  public Map<String, SecretResolutionResult> resolveFromStore(final Set<String> names) {
    return resolve(names);
  }

  @Override
  public Optional<String> lookupLocal(final String name) {
    return isValidName(name) ? Optional.ofNullable(secrets.get(name)) : Optional.empty();
  }

  @Override
  public List<String> list() {
    LOG.debug(
        "Listing secrets from {} environment variables, prefixed '{}'", secrets.size(), prefix);
    return List.of();
  }

  private SecretResolutionResult resolveOne(final String name) {
    if (!isValidName(name)) {
      return new SecretResolutionResult.Failed(INVALID_REF, "Invalid secret name: " + name, null);
    }
    final var value = secrets.get(name);
    return value == null ? notFound(name) : new SecretResolutionResult.Resolved(value);
  }

  private static boolean isValidName(final String name) {
    return NAME_PATTERN.matcher(name).matches();
  }

  private static SecretResolutionResult notFound(final String name) {
    return new SecretResolutionResult.Failed(NOT_FOUND, "Secret not found: " + name, null);
  }

  private static void requireValidPrefix(final String prefix) {
    if (prefix.isBlank()) {
      throw new IllegalArgumentException(
          "The prefix is missing; it is required so that a secret reference cannot read every"
              + " environment variable of the node");
    }
    if (!PREFIX_FORMAT.matcher(prefix).matches()) {
      throw new IllegalArgumentException(
          "The prefix '%s' is malformed; it must start with a letter, contain only letters, digits and underscores, and end with an underscore"
              .formatted(prefix));
    }
    final var upperCasePrefix = prefix.toUpperCase(Locale.ROOT);
    for (final var reserved : RESERVED_PREFIXES) {
      if (upperCasePrefix.startsWith(reserved)) {
        throw new IllegalArgumentException(
            "The prefix '%s' overlaps the reserved prefix '%s', so a secret reference could read the platform's own settings or credentials"
                .formatted(prefix, reserved));
      }
    }
  }
}
