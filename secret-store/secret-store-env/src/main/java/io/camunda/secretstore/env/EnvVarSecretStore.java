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

import io.camunda.secretstore.SecretResolutionResult;
import io.camunda.secretstore.SecretStore;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A secret store backed by the process environment: secret {@code name} is held in the variable
 * {@code <prefix><name>}.
 *
 * <p>The environment is snapshotted at construction. The JVM cannot see changes to its own
 * environment after start, and Kubernetes does not update a running container's environment either,
 * so rotating a secret held here requires a restart.
 *
 * <p>Reading secrets from the snapshot is not implemented yet: until then, {@link #resolve(Set)}
 * reports every name as {@link io.camunda.secretstore.SecretErrorCode#NOT_FOUND not found} and
 * {@link #list()} returns no names.
 */
public final class EnvVarSecretStore implements SecretStore {

  private static final Logger LOG = LoggerFactory.getLogger(EnvVarSecretStore.class);

  private final Map<String, String> environment;
  private final String prefix;

  public EnvVarSecretStore(final Map<String, String> environment, final String prefix) {
    this.environment = Map.copyOf(environment);
    this.prefix = prefix;
  }

  /** Creates a store over a snapshot of this process's environment. */
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
}
