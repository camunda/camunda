/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.search.contract;

import static java.util.Optional.ofNullable;

import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedDecisionDefinitionFilterStrictContract;
import io.camunda.gateway.mapping.http.util.KeyUtil;
import io.camunda.search.filter.DecisionDefinitionFilter;
import io.camunda.search.filter.FilterBuilders;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public final class DecisionDefinitionFilterMapper {

  private DecisionDefinitionFilterMapper() {}

  public static DecisionDefinitionFilter toDecisionDefinitionFilter(
      @Nullable final GeneratedDecisionDefinitionFilterStrictContract filter) {
    final var builder = FilterBuilders.decisionDefinition();
    if (filter != null) {
      ofNullable(filter.decisionDefinitionKey())
          .map(KeyUtil::keyToLong)
          .ifPresent(builder::decisionDefinitionKeys);
      ofNullable(filter.decisionDefinitionId()).ifPresent(builder::decisionDefinitionIds);
      ofNullable(filter.name()).ifPresent(builder::names);
      ofNullable(filter.isLatestVersion()).ifPresent(builder::isLatestVersion);
      ofNullable(filter.version()).ifPresent(builder::versions);
      ofNullable(filter.decisionRequirementsId()).ifPresent(builder::decisionRequirementsIds);
      ofNullable(filter.decisionRequirementsKey())
          .map(KeyUtil::keyToLong)
          .ifPresent(builder::decisionRequirementsKeys);
      ofNullable(filter.decisionRequirementsName()).ifPresent(builder::decisionRequirementsNames);
      ofNullable(filter.decisionRequirementsVersion())
          .ifPresent(builder::decisionRequirementsVersions);
      ofNullable(filter.tenantId()).ifPresent(builder::tenantIds);
    }
    return builder.build();
  }
}
