/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.search.contract;

import static io.camunda.gateway.mapping.http.util.AdvancedSearchFilterUtil.mapToOperations;
import static java.util.Optional.ofNullable;

import io.camunda.gateway.mapping.http.search.contract.generated.ElementInstanceFilterContract;
import io.camunda.gateway.mapping.http.util.KeyUtil;
import io.camunda.search.entities.FlowNodeInstanceEntity.FlowNodeType;
import io.camunda.search.filter.FilterBuilders;
import io.camunda.search.filter.FlowNodeInstanceFilter;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public final class ElementInstanceFilterMapper {

  private ElementInstanceFilterMapper() {}

  public static FlowNodeInstanceFilter toElementInstanceFilter(
      @Nullable final ElementInstanceFilterContract filter) {
    final var builder = FilterBuilders.flowNodeInstance();
    Optional.ofNullable(filter)
        .ifPresent(
            f -> {
              ofNullable(f.elementInstanceKey())
                  .map(KeyUtil::keyToLong)
                  .ifPresent(builder::flowNodeInstanceKeys);
              ofNullable(f.processInstanceKey())
                  .map(KeyUtil::keyToLong)
                  .ifPresent(builder::processInstanceKeys);
              ofNullable(f.processDefinitionKey())
                  .map(KeyUtil::keyToLong)
                  .ifPresent(builder::processDefinitionKeys);
              ofNullable(f.processDefinitionId()).ifPresent(builder::processDefinitionIds);
              ofNullable(f.state())
                  .map(mapToOperations(String.class))
                  .ifPresent(builder::stateOperations);
              ofNullable(f.type())
                  .ifPresent(t -> builder.types(FlowNodeType.fromZeebeBpmnElementType(t)));
              ofNullable(f.elementId()).ifPresent(builder::flowNodeIds);
              ofNullable(f.elementName()).ifPresent(builder::flowNodeNames);
              ofNullable(f.hasIncident()).ifPresent(builder::hasIncident);
              ofNullable(f.incidentKey()).map(KeyUtil::keyToLong).ifPresent(builder::incidentKeys);
              ofNullable(f.tenantId()).ifPresent(builder::tenantIds);
              ofNullable(f.startDate())
                  .map(mapToOperations(OffsetDateTime.class))
                  .ifPresent(builder::startDateOperations);
              ofNullable(f.endDate())
                  .map(mapToOperations(OffsetDateTime.class))
                  .ifPresent(builder::endDateOperations);
              ofNullable(f.elementInstanceScopeKey())
                  .map(KeyUtil::keyToLong)
                  .ifPresent(builder::elementInstanceScopeKeys);
            });
    return builder.build();
  }
}
