/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.secretstore.env;

import static io.camunda.secretstore.SecretErrorCode.NOT_FOUND;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toUnmodifiableMap;

import io.camunda.secretstore.SecretResolutionResult;
import io.camunda.secretstore.SecretStore;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A secret store backed by the process environment: secret {@code name} is held in the variable
 * {@code <prefix><name>}.
 *
 * <p>The environment is snapshotted at construction, keeping only the variables that carry the
 * prefix, so the store never holds the platform's own settings or credentials. The JVM cannot see
 * changes to its own environment after start, and Kubernetes does not update a running container's
 * environment either, so rotating a secret held here requires a restart.
 *
 * <p>Reading secrets from the snapshot is not implemented yet: until then, {@link #resolve(Set)}
 * reports every name as {@link io.camunda.secretstore.SecretErrorCode#NOT_FOUND not found} and
 * {@link #list()} returns no names.
 *
 * <p>The prefix is mandatory, must be a {@link #PREFIX_FORMAT well-formed} variable-name prefix
 * ending in an underscore, and must not start with a {@link #RESERVED_PREFIXES reserved prefix}.
 * Without it, anyone who can deploy a process could read every variable of the node, such as
 * database passwords, OIDC client secrets or cloud keys, through a {@code camunda.secrets.<name>}
 * reference.
 */
public final class EnvVarSecretStore implements SecretStore {

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

  private final Map<String, String> environment;
  private final String prefix;

  /**
   * @throws IllegalArgumentException if the prefix is blank, malformed, or starts with a {@link
   *     #RESERVED_PREFIXES reserved prefix}
   */
  public EnvVarSecretStore(final Map<String, String> environment, final String prefix) {
    requireValidPrefix(prefix);
    this.environment =
        environment.entrySet().stream()
            .filter(variable -> variable.getKey().startsWith(prefix))
            .collect(toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
    this.prefix = prefix;
  }

  /**
   * Creates a store over a snapshot of this process's environment.
   *
   * @throws IllegalArgumentException if the prefix is blank, malformed, or starts with a {@link
   *     #RESERVED_PREFIXES reserved prefix}
   */
  public static EnvVarSecretStore fromSystemEnvironment(final String prefix) {
    return new EnvVarSecretStore(System.getenv(), prefix);
  }

  @Override
  public Map<String, SecretResolutionResult> resolve(final Set<String> names) {
    if (names.isEmpty()) {
      return Map.of();
    }
    LOG.debug(
        "Resolving {} secrets from environment variables prefixed '{}'", names.size(), prefix);
    return names.stream().collect(toMap(name -> name, EnvVarSecretStore::notFound));
  }

  @Override
  public List<String> list() {
    LOG.debug(
        "Listing secrets from {} environment variables, prefixed '{}'", environment.size(), prefix);
    return List.of();
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
