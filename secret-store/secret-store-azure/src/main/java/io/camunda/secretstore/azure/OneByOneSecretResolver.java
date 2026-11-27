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

import com.azure.core.exception.ClientAuthenticationException;
import com.azure.core.exception.HttpResponseException;
import com.azure.security.keyvault.secrets.SecretClient;
import com.azure.security.keyvault.secrets.models.KeyVaultSecret;
import com.azure.security.keyvault.secrets.models.SecretProperties;
import io.camunda.secretstore.SecretResolutionResult;
import io.camunda.secretstore.SecretResolutionResult.Failed;
import io.camunda.secretstore.SecretResolutionResult.Resolved;
import io.camunda.secretstore.SecretStoreUnavailableException;
import java.time.InstantSource;
import java.util.ArrayList;
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
 * AzureKeyVaultSecretStore#NAME_PATTERN}). Key Vault keeps returning a secret that is at or past
 * its expiry date or before its activation date, as these dates are only informational there; this
 * resolver refuses both. It also refuses a disabled secret, which Key Vault answers with 403 like a
 * missing permission. All three are reported as {@link
 * io.camunda.secretstore.SecretErrorCode#NOT_FOUND NOT_FOUND}, so a cached value is dropped and the
 * incident resolves once the secret is fixed; the message names the reason.
 *
 * <p>Every other failure is mapped by {@link AzureKeyVaultErrors#classify}. The first 403 also logs
 * one WARN for the store, as it may mean the identity lacks its role on the whole vault, which
 * would otherwise show only as one incident per reference.
 *
 * <p>Listing pages through every secret's properties, never a value, and returns each name that
 * starts with the path prefix, compared case-insensitively as Key Vault compares names, without
 * that prefix, so a listed name resolves as listed. Disabled, expired and not yet active secrets,
 * and secrets backing a Key Vault certificate, are listed too; Camunda's reference rules,
 * duplicates and order are left to the caller. Listing is bounded by the store's call timeout as a
 * whole, every page included, and any failure to list fails the whole listing as store-unavailable.
 */
final class OneByOneSecretResolver implements AzureSecretResolver {

  private static final Logger LOG = LoggerFactory.getLogger(OneByOneSecretResolver.class);

  private static final String LIST_PERMISSION_HINT =
      "grant the identity the 'Key Vault Secrets User' role on the vault, or, if the vault uses"
          + " access policies, the 'list' secret permission";

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
      return refused(secretName, "is disabled in Azure Key Vault");
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
    if (properties == null) {
      return null;
    }
    return switch (SecretStatus.of(properties, clock.instant())) {
      case ACTIVE -> null;
      case DISABLED -> refused(secretName, "is disabled in Azure Key Vault");
      case EXPIRED -> refused(secretName, "expired on " + properties.getExpiresOn().toInstant());
      case NOT_YET_ACTIVE ->
          refused(secretName, "is not active until " + properties.getNotBefore().toInstant());
    };
  }

  private static Failed refused(final String secretName, final String reason) {
    return new Failed(NOT_FOUND, "Secret " + secretName + " " + reason + " and is refused", null);
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
            + "), and names are never rewritten",
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
    return listSecrets().stream().map(ListedSecret::name).toList();
  }

  /**
   * Every secret under the path prefix with the properties its status derives from, for {@link
   * #list} and a status-aware listing alike, so both name the same secrets. The status-aware
   * listing (camunda/camunda#68643) derives each {@link SecretStatus} from these properties.
   *
   * @throws SecretStoreUnavailableException if any page cannot be read within the call timeout
   */
  List<ListedSecret> listSecrets() {
    LOG.debug("Listing secrets from Azure Key Vault");
    try {
      return callTimeout.call("list its secrets", this::readAllPages);
    } catch (final SecretStoreUnavailableException e) {
      // a timeout, an interrupt or a closed store: name the store, as every other listing failure
      throw new SecretStoreUnavailableException(storeDescription + ": " + e.getMessage(), e);
    } catch (final RuntimeException e) {
      throw listingFailed(e);
    }
  }

  // iterating the PagedIterable fetches each next page on demand
  private List<ListedSecret> readAllPages() {
    final List<ListedSecret> listed = new ArrayList<>();
    for (final var properties : client.listPropertiesOfSecrets()) {
      final var name = referenceName(properties.getName());
      if (name != null) {
        listed.add(
            new ListedSecret(
                name,
                properties.isEnabled(),
                properties.getExpiresOn(),
                properties.getNotBefore()));
      }
    }
    return listed;
  }

  /** The reference a Key Vault secret name resolves from, or {@code null} if none does. */
  private @Nullable String referenceName(final @Nullable String secretName) {
    if (secretName == null
        || secretName.length() <= pathPrefix.length()
        || !secretName.regionMatches(true, 0, pathPrefix, 0, pathPrefix.length())) {
      return null;
    }
    return secretName.substring(pathPrefix.length());
  }

  private SecretStoreUnavailableException listingFailed(final RuntimeException e) {
    final var hint = isListingDenied(e) ? ". To list secrets, " + LIST_PERMISSION_HINT : "";
    return AzureKeyVaultErrors.listingUnavailable(storeDescription, e, hint);
  }

  // a 403 from the vault's network rules is not about the identity's permissions
  private static boolean isListingDenied(final RuntimeException e) {
    return e instanceof final HttpResponseException http
        && !(e instanceof ClientAuthenticationException)
        && http.getResponse() != null
        && http.getResponse().getStatusCode() == 403
        && !AzureKeyVaultErrors.isRefusedByNetworkRules(http);
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
    return LIST_PERMISSION_HINT
        + ". Listing is not permitted; resolution may still work if the identity has the 'get'"
        + " permission";
  }

  @Override
  public boolean accessDeniedFailsResolution() {
    return false;
  }
}
