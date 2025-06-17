/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.filter;

import static io.camunda.search.clients.query.SearchQueryBuilders.*;
import static io.camunda.webapps.schema.descriptors.template.OperationTemplate.*;

import io.camunda.search.clients.query.SearchQuery;
import io.camunda.search.filter.BatchOperationItemFilter;
import io.camunda.search.filter.Operation;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class BatchOperationItemFilterTransformer
    extends IndexFilterTransformer<BatchOperationItemFilter> {

  public BatchOperationItemFilterTransformer(final IndexDescriptor indexDescriptor) {
    super(indexDescriptor);
  }

  @Override
  public SearchQuery toSearchQuery(final BatchOperationItemFilter filter) {
    final var queries = new ArrayList<SearchQuery>();

    Optional.ofNullable(stringOperations(BATCH_OPERATION_ID, filter.batchOperationIdOperations()))
        .ifPresent(queries::addAll);
    Optional.ofNullable(stringOperations(STATE, mapStateOperations(filter.stateOperations())))
        .ifPresent(queries::addAll);
    Optional.ofNullable(longOperations(ITEM_KEY, filter.itemKeyOperations()))
        .ifPresent(queries::addAll);
    Optional.ofNullable(longOperations(PROCESS_INSTANCE_KEY, filter.processInstanceKeyOperations()))
        .ifPresent(queries::addAll);

    return and(queries);
  }

  private List<Operation<String>> mapStateOperations(
      final List<Operation<String>> stateOperations) {
    if (stateOperations == null) {
      return null;
    }

    return stateOperations.stream()
        .map(o -> new Operation<>(o.operator(), o.values().stream().map(this::mapState).toList()))
        .toList();
  }

  private String mapState(final String state) {
    return switch (state) {
      case "ACTIVE" -> "SCHEDULED";
      case "COMPLETED" -> "COMPLETED";
      case "CANCELED" -> "CANCELED";
      case "FAILED" -> "FAILED";
      default -> {
        throw new IllegalArgumentException("Unknown batch operation item state: " + state);
      }
    };
  }
}
