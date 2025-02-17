/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.identity.dto;

import java.util.Set;

public class TenantMappingRule extends MappingRule {
  private Set<Tenant> appliedTenants;

  public TenantMappingRule() {}

  public TenantMappingRule(
      final String name,
      final String claimName,
      final String claimValue,
      final Operator operator,
      final Set<Tenant> appliedTenants) {
    super(MappingRuleType.TENANT, name, claimName, claimValue, operator);
    this.appliedTenants = appliedTenants;
  }

  public Set<Tenant> getAppliedTenants() {
    return appliedTenants;
  }

  public void setAppliedTenants(final Set<Tenant> appliedTenants) {
    this.appliedTenants = appliedTenants;
  }
}
