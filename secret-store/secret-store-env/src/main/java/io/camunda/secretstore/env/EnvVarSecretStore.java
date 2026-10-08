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

import io.camunda.secretstore.SecretResolutionResult;
import io.camunda.secretstore.SecretStore;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A secret store backed by the process environment: secret {@code name} is read from the variable
 * {@code <prefix><name>}.
 *
 * <p>The environment is snapshotted at construction. The JVM cannot see changes to its own
 * environment after start, and Kubernetes does not update a running container's environment either,
 * so rotating a secret held here requires a restart.
 *
 * <p>The prefix is mandatory and must not overlap the namespaces the platform reads its own
 * configuration and cloud credentials from. Without it, anyone who can deploy a process could read
 * every variable of the node — database passwords, OIDC client secrets, cloud keys — through a
 * {@code camunda.secrets.<name>} reference.
 */
public final class EnvVarSecretStore implements SecretStore {

  /**
   * Prefixes of variables the platform or its runtime reads for its own settings and credentials. A
   * secret prefix that starts with one of these, or that one of these starts with, would let a
   * secret reference read them.
   */
  static final List<String> RESERVED_PREFIXES =
      List.of(
          "CAMUNDA_",
          "ZEEBE_",
          "OPERATE_",
          "TASKLIST_",
          "OPTIMIZE_",
          "IDENTITY_",
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

  private static final Pattern VALID_PREFIX = Pattern.compile("[A-Za-z0-9_]+");
  private static final Logger LOG = LoggerFactory.getLogger(EnvVarSecretStore.class);

  private final Map<String, String> environment;
  private final String prefix;
  private final NameMatching nameMatching;

  /**
   * @throws IllegalArgumentException if the prefix is blank, contains characters other than
   *     letters, digits and underscores, or overlaps a {@link #RESERVED_PREFIXES reserved prefix}
   */
  public EnvVarSecretStore(
      final Map<String, String> environment, final String prefix, final NameMatching nameMatching) {
    requireValidPrefix(prefix);
    this.environment = Map.copyOf(environment);
    this.prefix = prefix;
    this.nameMatching = nameMatching;
  }

  public static EnvVarSecretStore fromSystemEnvironment(
      final String prefix, final NameMatching nameMatching) {
    return new EnvVarSecretStore(System.getenv(), prefix, nameMatching);
  }

  /**
   * Whether two prefixes could select the same variable. Compared case-insensitively, since {@link
   * NameMatching#CONNECTORS_COMPATIBLE} also tries the upper-cased name.
   */
  public static boolean overlap(final String prefix, final String otherPrefix) {
    final var a = prefix.toUpperCase(Locale.ROOT);
    final var b = otherPrefix.toUpperCase(Locale.ROOT);
    return a.startsWith(b) || b.startsWith(a);
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

  /**
   * Lists the names of variables carrying the prefix, with the prefix removed. Under {@link
   * NameMatching#CONNECTORS_COMPATIBLE} this is the variable's own name, which is not necessarily
   * the name a process references it by: {@code db.password} and {@code DB_PASSWORD} both resolve
   * {@code <PREFIX>DB_PASSWORD}, and only the latter can be listed.
   */
  @Override
  public List<String> list() {
    final var upperPrefix = prefix.toUpperCase(Locale.ROOT);
    final var names = new ArrayList<String>();
    for (final var variable : environment.keySet()) {
      final String matchedPrefix;
      if (variable.startsWith(prefix)) {
        matchedPrefix = prefix;
      } else if (nameMatching == NameMatching.CONNECTORS_COMPATIBLE
          && variable.startsWith(upperPrefix)) {
        matchedPrefix = upperPrefix;
      } else {
        continue;
      }
      final var name = variable.substring(matchedPrefix.length());
      if (!name.isEmpty()) {
        names.add(name);
      }
    }
    return names;
  }

  private SecretResolutionResult resolveOne(final String name) {
    if (name.isBlank()) {
      return new SecretResolutionResult.Failed(INVALID_REF, "Invalid secret name: " + name, null);
    }
    final var value = lookup(prefix + name);
    if (value == null) {
      return new SecretResolutionResult.Failed(NOT_FOUND, "Secret not found: " + name, null);
    }
    return new SecretResolutionResult.Resolved(value);
  }

  private @Nullable String lookup(final String variable) {
    if (nameMatching == NameMatching.EXACT) {
      return environment.get(variable);
    }
    for (final var candidate : connectorsCandidates(variable)) {
      final var value = environment.get(candidate);
      if (value != null) {
        return value;
      }
    }
    return null;
  }

  /** The candidates, in order, that Spring's {@code SystemEnvironmentPropertySource} tries. */
  static Set<String> connectorsCandidates(final String variable) {
    final var candidates = new LinkedHashSet<String>();
    addSeparatorVariants(candidates, variable);
    addSeparatorVariants(candidates, variable.toUpperCase(Locale.ROOT));
    return candidates;
  }

  private static void addSeparatorVariants(final Set<String> candidates, final String name) {
    final var noDot = name.replace('.', '_');
    candidates.add(name);
    candidates.add(noDot);
    candidates.add(name.replace('-', '_'));
    candidates.add(noDot.replace('-', '_'));
  }

  private static void requireValidPrefix(final String prefix) {
    if (prefix.isBlank()) {
      throw new IllegalArgumentException(
          "An environment variable secret store requires a non-blank prefix, so that a secret"
              + " reference cannot read every environment variable of the node");
    }
    if (!VALID_PREFIX.matcher(prefix).matches()) {
      throw new IllegalArgumentException(
          "Environment variable secret store prefix '%s' may only contain letters, digits and underscores"
              .formatted(prefix));
    }
    for (final var reserved : RESERVED_PREFIXES) {
      if (overlap(prefix, reserved)) {
        throw new IllegalArgumentException(
            "Environment variable secret store prefix '%s' overlaps the reserved prefix '%s', which would expose the platform's own settings or credentials as secrets"
                .formatted(prefix, reserved));
      }
    }
  }
}
