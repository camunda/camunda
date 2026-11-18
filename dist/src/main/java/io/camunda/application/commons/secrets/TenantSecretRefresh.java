/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.secrets;

import io.camunda.application.commons.secrets.ConfigurationSecretRefreshReport.Failure;
import io.camunda.application.commons.secrets.ConfigurationSecretRefreshReport.Reason;
import io.camunda.configuration.ConfigurationSecretReference;
import io.camunda.secretstore.SecretErrorCode;
import io.camunda.secretstore.SecretResolutionResult.Failed;
import io.camunda.secretstore.SecretResolutionResult.Resolved;
import io.camunda.secretstore.SecretStore;
import io.camunda.secretstore.SecretStoreException;
import io.camunda.secretstore.refresh.RefreshableClient;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reads the secret references of one physical tenant again, and passes changed values on to the
 * clients that use them. A value that can't be read keeps its current value, and the next read is
 * brought forward with exponential backoff.
 *
 * <p>Not thread-safe: {@link #run()} must not be called concurrently.
 */
final class TenantSecretRefresh {

  static final Duration INITIAL_BACKOFF = Duration.ofSeconds(10);
  static final String STARTUP = "startup";

  private static final Logger LOG = LoggerFactory.getLogger(TenantSecretRefresh.class);

  private final String physicalTenantId;
  private final Duration interval;
  private final Supplier<Optional<SecretStore>> store;
  private final Map<String, ConfigurationSecretReference> references;
  private final Map<String, String> configuration;
  private final Supplier<List<RefreshableClient>> clients;
  private final ConfigurationSecretRefreshReport report;

  private final Map<String, String> latest = new HashMap<>();
  private final Map<RefreshableClient, Applied> applied = new HashMap<>();
  private final Map<String, String> readOnStartup = new HashMap<>();
  private Duration backoff = Duration.ZERO;

  /**
   * @param references the reference behind each property that held one
   * @param configuration the value of each property as the tenant's clients were built with it
   */
  TenantSecretRefresh(
      final String physicalTenantId,
      final Duration interval,
      final Supplier<Optional<SecretStore>> store,
      final Map<String, ConfigurationSecretReference> references,
      final Map<String, String> configuration,
      final Supplier<List<RefreshableClient>> clients,
      final ConfigurationSecretRefreshReport report) {
    this.physicalTenantId = physicalTenantId;
    this.interval = interval;
    this.store = store;
    this.references = Map.copyOf(references);
    this.configuration = Map.copyOf(configuration);
    this.clients = clients;
    this.report = report;
    references.forEach(
        (property, reference) -> {
          final var value = configuration.get(property);
          if (value != null) {
            latest.put(reference.name(), value);
            readOnStartup.put(reference.name(), value);
          }
        });
  }

  /**
   * Reads every reference, and refreshes the clients whose values changed.
   *
   * @return how long to wait until the next run
   */
  Duration run() {
    final var unread = read();
    final var registered = clients.get();
    applied.keySet().retainAll(new HashSet<>(registered));
    final Set<Failure> failures = new HashSet<>();
    final Set<String> usedByClients = new TreeSet<>();
    for (final var client : registered) {
      refresh(client, failures);
      client.properties().stream()
          .map(references::get)
          .filter(Objects::nonNull)
          .forEach(
              reference -> {
                usedByClients.add(reference.name());
                final var reason = unread.get(reference.name());
                if (reason != null) {
                  failures.add(new Failure(reference.toString(), client.name(), reason));
                }
              });
    }
    unread.forEach(
        (name, reason) -> {
          if (!usedByClients.contains(name)) {
            failures.add(new Failure(ConfigurationSecretReference.PREFIX + name, STARTUP, reason));
          }
        });
    reportChangesReadOnStartup(usedByClients);
    report.failing(physicalTenantId, failures);

    if (unread.isEmpty()) {
      backoff = Duration.ZERO;
      return interval;
    }
    backoff = backoff.isZero() ? INITIAL_BACKOFF : backoff.multipliedBy(2);
    if (backoff.compareTo(interval) > 0) {
      backoff = interval;
    }
    return backoff;
  }

  /** Returns why each reference that couldn't be read wasn't. */
  private Map<String, Reason> read() {
    final Map<String, Reason> unread = new HashMap<>();
    if (references.isEmpty()) {
      return unread;
    }
    final var names =
        references.values().stream()
            .map(ConfigurationSecretReference::name)
            .collect(Collectors.toCollection(TreeSet::new));
    final var configurationStore = store.get();
    if (configurationStore.isEmpty()) {
      LOG.warn(
          "Physical tenant '{}' has no configuration secrets store to read {} from; keeping their current values",
          physicalTenantId,
          names);
      names.forEach(name -> unread.put(name, Reason.STORE_UNREACHABLE));
      return unread;
    }
    final Map<String, ?> results;
    try {
      results = configurationStore.get().resolve(names);
    } catch (final SecretStoreException e) {
      LOG.warn(
          "Physical tenant '{}' failed to read {} from its configuration secrets store; keeping their current values",
          physicalTenantId,
          names,
          e);
      names.forEach(name -> unread.put(name, Reason.STORE_UNREACHABLE));
      return unread;
    }
    for (final var name : names) {
      switch (results.get(name)) {
        case final Resolved resolved -> latest.put(name, resolved.value());
        case final Failed failure -> {
          unread.put(name, reasonOf(failure.code()));
          LOG.warn(
              "Physical tenant '{}' failed to read secret:{} from its configuration secrets store ({}); keeping its current value",
              physicalTenantId,
              name,
              failure.code());
        }
        case null, default -> {
          unread.put(name, Reason.UNREADABLE);
          LOG.warn(
              "Physical tenant '{}' got no value for secret:{} from its configuration secrets store; keeping its current value",
              physicalTenantId,
              name);
        }
      }
    }
    return unread;
  }

  private static Reason reasonOf(final SecretErrorCode code) {
    return switch (code) {
      case NOT_FOUND -> Reason.NOT_FOUND;
      case ACCESS_DENIED -> Reason.ACCESS_DENIED;
      case INVALID_REF, UNREADABLE -> Reason.UNREADABLE;
    };
  }

  private void refresh(final RefreshableClient client, final Set<Failure> failures) {
    final var current =
        applied.computeIfAbsent(client, c -> new Applied(builtWith(c), hashes(c, Map.of())));
    final var wanted = new Applied(values(client), hashes(client, current.files()));
    if (current.equals(wanted)) {
      return;
    }
    final var changed = changes(current, wanted);
    try {
      client.refresh(wanted.values());
      applied.put(client, wanted);
      changed.forEach(reference -> report.applied(physicalTenantId, reference));
      LOG.info(
          "Physical tenant '{}' applied a change of {} to its {} client",
          physicalTenantId,
          changed,
          client.name());
    } catch (final RuntimeException e) {
      LOG.warn(
          "Physical tenant '{}' failed to apply a change of {} to its {} client; keeping the current one",
          physicalTenantId,
          changed,
          client.name(),
          e);
      changed.forEach(
          reference ->
              failures.add(new Failure(reference, client.name(), Reason.REJECTED_BY_CHECK)));
    }
  }

  /** The values a client was built with on startup. */
  private Map<String, String> builtWith(final RefreshableClient client) {
    final Map<String, String> values = new LinkedHashMap<>();
    for (final var property : client.properties()) {
      final var value = configuration.get(property);
      if (value != null) {
        values.put(property, value);
      }
    }
    return values;
  }

  private Map<String, String> values(final RefreshableClient client) {
    final Map<String, String> values = new LinkedHashMap<>();
    for (final var property : client.properties()) {
      final var reference = references.get(property);
      final var value =
          reference != null ? latest.get(reference.name()) : configuration.get(property);
      if (value != null) {
        values.put(property, value);
      }
    }
    return values;
  }

  private Set<String> changes(final Applied current, final Applied wanted) {
    final Set<String> changed = new TreeSet<>();
    wanted
        .values()
        .forEach(
            (property, value) -> {
              if (!value.equals(current.values().get(property))) {
                final var reference = references.get(property);
                changed.add(reference != null ? reference.toString() : property);
              }
            });
    wanted
        .files()
        .forEach(
            (file, hash) -> {
              if (!hash.equals(current.files().get(file))) {
                changed.add(file.toString());
              }
            });
    return changed;
  }

  private void reportChangesReadOnStartup(final Set<String> usedByClients) {
    readOnStartup.forEach(
        (name, value) -> {
          final var now = latest.get(name);
          if (!usedByClients.contains(name) && now != null && !now.equals(value)) {
            readOnStartup.put(name, now);
            LOG.info(
                "Physical tenant '{}' read a change of secret:{}, which is only read on startup and applies at the next restart",
                physicalTenantId,
                name);
          }
        });
  }

  private Map<Path, String> hashes(
      final RefreshableClient client, final Map<Path, String> previous) {
    final Map<Path, String> hashes = new HashMap<>();
    for (final var file : client.files()) {
      try {
        final var digest = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file));
        hashes.put(file, HexFormat.of().formatHex(digest));
      } catch (final IOException e) {
        LOG.warn(
            "Physical tenant '{}' failed to read {} for its {} client; keeping its current content",
            physicalTenantId,
            file,
            client.name(),
            e);
        final var hash = previous.get(file);
        if (hash != null) {
          hashes.put(file, hash);
        }
      } catch (final NoSuchAlgorithmException e) {
        throw new IllegalStateException(e);
      }
    }
    return hashes;
  }

  private record Applied(Map<String, String> values, Map<Path, String> files) {}
}
