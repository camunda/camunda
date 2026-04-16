/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.search.contract;

import static io.camunda.gateway.mapping.http.validator.ErrorMessages.ERROR_MESSAGE_EMPTY_ATTRIBUTE;

import io.camunda.search.filter.FilterBuilders;
import io.camunda.search.filter.ProcessDefinitionInstanceVersionStatisticsFilter;
import io.camunda.zeebe.util.Either;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.NullMarked;

@NullMarked
public final class ProcessDefinitionInstanceVersionStatisticsFilterMapper {

  private ProcessDefinitionInstanceVersionStatisticsFilterMapper() {}

  public static Either<List<String>, ProcessDefinitionInstanceVersionStatisticsFilter>
      toProcessDefinitionInstanceVersionStatisticsFilter(
          final io.camunda.gateway.protocol.model.ProcessDefinitionInstanceVersionStatisticsFilter
              filter) {
    if (filter == null) {
      return Either.left(List.of(ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("filter")));
    }
    if (filter.getProcessDefinitionId() == null || filter.getProcessDefinitionId().isBlank()) {
      return Either.left(
          List.of(ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("filter.processDefinitionId")));
    }
    final var builder = FilterBuilders.processDefinitionInstanceVersionStatistics();
    builder.processDefinitionId(filter.getProcessDefinitionId());
    Optional.ofNullable(filter.getTenantId()).map(Object::toString).ifPresent(builder::tenantId);
    return Either.right(builder.build());
  }
}
