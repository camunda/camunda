/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.secretstore.azure;

import static io.camunda.secretstore.SecretErrorCode.INVALID_REF;
import static io.camunda.secretstore.SecretErrorCode.NOT_FOUND;
import static io.camunda.secretstore.azure.AzureKeyVaultErrors.VALID_SECRET_NAME;
import static io.camunda.secretstore.azure.AzureKeyVaultErrors.classify;
import static io.camunda.secretstore.azure.AzureKeyVaultErrors.messageFor;
import static io.camunda.secretstore.azure.AzureKeyVaultErrors.storeUnavailable;

import com.azure.core.exception.HttpResponseException;
import com.azure.security.keyvault.secrets.SecretClient;
import com.azure.security.keyvault.secrets.models.KeyVaultSecret;
import com.azure.security.keyvault.secrets.models.SecretProperties;
import io.camunda.secretstore.SecretResolutionResult;
import io.camunda.secretstore.SecretResolutionResult.Failed;
import io.camunda.secretstore.SecretResolutionResult.Resolved;
import java.time.Instant;
import java.time.InstantSource;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default resolution mode: each reference name maps 1:1 to a Key Vault secret name ({@code
 * pathPrefix + name}). Issues one {@code getSecret} call per reference against the latest enabled
 * version.
 *
 * <p>A secret that exists but is outside its validity window — past its {@code expiresOn} or before
 * its {@code notBefore} — is reported as {@link io.camunda.secretstore.SecretErrorCode#NOT_FOUND}
 * rather than returned, so a rotated-out value is never handed to a caller.
 */
final class OneByOneSecretResolver implements AzureSecretResolver {

  private static final Logger LOG = LoggerFactory.getLogger(OneByOneSecretResolver.class);

  private final SecretClient client;
  private final String pathPrefix;
  private final InstantSource clock;

  OneByOneSecretResolver(
      final SecretClient client, final String pathPrefix, final InstantSource clock) {
    this.client = client;
    this.pathPrefix = pathPrefix;
    this.clock = clock;
  }

  @Override
  public Map<String, SecretResolutionResult> resolve(final Set<String> names) {
    if (names.isEmpty()) {
      return Map.of();
    }
    LOG.debug("Resolving {} secret refs from Azure Key Vault", names.size());
    final Map<String, SecretResolutionResult> results = new LinkedHashMap<>(names.size());
    for (final var name : names) {
      results.put(name, resolveSingle(name));
    }
    return results;
  }

  @Override
  public List<String> list() {
    LOG.debug("Listing secrets from Azure Key Vault with prefix '{}'", pathPrefix);
    final var names = new ArrayList<String>();
    try {
      for (final SecretProperties props : client.listPropertiesOfSecrets()) {
        final var secretName = props.getName();
        if (secretName.startsWith(pathPrefix)) {
          final var logicalName = secretName.substring(pathPrefix.length());
          if (!logicalName.isBlank()) {
            names.add(logicalName);
          }
        }
      }
      return names;
    } catch (final RuntimeException e) {
      throw storeUnavailable(e);
    }
  }

  @Override
  public int namesPerCall() {
    return 1;
  }

  @Override
  public void validateConnectivity() {
    // one-by-one mode resolves via getSecret and lists via listPropertiesOfSecrets; triggering the
    // first page of the list is the cheapest probe that exercises this mode's access footprint.
    client.listPropertiesOfSecrets().iterator().hasNext();
  }

  private SecretResolutionResult resolveSingle(final String name) {
    final var secretName = pathPrefix + name;
    if (!VALID_SECRET_NAME.matcher(secretName).matches()) {
      return new Failed(INVALID_REF, messageFor(INVALID_REF, "secret", secretName), null);
    }
    try {
      final KeyVaultSecret secret = client.getSecret(secretName);
      final var withinWindow = withinValidityWindow(secret.getProperties());
      if (!withinWindow || secret.getValue() == null) {
        return new Failed(NOT_FOUND, messageFor(NOT_FOUND, "secret", secretName), null);
      }
      return new Resolved(secret.getValue());
    } catch (final HttpResponseException e) {
      final var code = classify(e);
      return new Failed(code, messageFor(code, "secret", secretName), e);
    } catch (final RuntimeException e) {
      throw storeUnavailable(e);
    }
  }

  /**
   * An expired or not-yet-active secret still returns a value from Key Vault, so the validity
   * window is enforced here: a value outside it is treated as absent rather than handed back.
   */
  private boolean withinValidityWindow(final SecretProperties props) {
    final Instant now = clock.instant();
    final OffsetDateTime expiresOn = props.getExpiresOn();
    if (expiresOn != null && !now.isBefore(expiresOn.toInstant())) {
      return false;
    }
    final OffsetDateTime notBefore = props.getNotBefore();
    return notBefore == null || !now.isBefore(notBefore.toInstant());
  }
}
