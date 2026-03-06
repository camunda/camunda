/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.auth.domain.spi;

import io.camunda.auth.domain.model.TenantInfo;
import java.util.List;

/**
 * SPI for resolving tenant details from tenant IDs. Implementations typically query a tenant
 * database.
 */
public interface TenantInfoProvider {
  /** Resolves tenant info (id + name) for the given tenant IDs. */
  List<TenantInfo> getTenants(List<String> tenantIds);
}
