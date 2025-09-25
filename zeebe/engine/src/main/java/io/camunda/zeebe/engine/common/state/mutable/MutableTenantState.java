/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.common.state.mutable;

import io.camunda.zeebe.engine.common.state.immutable.TenantState;
import io.camunda.zeebe.protocol.impl.record.value.tenant.TenantRecord;

public interface MutableTenantState extends TenantState {
  /**
   * Creates a tenant in the state.
   *
   * @param tenantRecord the tenant record to add
   */
  void createTenant(final TenantRecord tenantRecord);

  /**
   * Updates the tenant record associated with the given tenant key.
   *
   * @param updatedTenantRecord the updated tenant record with new values
   */
  void updateTenant(final TenantRecord updatedTenantRecord);

  /**
   * Deletes a tenant and all associated data from the state.
   *
   * @param tenantRecord the tenant record representing the tenant to delete
   */
  void delete(final TenantRecord tenantRecord);
}
