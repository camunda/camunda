/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.search.contract;

import static io.camunda.gateway.mapping.http.util.AdvancedSearchFilterUtil.mapToOperations;
import static io.camunda.gateway.mapping.http.validator.ErrorMessages.ERROR_MESSAGE_EMPTY_ATTRIBUTE;
import static io.camunda.gateway.mapping.http.validator.RequestValidator.validateDate;

import io.camunda.search.filter.FilterBuilders;
import io.camunda.search.filter.JobErrorStatisticsFilter;
import io.camunda.zeebe.util.Either;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.NullMarked;

@NullMarked
public final class JobErrorStatisticsFilterMapper {

  private JobErrorStatisticsFilterMapper() {}

  public static Either<List<String>, JobErrorStatisticsFilter> toJobErrorStatisticsFilter(
      final io.camunda.gateway.protocol.model.JobErrorStatisticsFilter filter) {
    final var builder = FilterBuilders.jobErrorStatistics();
    final List<String> validationErrors = new ArrayList<>();
    if (filter == null) {
      validationErrors.add(ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("filter"));
      return Either.<List<String>, JobErrorStatisticsFilter>left(validationErrors);
    }
    final var from = validateDate(filter.getFrom(), "from", validationErrors);
    Optional.ofNullable(from).ifPresent(builder::from);
    final var to = validateDate(filter.getTo(), "to", validationErrors);
    Optional.ofNullable(to).ifPresent(builder::to);
    if (filter.getJobType() == null || filter.getJobType().isBlank()) {
      validationErrors.add(ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("jobType"));
    } else {
      builder.jobType(filter.getJobType());
    }
    filter
        .getErrorCode()
        .map(mapToOperations(String.class))
        .ifPresent(builder::errorCodeOperations);
    filter
        .getErrorMessage()
        .map(mapToOperations(String.class))
        .ifPresent(builder::errorMessageOperations);
    return validationErrors.isEmpty()
        ? Either.right(builder.build())
        : Either.left(validationErrors);
  }
}
