/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.search.contract;

import static java.util.Optional.ofNullable;

import io.camunda.gateway.mapping.http.util.KeyUtil;
import io.camunda.search.filter.DecisionRequirementsFilter;
import io.camunda.search.filter.FilterBuilders;
import org.jspecify.annotations.NullMarked;

@NullMarked
public final class DecisionRequirementsFilterMapper {

  private DecisionRequirementsFilterMapper() {}

  public static DecisionRequirementsFilter toDecisionRequirementsFilter(
      final io.camunda.gateway.protocol.model.DecisionRequirementsFilter filter) {
    final var builder = FilterBuilders.decisionRequirements();
    if (filter != null) {
      ofNullable(filter.getDecisionRequirementsKey())
          .map(KeyUtil::keyToLong)
          .ifPresent(builder::decisionRequirementsKeys);
      ofNullable(filter.getDecisionRequirementsName()).ifPresent(builder::names);
      ofNullable(filter.getVersion()).ifPresent(builder::versions);
      ofNullable(filter.getDecisionRequirementsId()).ifPresent(builder::decisionRequirementsIds);
      ofNullable(filter.getTenantId()).ifPresent(builder::tenantIds);
      ofNullable(filter.getResourceName()).ifPresent(builder::resourceNames);
    }
    return builder.build();
  }
}
