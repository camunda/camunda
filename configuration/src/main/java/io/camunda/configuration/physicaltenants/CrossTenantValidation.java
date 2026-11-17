/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration.physicaltenants;

import io.camunda.configuration.Camunda;
import io.camunda.configuration.UnifiedConfigurationException;
import java.util.Map;

/**
 * A validation rule that spans more than one resolved physical-tenant {@link Camunda} (as opposed
 * to single-config validation, which lives in the {@code Camunda} getters).
 *
 * <p>The collection signature is deliberate: cross-tenant rules are <em>relational</em> (uniqueness
 * / group-by / aggregate over all tenants), so they need the whole map at once — both to group
 * values and to name the offending tenants in the error message. A pairwise {@code
 * validate(Camunda, Camunda)} signature was rejected: it produces O(n²) redundant collision reports
 * instead of one grouped message and loses the tenant ids.
 */
public interface CrossTenantValidation {

  /**
   * Validates the given resolved-per-tenant configuration.
   *
   * @param resolvedByTenant tenant id → fully-resolved {@link Camunda} (one entry per physical
   *     tenant, including the synthesized {@code default})
   * @throws UnifiedConfigurationException if the rule is violated; the message names the offending
   *     tenants
   */
  void validate(Map<String, Camunda> resolvedByTenant);
}
