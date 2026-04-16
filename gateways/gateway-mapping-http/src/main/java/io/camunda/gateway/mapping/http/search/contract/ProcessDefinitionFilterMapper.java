/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.search.contract;

import static io.camunda.gateway.mapping.http.util.AdvancedSearchFilterUtil.mapToOperations;
import static io.camunda.gateway.mapping.http.util.KeyUtil.mapKeyToLong;
import static java.util.Optional.ofNullable;

import io.camunda.search.filter.FilterBuilders;
import io.camunda.search.filter.ProcessDefinitionFilter;
import io.camunda.zeebe.util.Either;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.NullMarked;

@NullMarked
public final class ProcessDefinitionFilterMapper {

  private ProcessDefinitionFilterMapper() {}

  public static Either<List<String>, ProcessDefinitionFilter> toProcessDefinitionFilter(
      final io.camunda.gateway.protocol.model.ProcessDefinitionFilter filter) {
    final var builder = FilterBuilders.processDefinition();
    final List<String> validationErrors = new ArrayList<>();
    if (filter != null) {
      ofNullable(filter.getIsLatestVersion()).ifPresent(builder::isLatestVersion);
      ofNullable(filter.getProcessDefinitionKey())
          .map(mapKeyToLong("processDefinitionKey", validationErrors))
          .ifPresent(builder::processDefinitionKeys);
      ofNullable(filter.getName())
          .map(mapToOperations(String.class))
          .ifPresent(builder::nameOperations);
      ofNullable(filter.getResourceName()).ifPresent(builder::resourceNames);
      ofNullable(filter.getVersion()).ifPresent(builder::versions);
      ofNullable(filter.getVersionTag()).ifPresent(builder::versionTags);
      ofNullable(filter.getProcessDefinitionId())
          .map(mapToOperations(String.class))
          .ifPresent(builder::processDefinitionIdOperations);
      ofNullable(filter.getTenantId()).ifPresent(builder::tenantIds);
      ofNullable(filter.getHasStartForm()).ifPresent(builder::hasStartForm);
    }
    return validationErrors.isEmpty()
        ? Either.right(builder.build())
        : Either.left(validationErrors);
  }
}
