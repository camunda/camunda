/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.eventplane.consumer;

import io.camunda.zeebe.eventplane.store.InvalidObjectNameException;
import io.camunda.zeebe.eventplane.store.ObjectNames;
import io.camunda.zeebe.eventplane.store.ObjectStore;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * Finds the logs that a subscription covers, by listing the store's directories: clusters below the
 * root, except what starts with {@code _}, such as {@code _groups/}; tenants below a cluster; and
 * partitions below a tenant, which have numeric names.
 */
final class LogDiscovery {

  private static final Pattern PARTITION = Pattern.compile("[1-9][0-9]{0,8}");

  private LogDiscovery() {}

  static Set<LogId> discover(final ObjectStore store, final Collection<Subscription> subscription)
      throws IOException {
    final var logs = new TreeSet<LogId>(LogDiscovery::compare);
    for (final var entry : subscription) {
      for (final var cluster : clusters(store, entry.cluster())) {
        for (final var tenant : tenants(store, cluster, entry.tenant())) {
          addPartitions(store, cluster, tenant, logs);
        }
      }
    }
    return logs;
  }

  private static void addPartitions(
      final ObjectStore store, final String cluster, final String tenant, final Set<LogId> logs)
      throws IOException {
    for (final var directory : store.listPrefixes(ObjectNames.tenantPrefix(cluster, tenant))) {
      final var name = lastName(directory);
      if (PARTITION.matcher(name).matches()) {
        logs.add(new LogId(cluster, tenant, Integer.parseInt(name)));
      }
    }
  }

  private static List<String> clusters(final ObjectStore store, final String cluster)
      throws IOException {
    if (!Subscription.ANY.equals(cluster)) {
      return List.of(cluster);
    }
    final var clusters = new ArrayList<String>();
    for (final var directory : store.listPrefixes("")) {
      final var name = lastName(directory);
      if (!name.startsWith("_")) {
        decoded(name, clusters);
      }
    }
    return clusters;
  }

  private static List<String> tenants(
      final ObjectStore store, final String cluster, final String tenant) throws IOException {
    if (!Subscription.ANY.equals(tenant)) {
      return List.of(tenant);
    }
    final var tenants = new ArrayList<String>();
    for (final var directory : store.listPrefixes(ObjectNames.clusterPrefix(cluster))) {
      decoded(lastName(directory), tenants);
    }
    return tenants;
  }

  /** Adds a name that a key holds percent-encoded; skips one that isn't. */
  private static void decoded(final String encoded, final List<String> names) {
    try {
      names.add(ObjectNames.decode(encoded));
    } catch (final InvalidObjectNameException e) {
      // Not a directory that the Event Plane wrote.
    }
  }

  /** The last name of a directory's key, which ends in a slash. */
  private static String lastName(final String directory) {
    final var trimmed = directory.substring(0, directory.length() - 1);
    return trimmed.substring(trimmed.lastIndexOf('/') + 1);
  }

  private static int compare(final LogId a, final LogId b) {
    final int cluster = a.cluster().compareTo(b.cluster());
    if (cluster != 0) {
      return cluster;
    }
    final int tenant = a.tenant().compareTo(b.tenant());
    return tenant != 0 ? tenant : Integer.compare(a.partition(), b.partition());
  }
}
