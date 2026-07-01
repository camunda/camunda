/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.qa.util.multidb;

import io.camunda.client.CamundaClient;
import java.util.Collections;
import java.util.Map;
import org.agrona.CloseHelper;
import org.jspecify.annotations.NullMarked;

/**
 * Holder for per-physical-tenant admin {@link CamundaClient}s provisioned by {@link
 * CamundaMultiDBExtension} when the test class is annotated with {@link MultiDbPhysicalTenants}.
 *
 * <p>The extension injects this into a {@code static MultiPhysicalTenantClients} field on the test
 * class. Tests obtain a per-PT admin client via {@link #admin(String)}.
 */
@NullMarked
public final class MultiPhysicalTenantClients implements AutoCloseable {

  private final Map<String, CamundaClient> adminClients;

  MultiPhysicalTenantClients(final Map<String, CamundaClient> adminClients) {
    this.adminClients = Collections.unmodifiableMap(adminClients);
  }

  /**
   * Returns the admin {@link CamundaClient} for the given physical tenant ID.
   *
   * @throws IllegalArgumentException if no client was provisioned for that tenant
   */
  public CamundaClient admin(final String physicalTenantId) {
    final CamundaClient client = adminClients.get(physicalTenantId);
    if (client == null) {
      throw new IllegalArgumentException(
          "No admin client provisioned for physical tenant '" + physicalTenantId + "'");
    }
    return client;
  }

  @Override
  public void close() {
    CloseHelper.quietCloseAll(adminClients.values());
  }
}
