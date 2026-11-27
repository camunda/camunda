/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.secretstore.azure;

import com.azure.security.keyvault.secrets.SecretClient;
import io.camunda.secretstore.SecretResolutionResult;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Resolves every reference as a key of one JSON Key Vault secret, named {@code pathPrefix +
 * containerSecretId}.
 *
 * <p>Reading the vault is not implemented yet (camunda/camunda#67226): until then every name is
 * reported as not found and no names are listed.
 */
final class ContainerSecretResolver implements AzureSecretResolver {

  private final SecretClient client;
  private final String containerSecretName;

  ContainerSecretResolver(final SecretClient client, final String containerSecretName) {
    this.client = client;
    this.containerSecretName = containerSecretName;
  }

  @Override
  public Map<String, SecretResolutionResult> resolve(final Set<String> names) {
    return AzureSecretResolver.notFound(names);
  }

  @Override
  public List<String> list() {
    // TODO(#71444): return the container secret's field names once container resolution
    // (#68637, PR #71286) lands, reading it within the call timeout like resolve does
    return List.of();
  }

  /** Reads the container secret: the only call this mode makes, needing only {@code get}. */
  @Override
  public void validateConnectivity() {
    client.getSecret(containerSecretName);
  }

  @Override
  public String probeDescription() {
    return "read its container secret '" + containerSecretName + "'";
  }

  @Override
  public String accessDeniedHint() {
    return "grant the identity the 'Key Vault Secrets User' role on the vault, or, if the vault uses"
        + " access policies, the 'get' secret permission";
  }

  @Override
  public boolean accessDeniedFailsResolution() {
    return true;
  }
}
