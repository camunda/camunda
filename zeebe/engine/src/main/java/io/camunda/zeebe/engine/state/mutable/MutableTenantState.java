/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.mutable;

import io.camunda.zeebe.engine.state.immutable.TenantState;
import io.camunda.zeebe.protocol.impl.record.value.tenant.TenantRecord;

public interface MutableTenantState extends TenantState {

  /**
   * Creates a new tenant in the state by adding entries to both TENANTS and TENANT_BY_ID column families.
   *
   * @param tenantKey the key of the tenant
   * @param tenantRecord the tenant record to add
   */
  void createTenant(long tenantKey, TenantRecord tenantRecord);

  /**
   * Adds a new tenant to the state.
   *
   * @param tenantKey the key of the tenant
   * @param tenantRecord the tenant record to add
   */
  void addTenant(long tenantKey, TenantRecord tenantRecord);

  /**
   * Updates an existing tenant record.
   *
   * @param tenantKey the key of the tenant to update
   * @param tenantRecord the new tenant record data
   */
  void updateTenant(long tenantKey, TenantRecord tenantRecord);

  /**
   * Removes a tenant from the state.
   *
   * @param tenantKey the key of the tenant to remove
   */
  void removeTenant(long tenantKey);
}
