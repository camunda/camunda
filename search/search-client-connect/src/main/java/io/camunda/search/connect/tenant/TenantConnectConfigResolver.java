/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.connect.tenant;

import io.camunda.search.connect.configuration.ConnectConfiguration;
import java.util.Map;

/**
 * Resolves the per-physical-tenant {@link ConnectConfiguration} for secondary storage (ES/OS).
 *
 * <p>Currently this is a thin wrapper around a pre-built tenant-id to {@link ConnectConfiguration}
 * map. When physical tenant isolation is implemented, this record can be replaced by a richer type
 * that reads from {@code camunda.physical-tenants} configuration.
 */
public record TenantConnectConfigResolver(Map<String, ConnectConfiguration> tenantConfigs) {

  public static final String DEFAULT_TENANT_ID = "default";

  public TenantConnectConfigResolver {
    tenantConfigs = Map.copyOf(tenantConfigs);
  }
}
