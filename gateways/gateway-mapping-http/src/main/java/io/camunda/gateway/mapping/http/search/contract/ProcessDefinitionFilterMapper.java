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

import io.camunda.gateway.mapping.http.search.contract.generated.ProcessDefinitionFilterContract;
import io.camunda.search.filter.FilterBuilders;
import io.camunda.search.filter.ProcessDefinitionFilter;
import io.camunda.zeebe.util.Either;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public final class ProcessDefinitionFilterMapper {

  private ProcessDefinitionFilterMapper() {}

  public static Either<List<String>, ProcessDefinitionFilter> toProcessDefinitionFilter(
      @Nullable final ProcessDefinitionFilterContract filter) {
    final var builder = FilterBuilders.processDefinition();
    final List<String> validationErrors = new ArrayList<>();
    if (filter != null) {
      ofNullable(filter.isLatestVersion()).ifPresent(builder::isLatestVersion);
      ofNullable(filter.processDefinitionKey())
          .map(mapKeyToLong("processDefinitionKey", validationErrors))
          .ifPresent(builder::processDefinitionKeys);
      ofNullable(filter.name())
          .map(mapToOperations(String.class))
          .ifPresent(builder::nameOperations);
      ofNullable(filter.resourceName()).ifPresent(builder::resourceNames);
      ofNullable(filter.version()).ifPresent(builder::versions);
      ofNullable(filter.versionTag()).ifPresent(builder::versionTags);
      ofNullable(filter.processDefinitionId())
          .map(mapToOperations(String.class))
          .ifPresent(builder::processDefinitionIdOperations);
      ofNullable(filter.tenantId()).ifPresent(builder::tenantIds);
      ofNullable(filter.hasStartForm()).ifPresent(builder::hasStartForm);
    }
    return validationErrors.isEmpty()
        ? Either.right(builder.build())
        : Either.left(validationErrors);
  }
}
