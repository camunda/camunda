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

import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedGlobalTaskListenerSearchQueryFilterRequestStrictContract;
import io.camunda.search.entities.GlobalListenerType;
import io.camunda.search.filter.FilterBuilders;
import io.camunda.search.filter.GlobalListenerFilter;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public final class GlobalTaskListenerFilterMapper {

  private GlobalTaskListenerFilterMapper() {}

  public static GlobalListenerFilter toGlobalTaskListenerFilter(
      @Nullable final GeneratedGlobalTaskListenerSearchQueryFilterRequestStrictContract filter) {
    final var builder =
        FilterBuilders.globalListener().listenerTypes(GlobalListenerType.USER_TASK.name());
    if (filter != null) {
      ofNullable(filter.id())
          .map(mapToOperations(String.class))
          .ifPresent(builder::listenerIdOperations);
      ofNullable(filter.type())
          .map(mapToOperations(String.class))
          .ifPresent(builder::typeOperations);
      ofNullable(filter.retries())
          .map(mapToOperations(Integer.class))
          .ifPresent(builder::retriesOperations);
      ofNullable(filter.eventTypes())
          .map(mapToOperations(String.class))
          .ifPresent(builder::eventTypeOperations);
      ofNullable(filter.afterNonGlobal()).ifPresent(builder::afterNonGlobal);
      ofNullable(filter.priority())
          .map(mapToOperations(Integer.class))
          .ifPresent(builder::priorityOperations);
      ofNullable(filter.source())
          .map(mapToOperations(String.class))
          .ifPresent(builder::sourceOperations);
    }
    return builder.build();
  }
}
