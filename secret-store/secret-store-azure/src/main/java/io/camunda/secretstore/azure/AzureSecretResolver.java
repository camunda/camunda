/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.secretstore.azure;

import static io.camunda.secretstore.SecretErrorCode.NOT_FOUND;
import static java.util.stream.Collectors.toMap;

import io.camunda.secretstore.SecretResolutionResult;
import io.camunda.secretstore.SecretStoreUnavailableException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * One Azure Key Vault resolution algorithm: how reference names are mapped to Key Vault secrets and
 * read. {@link AzureKeyVaultSecretStore} picks exactly one implementation at construction time,
 * based on {@link AzureKeyVaultStoreConfig#containerSecretId()}, and delegates every call to it.
 */
interface AzureSecretResolver {

  /**
   * @throws SecretStoreUnavailableException if the vault cannot be accessed
   */
  Map<String, SecretResolutionResult> resolve(Set<String> names);

  /**
   * @throws SecretStoreUnavailableException if the vault cannot be accessed or its content is
   *     malformed
   */
  List<String> list();

  /**
   * How many names one {@link #resolve} call covers. {@code 1} for a resolver issuing one request
   * per name; {@link Integer#MAX_VALUE} for a container secret, which reads every requested name
   * from one shared secret in a single request.
   */
  default int namesPerCall() {
    return Integer.MAX_VALUE;
  }

  /**
   * Makes the one call to the vault that this mode's minimal permission allows, so the startup
   * check fails exactly when this mode cannot work.
   *
   * @throws RuntimeException as the SDK throws it, for {@link StartupCheck} to classify
   */
  void validateConnectivity();

  /** What {@link #validateConnectivity()} does, as the end of "cannot ...", for the log. */
  String probeDescription();

  /**
   * Tells the operator what to grant when the vault answers {@link #validateConnectivity()} with
   * 403: the role, or the access-policy permission, this mode needs.
   */
  String accessDeniedHint();

  /**
   * Whether a 403 to {@link #validateConnectivity()} means that secrets fail to resolve too, rather
   * than only that the probe is not permitted.
   */
  boolean accessDeniedFailsResolution();

  static Map<String, SecretResolutionResult> notFound(final Set<String> names) {
    return names.stream()
        .collect(
            toMap(
                Function.identity(),
                name ->
                    new SecretResolutionResult.Failed(
                        NOT_FOUND, "Secret not found: " + name, null)));
  }
}
