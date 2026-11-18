/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.secrets;

import io.camunda.secretstore.refresh.RefreshableClient;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/** The clients of each physical tenant that take new configuration values while they run. */
public final class RefreshableClients {

  private final Map<String, List<RefreshableClient>> byPhysicalTenant = new ConcurrentHashMap<>();

  /**
   * Registers a client of the given physical tenant, to be passed the new values of its properties.
   * Closing the returned registration stops this.
   */
  public AutoCloseable register(final String physicalTenantId, final RefreshableClient client) {
    final var clients =
        byPhysicalTenant.computeIfAbsent(physicalTenantId, id -> new CopyOnWriteArrayList<>());
    clients.add(client);
    return () -> clients.remove(client);
  }

  public List<RefreshableClient> forPhysicalTenant(final String physicalTenantId) {
    return List.copyOf(byPhysicalTenant.getOrDefault(physicalTenantId, List.of()));
  }
}
