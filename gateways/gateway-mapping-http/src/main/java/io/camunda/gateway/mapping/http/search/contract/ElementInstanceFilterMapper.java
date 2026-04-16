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
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public final class ElementInstanceFilterMapper {

  private ElementInstanceFilterMapper() {}

  public static FlowNodeInstanceFilter toElementInstanceFilter(
      @Nullable final ElementInstanceFilter filter) {
    if (filter == null) {
      return FilterBuilders.flowNodeInstance().build();
    }
    final var builder = FilterBuilders.flowNodeInstance();
    ofNullable(filter.getElementInstanceKey())
        .map(KeyUtil::keyToLong)
        .ifPresent(builder::flowNodeInstanceKeys);
    ofNullable(filter.getProcessInstanceKey())
        .map(KeyUtil::keyToLong)
        .ifPresent(builder::processInstanceKeys);
    ofNullable(filter.getProcessDefinitionKey())
        .map(KeyUtil::keyToLong)
        .ifPresent(builder::processDefinitionKeys);
    ofNullable(filter.getProcessDefinitionId()).ifPresent(builder::processDefinitionIds);
    ofNullable(filter.getState())
        .map(mapToOperations(String.class))
        .ifPresent(builder::stateOperations);
    ofNullable(filter.getType())
        .ifPresent(t -> builder.types(FlowNodeType.fromZeebeBpmnElementType(t)));
    ofNullable(filter.getElementId())
        .map(mapToOperations(String.class))
        .ifPresent(builder::flowNodeIdOperations);
    ofNullable(filter.getElementName())
        .map(mapToOperations(String.class))
        .ifPresent(builder::flowNodeNameOperations);
    ofNullable(filter.getHasIncident()).ifPresent(builder::hasIncident);
    ofNullable(filter.getIncidentKey()).map(KeyUtil::keyToLong).ifPresent(builder::incidentKeys);
    ofNullable(filter.getTenantId()).ifPresent(builder::tenantIds);
    ofNullable(filter.getStartDate())
        .map(mapToOperations(OffsetDateTime.class))
        .ifPresent(builder::startDateOperations);
    ofNullable(filter.getEndDate())
        .map(mapToOperations(OffsetDateTime.class))
        .ifPresent(builder::endDateOperations);
    ofNullable(filter.getElementInstanceScopeKey())
        .map(KeyUtil::keyToLong)
        .ifPresent(builder::elementInstanceScopeKeys);
    return builder.build();
  }
}
