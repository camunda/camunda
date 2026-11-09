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
 * based on {@link AzureKeyVaultStoreConfig#mode()}, and delegates every call to it.
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
