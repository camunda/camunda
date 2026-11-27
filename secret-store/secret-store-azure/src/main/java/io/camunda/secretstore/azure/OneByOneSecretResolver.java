/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.secretstore.azure;

import static io.camunda.secretstore.SecretErrorCode.ACCESS_DENIED;
import static io.camunda.secretstore.SecretErrorCode.INVALID_REF;
import static io.camunda.secretstore.SecretErrorCode.NOT_FOUND;

import com.azure.security.keyvault.secrets.SecretClient;
import com.azure.security.keyvault.secrets.models.KeyVaultSecret;
import com.azure.security.keyvault.secrets.models.SecretProperties;
import io.camunda.secretstore.SecretResolutionResult;
import io.camunda.secretstore.SecretResolutionResult.Failed;
import io.camunda.secretstore.SecretResolutionResult.Resolved;
import io.camunda.secretstore.SecretStoreUnavailableException;
import java.time.InstantSource;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Resolves every reference as its own Key Vault secret, named {@code pathPrefix + name}, reading
 * its latest version with one {@code getSecret} call per name, which the SDK may retry. Each read
 * is bounded by the store's call timeout, retries included ({@link CallTimeout}); past it, or on
 * any other store-wide failure, the whole call fails rather than wait for the next name.
 *
 * <p>The name is passed as written: its case is kept and {@code _} is not mapped to {@code -}. Key
 * Vault compares names case-insensitively, so two spellings of one name read the same secret. The
 * value is returned unchanged; an empty value is reported as not found, as the environment-variable
 * store does.
 *
 * <p>A reference is refused, without a call to the vault, as {@link
 * io.camunda.secretstore.SecretErrorCode#INVALID_REF INVALID_REF} when it is empty or {@code
 * pathPrefix + name} is not a name Key Vault can hold ({@link
 * AzureKeyVaultSecretStore#NAME_PATTERN}); the message then suggests container mode, whose field
 * names have no such limit. Key Vault keeps returning a secret that is at or past its expiry date
 * or before its activation date, as these dates are only informational there; this resolver refuses
 * both. It also refuses a disabled secret, which Key Vault answers with 403 like a missing
 * permission. All three are reported as {@link io.camunda.secretstore.SecretErrorCode#NOT_FOUND
 * NOT_FOUND}, so a cached value is dropped and the incident resolves once the secret is fixed; the
 * message names the reason.
 *
 * <p>Every other failure is mapped by {@link AzureKeyVaultErrors#classify}. The first 403 also logs
 * one WARN for the store, as it may mean the identity lacks its role on the whole vault, which
 * would otherwise show only as one incident per reference. Listing is not implemented yet: no names
 * are listed.
 */
final class OneByOneSecretResolver implements AzureSecretResolver {

  private static final Logger LOG = LoggerFactory.getLogger(OneByOneSecretResolver.class);

  private final SecretClient client;
  private final String pathPrefix;
  private final InstantSource clock;
  private final String storeDescription;
  private final CallTimeout callTimeout;
  private final AtomicBoolean accessDeniedLogged = new AtomicBoolean();

  /**
   * @param clock what the expiry and activation dates are compared with
   * @param storeDescription names the store in its log lines
   * @param callTimeout bounds each read
   */
  OneByOneSecretResolver(
      final SecretClient client,
      final String pathPrefix,
      final InstantSource clock,
      final String storeDescription,
      final CallTimeout callTimeout) {
    this.client = client;
    this.pathPrefix = pathPrefix;
    this.clock = clock;
    this.storeDescription = storeDescription;
    this.callTimeout = callTimeout;
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
    if (name.isEmpty()) {
      return new Failed(
          INVALID_REF, "Invalid secret reference: the secret name must not be empty", null);
    }
    final var secretName = pathPrefix + name;
    if (!AzureKeyVaultSecretStore.NAME_PATTERN.matcher(secretName).matches()) {
      return invalidName(name, secretName);
    }
    final KeyVaultSecret secret;
    try {
      secret = callTimeout.call("read secret " + secretName, () -> client.getSecret(secretName));
    } catch (final SecretStoreUnavailableException e) {
      throw e;
    } catch (final RuntimeException e) {
      return failedRead(secretName, e);
    }
    if (secret == null) {
      throw new SecretStoreUnavailableException(
          "Azure Key Vault is unavailable: empty response for secret " + secretName);
    }
    final var refusal = refusalFor(secretName, secret.getProperties());
    if (refusal != null) {
      return refusal;
    }
    final var value = secret.getValue();
    if (value == null || value.isEmpty()) {
      return new Failed(
          NOT_FOUND, "Secret not found: " + secretName + " (its value is empty)", null);
    }
    return new Resolved(value);
  }

  private Failed failedRead(final String secretName, final RuntimeException e) {
    if (AzureKeyVaultErrors.isSecretDisabled(e)) {
      return new Failed(NOT_FOUND, SecretStatus.disabledRefusal("Secret " + secretName), null);
    }
    final var code = AzureKeyVaultErrors.classify(secretName, e);
    if (code == ACCESS_DENIED) {
      warnOnceAboutAccess(secretName, e);
    }
    return new Failed(
        code,
        AzureKeyVaultErrors.messageFor(code, secretName, e),
        AzureKeyVaultErrors.causeToKeep(e));
  }

  /**
   * Key Vault treats the expiry and activation dates as informational and still returns the value,
   * and a proxy or emulator may likewise return a disabled bundle; the returned refusal must
   * therefore never carry the secret.
   */
  private @Nullable Failed refusalFor(
      final String secretName, final @Nullable SecretProperties properties) {
    final var refusal = SecretStatus.refusal("Secret " + secretName, properties, clock.instant());
    return refusal == null ? null : new Failed(NOT_FOUND, refusal, null);
  }

  private Failed invalidName(final String name, final String secretName) {
    final var prefixed = pathPrefix.isEmpty() ? "" : " (with the path prefix '" + pathPrefix + "')";
    return new Failed(
        INVALID_REF,
        "Invalid secret reference: "
            + name
            + ". Its Key Vault secret name '"
            + secretName
            + "'"
            + prefixed
            + " must be 1 to "
            + AzureKeyVaultStoreConfig.MAX_SECRET_NAME_LENGTH
            + " letters, digits and hyphens ("
            + AzureKeyVaultSecretStore.NAME_PATTERN.pattern()
            + "), and names are never rewritten. To keep it as written, store it as a field of a"
            + " container secret (containerSecretId)",
        null);
  }

  // with RBAC scoped per secret, a 403 can be genuine for this one secret: hence "may"
  private void warnOnceAboutAccess(final String secretName, final Exception e) {
    if (accessDeniedLogged.compareAndSet(false, true)) {
      LOG.warn(
          "{} was denied access to secret '{}' ({}). The identity may lack the 'Key Vault Secrets"
              + " User' role on the vault or, if the vault uses access policies, the 'get' secret"
              + " permission. Further denials of this store are not logged again",
          storeDescription,
          secretName,
          AzureKeyVaultErrors.describe(e));
    }
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
