/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.secretstore.hashicorpvault;

import static io.camunda.secretstore.SecretErrorCode.INVALID_REF;
import static io.camunda.secretstore.SecretErrorCode.NOT_FOUND;

import io.camunda.secretstore.SecretErrorCode;
import io.camunda.secretstore.SecretResolutionResult;
import io.camunda.secretstore.SecretResolutionResult.Failed;
import io.camunda.secretstore.SecretResolutionResult.Resolved;
import io.camunda.secretstore.SecretStore;
import io.camunda.secretstore.SecretStoreUnavailableException;
import io.github.jopenlibs.vault.Vault;
import io.github.jopenlibs.vault.VaultException;
import io.github.jopenlibs.vault.json.JsonObject;
import io.github.jopenlibs.vault.response.LogicalResponse;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A {@link SecretStore} backed by a HashiCorp Vault (or OpenBao) KV secrets engine, version 1 or 2.
 *
 * <p>Two modes, mirroring the AWS store:
 *
 * <ul>
 *   <li><b>per-entry</b> (default): the reference {@code name} reads the KV entry {@code
 *       <mount>/<pathPrefix><name>} and returns its {@code field} ({@code value} by default). One
 *       HTTP call per name.
 *   <li><b>container</b> ({@code containerPath} set): one KV entry holds all secrets, each as one
 *       of its fields; the reference {@code name} is the field name. One HTTP call per resolution.
 * </ul>
 *
 * <p>Error mapping: 404 and a missing field are {@link SecretErrorCode#NOT_FOUND}, 403 is {@link
 * SecretErrorCode#ACCESS_DENIED} (after signing in again once, in case the token was revoked), 400
 * and a non-string field are {@link SecretErrorCode#INVALID_REF}. Sealed, standby, rate limited,
 * server errors, and I/O failures throw {@link SecretStoreUnavailableException}.
 */
public final class HashiCorpVaultSecretStore implements SecretStore {

  private static final Logger LOG = LoggerFactory.getLogger(HashiCorpVaultSecretStore.class);

  private final HashiCorpVaultStoreConfig config;
  private final HashiCorpVaultClients clients;
  private final HashiCorpVaultSession session;

  HashiCorpVaultSecretStore(
      final HashiCorpVaultStoreConfig config,
      final String storeName,
      final MeterRegistry meterRegistry,
      final Tags tags,
      final Function<String, @Nullable String> environment) {
    this.config = config;
    clients = new HashiCorpVaultClients(config);
    session =
        new HashiCorpVaultSession(config, clients, storeName, meterRegistry, tags, environment);
  }

  /**
   * Builds a store from configuration and signs in eagerly. Like the AWS and GCP stores, a failure
   * to reach HashiCorp Vault or to sign in is only logged: the store is still created, keeps trying
   * to sign in in the background, and the error surfaces on the first {@link #resolve} call. A
   * transient outage during boot therefore cannot stop the application from starting.
   *
   * @param storeName used in logs and the session thread name, e.g. {@code tenant/store-id}
   * @param tags added to the store's metrics, e.g. the physical tenant and store id
   */
  public static HashiCorpVaultSecretStore fromConfig(
      final HashiCorpVaultStoreConfig config,
      final String storeName,
      final MeterRegistry meterRegistry,
      final Tags tags) {
    final var store =
        new HashiCorpVaultSecretStore(config, storeName, meterRegistry, tags, System::getenv);
    store.validateConnectivity();
    return store;
  }

  void validateConnectivity() {
    try {
      session.vault();
      if (config.containerPath() != null) {
        readEntry(config.entryPath(config.containerPath()));
      }
    } catch (final RuntimeException e) {
      LOG.warn(
          "Failed to validate HashiCorp Vault connectivity/credentials at {} at startup; the store"
              + " will still be created and the error will surface on first use: {}",
          config.address(),
          e.getMessage());
    }
  }

  @Override
  public Map<String, SecretResolutionResult> resolve(final Set<String> names) {
    if (names.isEmpty()) {
      return Map.of();
    }
    return config.containerPath() != null
        ? resolveFromContainer(names, config.entryPath(config.containerPath()))
        : resolvePerEntry(names);
  }

  @Override
  public List<String> list() {
    if (config.containerPath() != null) {
      final var entryPath = config.entryPath(config.containerPath());
      final var response = readEntry(entryPath);
      final var status = response.getRestResponse().getStatus();
      if (status == 404) {
        return List.of();
      }
      if (status != 200) {
        throw new SecretStoreUnavailableException(
            "Failed to list HashiCorp Vault container '" + entryPath + "': HTTP " + status);
      }
      final var data = response.getDataObject();
      return data == null ? List.of() : List.copyOf(data.names());
    }
    return listEntries();
  }

  /** One HTTP call per name: {@code resolve} must not hold the IO thread for long batches. */
  @Override
  public int namesPerCall() {
    return config.containerPath() != null ? Integer.MAX_VALUE : 1;
  }

  @Override
  public void close() {
    try {
      session.close();
    } finally {
      clients.close();
    }
  }

  private Map<String, SecretResolutionResult> resolvePerEntry(final Set<String> names) {
    final Map<String, SecretResolutionResult> results = new LinkedHashMap<>(names.size());
    for (final var name : names) {
      if (!HashiCorpVaultErrors.isValidName(name)) {
        results.put(
            name,
            new Failed(
                INVALID_REF,
                "Secret name '"
                    + name
                    + "' is not a valid HashiCorp Vault entry name (allowed: letters, digits,"
                    + " '.', '_', '-')",
                null));
        continue;
      }
      final var entryPath = config.entryPath(name);
      final var response = readEntry(entryPath);
      final var status = response.getRestResponse().getStatus();
      if (status != 200) {
        final var code = HashiCorpVaultErrors.classify(status, "entry '" + entryPath + "'");
        results.put(name, new Failed(code, messageFor(code, entryPath), null));
        continue;
      }
      results.put(name, extractField(response.getDataObject(), config.field(), entryPath));
    }
    return results;
  }

  private Map<String, SecretResolutionResult> resolveFromContainer(
      final Set<String> names, final String entryPath) {
    final var response = readEntry(entryPath);
    final var status = response.getRestResponse().getStatus();
    final Map<String, SecretResolutionResult> results = new LinkedHashMap<>(names.size());
    if (status != 200) {
      final var code = HashiCorpVaultErrors.classify(status, "container '" + entryPath + "'");
      final var message = messageFor(code, entryPath);
      names.forEach(name -> results.put(name, new Failed(code, message, null)));
      return results;
    }
    final var data = response.getDataObject();
    for (final var name : names) {
      results.put(name, extractField(data, name, entryPath));
    }
    return results;
  }

  /**
   * Reads one KV entry, signing in again and retrying once on 403: the token may have been revoked
   * or expired between renewals. A second 403 means the policy denies the path.
   *
   * @return the response for a 2xx or 4xx status
   * @throws SecretStoreUnavailableException on a 5xx, I/O failure, or failed sign-in
   */
  private LogicalResponse readEntry(final String entryPath) {
    var vault = session.vault();
    var response = readOnce(vault, entryPath);
    if (response.getRestResponse().getStatus() == 403) {
      LOG.debug("HashiCorp Vault denied '{}'; signing in again and retrying once", entryPath);
      vault = session.relogin(vault);
      response = readOnce(vault, entryPath);
    }
    return response;
  }

  private LogicalResponse readOnce(final Vault vault, final String entryPath) {
    try {
      final var response = vault.logical().read(clients.fullPath(entryPath));
      if (response == null || response.getRestResponse() == null) {
        throw new SecretStoreUnavailableException(
            "HashiCorp Vault returned no response reading '" + entryPath + "'");
      }
      return response;
    } catch (final VaultException e) {
      throw HashiCorpVaultErrors.storeUnavailable(e);
    }
  }

  private List<String> listEntries() {
    final var prefix = config.pathPrefix() == null ? "" : config.pathPrefix();
    final var listPath =
        prefix.isEmpty()
            ? config.mount()
            : clients.fullPath(
                prefix.endsWith("/") ? prefix.substring(0, prefix.length() - 1) : prefix);
    final LogicalResponse response;
    try {
      response = session.vault().logical().list(listPath);
    } catch (final VaultException e) {
      throw HashiCorpVaultErrors.storeUnavailable(e);
    }
    if (response == null) {
      return List.of();
    }
    final var status = response.getRestResponse().getStatus();
    if (status == 404) {
      return List.of();
    }
    if (status != 200) {
      throw new SecretStoreUnavailableException(
          "Failed to list HashiCorp Vault entries under '" + listPath + "': HTTP " + status);
    }
    final var names = new ArrayList<String>();
    for (final var key : response.getListData()) {
      // sub-paths end with '/' and are not secrets of this store
      if (HashiCorpVaultErrors.isValidName(key)) {
        names.add(key);
      }
    }
    return names;
  }

  private static SecretResolutionResult extractField(
      final @Nullable JsonObject data, final String field, final String entryPath) {
    // a KV v2 entry whose latest version is deleted reads as 200 with "data": null
    final var value = data == null ? null : data.get(field);
    if (value == null || value.isNull()) {
      return new Failed(
          NOT_FOUND,
          "Field '" + field + "' not found in HashiCorp Vault entry '" + entryPath + "'",
          null);
    }
    if (!value.isString()) {
      return new Failed(
          INVALID_REF,
          "Field '" + field + "' in HashiCorp Vault entry '" + entryPath + "' is not a string",
          null);
    }
    return new Resolved(value.asString());
  }

  private static String messageFor(final SecretErrorCode code, final String entryPath) {
    return switch (code) {
      case NOT_FOUND -> "HashiCorp Vault entry '" + entryPath + "' not found";
      case ACCESS_DENIED ->
          "Access denied to HashiCorp Vault entry '"
              + entryPath
              + "'; check the policy attached to the store's role";
      case INVALID_REF -> "HashiCorp Vault rejected the path of entry '" + entryPath + "'";
      default -> "Failed to read HashiCorp Vault entry '" + entryPath + "': " + code;
    };
  }
}
