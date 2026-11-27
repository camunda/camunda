/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.secretstore.azure;

import static io.camunda.secretstore.SecretErrorCode.NOT_FOUND;

import com.azure.security.keyvault.secrets.SecretClient;
import com.azure.security.keyvault.secrets.models.KeyVaultSecret;
import io.camunda.secretstore.JsonContainerSecret;
import io.camunda.secretstore.SecretErrorCode;
import io.camunda.secretstore.SecretResolutionResult;
import io.camunda.secretstore.SecretResolutionResult.Failed;
import io.camunda.secretstore.SecretStoreUnavailableException;
import java.time.InstantSource;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Resolves every reference as a field of one JSON Key Vault secret, named {@code pathPrefix +
 * containerSecretId}, reading its latest version with one {@code getSecret} call per {@link
 * #resolve}, whatever the number of names. The read is bounded by the store's call timeout, retries
 * included ({@link CallTimeout}).
 *
 * <p>Field names follow Camunda's reference rules, not Key Vault's: a name such as {@code API_KEY}
 * resolves. The value must be a flat JSON object of string values, read by {@link
 * JsonContainerSecret}: invalid JSON or a non-object fails every name as {@link
 * io.camunda.secretstore.SecretErrorCode#INVALID_REF INVALID_REF}, a missing or {@code null} field
 * is {@link io.camunda.secretstore.SecretErrorCode#NOT_FOUND NOT_FOUND} and a field that is not a
 * string is {@code INVALID_REF}. A message names the container secret, never its content.
 *
 * <p>A container secret that is disabled, at or past its expiry date or not active yet is refused
 * as {@code OneByOneSecretResolver} refuses a secret: every requested name fails as {@code
 * NOT_FOUND}, naming the reason. Any other failure to read it is mapped by {@link
 * AzureKeyVaultErrors#classify} and fails every requested name alike. Listing is not implemented
 * yet: no names are listed.
 */
final class ContainerSecretResolver implements AzureSecretResolver {

  private static final Logger LOG = LoggerFactory.getLogger(ContainerSecretResolver.class);

  private final SecretClient client;
  private final String containerSecretName;
  private final InstantSource clock;
  private final CallTimeout callTimeout;

  /**
   * @param clock what the expiry and activation dates are compared with
   * @param callTimeout bounds each read
   */
  ContainerSecretResolver(
      final SecretClient client,
      final String containerSecretName,
      final InstantSource clock,
      final CallTimeout callTimeout) {
    this.client = client;
    this.containerSecretName = containerSecretName;
    this.clock = clock;
    this.callTimeout = callTimeout;
  }

  @Override
  public Map<String, SecretResolutionResult> resolve(final Set<String> names) {
    if (names.isEmpty()) {
      return Map.of();
    }
    LOG.debug(
        "Resolving {} secret references from Azure Key Vault container secret '{}'",
        names.size(),
        containerSecretName);
    final KeyVaultSecret secret;
    try {
      secret =
          callTimeout.call(
              "read container secret " + containerSecretName,
              () -> client.getSecret(containerSecretName));
    } catch (final SecretStoreUnavailableException e) {
      throw e;
    } catch (final RuntimeException e) {
      return failedRead(names, e);
    }
    if (secret == null) {
      throw new SecretStoreUnavailableException(
          "Azure Key Vault is unavailable: empty response for container secret "
              + containerSecretName);
    }
    final var refusal = SecretStatus.refusal(subject(), secret.getProperties(), clock.instant());
    if (refusal != null) {
      return JsonContainerSecret.failAll(names, NOT_FOUND, refusal);
    }
    final var value = secret.getValue();
    return JsonContainerSecret.resolve(containerSecretName, value == null ? "" : value, names);
  }

  private Map<String, SecretResolutionResult> failedRead(
      final Set<String> names, final RuntimeException e) {
    if (AzureKeyVaultErrors.isSecretDisabled(e)) {
      return JsonContainerSecret.failAll(names, NOT_FOUND, SecretStatus.disabledRefusal(subject()));
    }
    final var code = AzureKeyVaultErrors.classify(containerSecretName, e);
    return failAll(
        names,
        code,
        AzureKeyVaultErrors.messageFor(code, containerSecretName, e),
        AzureKeyVaultErrors.causeToKeep(e));
  }

  private String subject() {
    return "Container secret " + containerSecretName;
  }

  private static Map<String, SecretResolutionResult> failAll(
      final Set<String> names,
      final SecretErrorCode code,
      final String message,
      final @Nullable Exception cause) {
    final Map<String, SecretResolutionResult> results = new LinkedHashMap<>(names.size());
    for (final var name : names) {
      results.put(name, new Failed(code, message, cause));
    }
    return results;
  }

  @Override
  public List<String> list() {
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
