/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.search.contract;

import static io.camunda.gateway.mapping.http.util.AdvancedSearchFilterUtil.mapToOperations;

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
    filter.getElementInstanceKey().map(KeyUtil::keyToLong).ifPresent(builder::flowNodeInstanceKeys);
    filter.getProcessInstanceKey().map(KeyUtil::keyToLong).ifPresent(builder::processInstanceKeys);
    filter
        .getProcessDefinitionKey()
        .map(KeyUtil::keyToLong)
        .ifPresent(builder::processDefinitionKeys);
    filter.getProcessDefinitionId().ifPresent(builder::processDefinitionIds);
    filter.getState().map(mapToOperations(String.class)).ifPresent(builder::stateOperations);
    filter.getType().ifPresent(t -> builder.types(FlowNodeType.fromZeebeBpmnElementType(t)));
    filter
        .getElementId()
        .map(mapToOperations(String.class))
        .ifPresent(builder::flowNodeIdOperations);
    filter
        .getElementName()
        .map(mapToOperations(String.class))
        .ifPresent(builder::flowNodeNameOperations);
    filter.getHasIncident().ifPresent(builder::hasIncident);
    filter.getIncidentKey().map(KeyUtil::keyToLong).ifPresent(builder::incidentKeys);
    filter.getTenantId().ifPresent(builder::tenantIds);
    filter
        .getStartDate()
        .map(mapToOperations(OffsetDateTime.class))
        .ifPresent(builder::startDateOperations);
    filter
        .getEndDate()
        .map(mapToOperations(OffsetDateTime.class))
        .ifPresent(builder::endDateOperations);
    filter
        .getElementInstanceScopeKey()
        .map(KeyUtil::keyToLong)
        .ifPresent(builder::elementInstanceScopeKeys);
    return builder.build();
  }
}
