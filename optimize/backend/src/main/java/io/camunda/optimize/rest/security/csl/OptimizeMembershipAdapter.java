/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest.security.csl;

import io.camunda.security.core.port.out.MembershipPort;
import io.camunda.security.core.port.out.MembershipQuery;
import java.util.List;

/**
 * Stub {@link MembershipPort} for Optimize's CSL wiring: CSL's lazy claim converters require a
 * {@code MembershipPort} bean to build their chains, and CSL ships no default.
 *
 * <p>Optimize has no live group/role/tenant membership feature to preserve, so every lookup returns
 * an empty list — matching the behaviour of the legacy setup, which resolved no memberships either.
 */
public final class OptimizeMembershipAdapter implements MembershipPort {

  @Override
  public List<String> mappingRuleIds(final MembershipQuery query) {
    return List.of();
  }

  @Override
  public List<String> groupIds(final MembershipQuery query) {
    return List.of();
  }

  @Override
  public List<String> roleIds(final MembershipQuery query) {
    return List.of();
  }

  @Override
  public List<String> tenantIds(final MembershipQuery query) {
    return List.of();
  }
}
