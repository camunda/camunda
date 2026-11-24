/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.secretstore.azure;

import static io.camunda.secretstore.SecretErrorCode.NOT_FOUND;

import com.azure.core.exception.ResourceNotFoundException;
import com.azure.security.keyvault.secrets.SecretClient;
import io.camunda.secretstore.SecretResolutionResult;
import io.camunda.secretstore.SecretResolutionResult.Failed;
import io.camunda.secretstore.SecretResolutionResult.Resolved;
import io.camunda.secretstore.SecretStoreUnavailableException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Resolves every reference as its own Key Vault secret, named {@code pathPrefix + name}, reading
 * its latest version with one {@code getSecret} request per name.
 *
 * <p>The name is passed as written: its case is kept and {@code _} is not mapped to {@code -}. Key
 * Vault compares names case-insensitively, so two spellings of one name read the same secret. The
 * value is returned unchanged; an empty value is reported as not found, as the environment-variable
 * store does.
 *
 * <p>A secret the vault does not hold is reported as {@link
 * io.camunda.secretstore.SecretErrorCode#NOT_FOUND NOT_FOUND}; every other failure makes the vault
 * unavailable for the whole call. Listing is not implemented yet: no names are listed.
 */
final class OneByOneSecretResolver implements AzureSecretResolver {

  private static final Logger LOG = LoggerFactory.getLogger(OneByOneSecretResolver.class);

  private final SecretClient client;
  private final String pathPrefix;

  OneByOneSecretResolver(final SecretClient client, final String pathPrefix) {
    this.client = client;
    this.pathPrefix = pathPrefix;
  }

  @Override
  public Map<String, SecretResolutionResult> resolve(final Set<String> names) {
    LOG.debug("Resolving {} secret references from Azure Key Vault", names.size());
    final Map<String, SecretResolutionResult> results = new LinkedHashMap<>(names.size());
    for (final var name : names) {
      results.put(name, resolveSingle(name));
    }
    return results;
  }

  private SecretResolutionResult resolveSingle(final String name) {
    final var secretName = pathPrefix + name;
    final String value;
    try {
      value = client.getSecret(secretName).getValue();
    } catch (final ResourceNotFoundException e) {
      return notFound(secretName, e);
    } catch (final RuntimeException e) {
      throw new SecretStoreUnavailableException(
          "Azure Key Vault is unavailable: " + e.getMessage(), e);
    }
    if (value == null || value.isEmpty()) {
      return new Failed(
          NOT_FOUND, "Secret not found: " + secretName + " (its value is empty)", null);
    }
    return new Resolved(value);
  }

  private static Failed notFound(final String secretName, final Exception cause) {
    return new Failed(NOT_FOUND, "Secret not found: " + secretName, cause);
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
