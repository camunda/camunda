/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.common.state.immutable;

import io.camunda.zeebe.engine.common.state.tenant.PersistedTenant;
import java.util.Optional;
import java.util.function.Function;

public interface TenantState {

  /**
   * Loops over all tenants and applies the provided callback. It stops looping over the tenants,
   * when the callback function returns false, otherwise it will continue until all tenants are
   * visited.
   */
  void forEachTenant(final Function<String, Boolean> callback);

  /** Retrieves a tenant record by its ID. */
  Optional<PersistedTenant> getTenantById(String tenantId);
}
