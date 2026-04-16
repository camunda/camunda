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

import io.camunda.gateway.mapping.http.util.KeyUtil;
import io.camunda.gateway.protocol.model.ElementInstanceFilter;
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
      @Nullable final ElementInstanceFilter filter) {
    final var builder = FilterBuilders.flowNodeInstance();
    Optional.ofNullable(filter)
        .ifPresent(
            f -> {
              ofNullable(f.getElementInstanceKey())
                  .map(KeyUtil::keyToLong)
                  .ifPresent(builder::flowNodeInstanceKeys);
              ofNullable(f.getProcessInstanceKey())
                  .map(KeyUtil::keyToLong)
                  .ifPresent(builder::processInstanceKeys);
              ofNullable(f.getProcessDefinitionKey())
                  .map(KeyUtil::keyToLong)
                  .ifPresent(builder::processDefinitionKeys);
              ofNullable(f.getProcessDefinitionId()).ifPresent(builder::processDefinitionIds);
              ofNullable(f.getState())
                  .map(mapToOperations(String.class))
                  .ifPresent(builder::stateOperations);
              ofNullable(f.getType())
                  .ifPresent(t -> builder.types(FlowNodeType.fromZeebeBpmnElementType(t)));
              ofNullable(f.getElementId())
                  .map(mapToOperations(String.class))
                  .ifPresent(builder::flowNodeIdOperations);
              ofNullable(f.getElementName())
                  .map(mapToOperations(String.class))
                  .ifPresent(builder::flowNodeNameOperations);
              ofNullable(f.getHasIncident()).ifPresent(builder::hasIncident);
              ofNullable(f.getIncidentKey())
                  .map(KeyUtil::keyToLong)
                  .ifPresent(builder::incidentKeys);
              ofNullable(f.getTenantId()).ifPresent(builder::tenantIds);
              ofNullable(f.getStartDate())
                  .map(mapToOperations(OffsetDateTime.class))
                  .ifPresent(builder::startDateOperations);
              ofNullable(f.getEndDate())
                  .map(mapToOperations(OffsetDateTime.class))
                  .ifPresent(builder::endDateOperations);
              ofNullable(f.getElementInstanceScopeKey())
                  .map(KeyUtil::keyToLong)
                  .ifPresent(builder::elementInstanceScopeKeys);
            });
    return builder.build();
  }
}
