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
import io.camunda.search.filter.DecisionDefinitionFilter;
import io.camunda.search.filter.FilterBuilders;
import org.jspecify.annotations.NullMarked;

@NullMarked
public final class DecisionDefinitionFilterMapper {

  private DecisionDefinitionFilterMapper() {}

  public static DecisionDefinitionFilter toDecisionDefinitionFilter(
      final io.camunda.gateway.protocol.model.DecisionDefinitionFilter filter) {
    final var builder = FilterBuilders.decisionDefinition();
    if (filter != null) {
      ofNullable(filter.getDecisionDefinitionKey())
          .map(KeyUtil::keyToLong)
          .ifPresent(builder::decisionDefinitionKeys);
      ofNullable(filter.getDecisionDefinitionId()).ifPresent(builder::decisionDefinitionIds);
      ofNullable(filter.getName()).ifPresent(builder::names);
      ofNullable(filter.getIsLatestVersion()).ifPresent(builder::isLatestVersion);
      ofNullable(filter.getVersion()).ifPresent(builder::versions);
      ofNullable(filter.getDecisionRequirementsId()).ifPresent(builder::decisionRequirementsIds);
      ofNullable(filter.getDecisionRequirementsKey())
          .map(KeyUtil::keyToLong)
          .ifPresent(builder::decisionRequirementsKeys);
      ofNullable(filter.getDecisionRequirementsName())
          .ifPresent(builder::decisionRequirementsNames);
      ofNullable(filter.getDecisionRequirementsVersion())
          .ifPresent(builder::decisionRequirementsVersions);
      ofNullable(filter.getTenantId()).ifPresent(builder::tenantIds);
    }
    return builder.build();
  }
}
