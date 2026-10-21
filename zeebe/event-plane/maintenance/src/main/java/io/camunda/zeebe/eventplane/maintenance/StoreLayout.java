/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.eventplane.maintenance;

import io.camunda.zeebe.eventplane.segment.LogId;
import io.camunda.zeebe.eventplane.store.InvalidObjectNameException;
import io.camunda.zeebe.eventplane.store.ObjectNames;
import io.camunda.zeebe.eventplane.store.ObjectStore;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Finds the logs in a store, by listing "directories": the clusters below the root, the tenants
 * below a cluster, the partitions below a tenant, and the histories below a partition.
 */
final class StoreLayout {

  private static final Logger LOG = LoggerFactory.getLogger(StoreLayout.class);
  private static final Pattern PARTITION = Pattern.compile("[1-9][0-9]{0,8}");

  private final ObjectStore store;

  StoreLayout(final ObjectStore store) {
    this.store = store;
  }

  /** The clusters, without the directory of consumer groups and anything else that starts in _. */
  List<String> clusters() throws IOException {
    return names("").stream().filter(name -> !name.startsWith("_")).toList();
  }

  /** The tenants of a cluster. */
  List<String> tenants(final String cluster) throws IOException {
    return names(ObjectNames.clusterPrefix(cluster));
  }

  /** The logs of a tenant, by partition: its directories with a number, not {@code histories}. */
  List<LogId> logs(final String cluster, final String tenant) throws IOException {
    final var logs = new ArrayList<LogId>();
    for (final var name : names(ObjectNames.tenantPrefix(cluster, tenant))) {
      if (PARTITION.matcher(name).matches()) {
        logs.add(new LogId(cluster, tenant, Integer.parseInt(name)));
      }
    }
    return logs;
  }

  /**
   * The histories that have segments in a log: its directories named like a UUID, not {@code
   * retention} or {@code snapshots}.
   */
  List<UUID> histories(final LogId log) throws IOException {
    final var histories = new ArrayList<UUID>();
    for (final var name : names(ObjectNames.logPrefix(log))) {
      final var history = parseHistory(name);
      if (history != null) {
        histories.add(history);
      }
    }
    return histories;
  }

  /** The names of the directories directly below a prefix, decoded. */
  private List<String> names(final String prefix) throws IOException {
    final var names = new ArrayList<String>();
    for (final var key : store.listPrefixes(prefix)) {
      final var encoded = key.substring(prefix.length(), key.length() - 1);
      try {
        final var name = ObjectNames.decode(encoded);
        // No log has a blank cluster or tenant, so it is not a directory that the Event Plane
        // wrote.
        if (!name.isBlank()) {
          names.add(name);
        }
      } catch (final InvalidObjectNameException e) {
        LOG.debug(
            "Skipping the directory '{}', which is not a name that the Event Plane wrote", key);
      }
    }
    return names;
  }

  private static @Nullable UUID parseHistory(final String name) {
    try {
      final var history = UUID.fromString(name);
      // UUID.fromString accepts shorter groups, which no history is named with.
      return history.toString().equals(name) ? history : null;
    } catch (final IllegalArgumentException e) {
      return null;
    }
  }
}
