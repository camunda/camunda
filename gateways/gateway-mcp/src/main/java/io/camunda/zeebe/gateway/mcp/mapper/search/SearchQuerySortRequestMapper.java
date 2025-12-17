/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.mcp.mapper.search;

import static io.camunda.zeebe.gateway.mcp.validator.ErrorMessages.ERROR_SORT_FIELD_MUST_NOT_BE_NULL;
import static io.camunda.zeebe.gateway.mcp.validator.ErrorMessages.ERROR_UNKNOWN_SORT_BY;

import io.camunda.search.sort.IncidentSort;
import io.camunda.search.sort.SortOption;
import io.camunda.search.sort.SortOptionBuilders;
import io.camunda.util.ObjectBuilder;
import io.camunda.zeebe.gateway.mcp.model.IncidentSearchQuerySortRequest;
import io.camunda.zeebe.gateway.mcp.model.SortOrderEnum;
import io.camunda.zeebe.util.Either;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Supplier;

public class SearchQuerySortRequestMapper {

  public static Either<List<String>, IncidentSort> toIncidentSearchSort(
      final List<IncidentSearchQuerySortRequest> sort) {
    return SearchQuerySortRequestMapper.toSearchQuerySort(
        fromIncidentSearchQuerySortRequest(sort),
        SortOptionBuilders::incident,
        SearchQuerySortRequestMapper::applyIncidentSortField);
  }

  private static List<SearchQuerySortRequest<IncidentSearchQuerySortRequest.FieldEnum>>
      fromIncidentSearchQuerySortRequest(final List<IncidentSearchQuerySortRequest> sort) {
    if (sort == null) {
      return List.of();
    }

    return sort.stream().map(r -> createFrom(r.field(), r.order())).toList();
  }

  private static <T> SearchQuerySortRequest<T> createFrom(
      final T field, final SortOrderEnum order) {
    return new SearchQuerySortRequest<T>(field, order);
  }

  private static List<String> applyIncidentSortField(
      final IncidentSearchQuerySortRequest.FieldEnum field, final IncidentSort.Builder builder) {
    final List<String> validationErrors = new ArrayList<>();
    if (field == null) {
      validationErrors.add(ERROR_SORT_FIELD_MUST_NOT_BE_NULL);
    } else {
      switch (field) {
        case incidentKey -> builder.incidentKey();
        case processDefinitionKey -> builder.processDefinitionKey();
        case processDefinitionId -> builder.processDefinitionId();
        case processInstanceKey -> builder.processInstanceKey();
        case errorType -> builder.errorType();
        case errorMessage -> builder.errorMessage();
        case elementId -> builder.flowNodeId();
        case elementInstanceKey -> builder.flowNodeInstanceKey();
        case creationTime -> builder.creationTime();
        case state -> builder.state();
        case jobKey -> builder.jobKey();
        case tenantId -> builder.tenantId();
        default -> validationErrors.add(ERROR_UNKNOWN_SORT_BY.formatted(field));
      }
    }
    return validationErrors;
  }

  private static <T, B extends SortOption.AbstractBuilder<B> & ObjectBuilder<T>, F>
      Either<List<String>, T> toSearchQuerySort(
          final List<SearchQuerySortRequest<F>> sorting,
          final Supplier<B> builderSupplier,
          final BiFunction<F, B, List<String>> sortFieldMapper) {
    if (sorting != null && !sorting.isEmpty()) {
      final List<String> validationErrors = new ArrayList<>();
      final var builder = builderSupplier.get();
      for (final SearchQuerySortRequest<F> sort : sorting) {
        validationErrors.addAll(sortFieldMapper.apply(sort.field(), builder));
        applySortOrder(sort.order(), builder);
      }

      return validationErrors.isEmpty()
          ? Either.right(builder.build())
          : Either.left(validationErrors);
    }

    return Either.right(null);
  }

  private static void applySortOrder(
      final SortOrderEnum order, final SortOption.AbstractBuilder<?> builder) {
    if (order == SortOrderEnum.DESC) {
      builder.desc();
    } else {
      builder.asc();
    }
  }

  public record SearchQuerySortRequest<T>(T field, SortOrderEnum order) {}
}
