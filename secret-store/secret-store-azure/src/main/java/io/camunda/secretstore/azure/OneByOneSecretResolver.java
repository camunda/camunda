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
 * Resolves every reference as its own Key Vault secret, named {@code pathPrefix + name}.
 *
 * <p>Reading the vault is not implemented yet (camunda/camunda#67226): until then every name is
 * reported as not found and no names are listed.
 */
final class OneByOneSecretResolver implements AzureSecretResolver {

  private final SecretClient client;

  OneByOneSecretResolver(final SecretClient client) {
    this.client = client;
  }

  @Override
  public Map<String, SecretResolutionResult> resolve(final Set<String> names) {
    return AzureSecretResolver.notFound(names);
  }

  @Override
  public List<String> list() {
    return List.of();
  }

  /** One request per name, so the store fans a request out across names. */
  @Override
  public int namesPerCall() {
    return 1;
  }

  /**
   * Fetches the first page of secret properties, never a value: one request, whatever the vault
   * holds.
   */
  @Override
  public void validateConnectivity() {
    client.listPropertiesOfSecrets().iterableByPage().iterator().next();
  }

  @Override
  public String probeDescription() {
    return "list its secrets";
  }

  // an access policy granting only 'get' refuses the listing yet still lets every reference resolve
  @Override
  public String accessDeniedHint() {
    return "grant the identity the 'Key Vault Secrets User' role on the vault, or, if the vault uses"
        + " access policies, the 'list' secret permission. Listing is not permitted; resolution may"
        + " still work if the identity has the 'get' permission";
  }

  @Override
  public boolean accessDeniedFailsResolution() {
    return false;
  }
}
