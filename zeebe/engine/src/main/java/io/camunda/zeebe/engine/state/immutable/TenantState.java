/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.immutable;

import io.camunda.zeebe.protocol.impl.record.value.tenant.TenantRecord;
import java.util.Optional;

public interface TenantState {

  /**
   * Retrieves a tenant record by its key.
   *
   * @param tenantKey the key of the tenant to retrieve
   * @return an Optional containing the tenant record if it exists, otherwise an empty Optional
   */
  Optional<TenantRecord> getTenantByKey(long tenantKey);

  /**
   * Retrieves a tenant key by its ID.
   *
   * @param tenantId the ID of the tenant to look up
   * @return the key of the tenant if it exists, otherwise null
   */
  Long getTenantKeyById(String tenantId);
}
