/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.search.contract;

import static io.camunda.gateway.mapping.http.validator.ErrorMessages.ERROR_MESSAGE_EMPTY_ATTRIBUTE;
import static io.camunda.gateway.mapping.http.validator.RequestValidator.validateDate;

import io.camunda.gateway.mapping.http.search.contract.generated.JobWorkerStatisticsFilterContract;
import io.camunda.search.filter.FilterBuilders;
import io.camunda.search.filter.JobWorkerStatisticsFilter;
import io.camunda.zeebe.util.Either;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public final class JobWorkerStatisticsFilterMapper {

  private JobWorkerStatisticsFilterMapper() {}

  public static Either<List<String>, JobWorkerStatisticsFilter> toJobWorkerStatisticsFilter(
      @Nullable final JobWorkerStatisticsFilterContract filter) {
    final var builder = FilterBuilders.jobWorkerStatistics();
    final List<String> validationErrors = new ArrayList<>();
    if (filter == null) {
      validationErrors.add(ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("filter"));
      return Either.left(validationErrors);
    }
    final var from = validateDate(filter.from(), "from", validationErrors);
    Optional.ofNullable(from).ifPresent(builder::from);
    final var to = validateDate(filter.to(), "to", validationErrors);
    Optional.ofNullable(to).ifPresent(builder::to);
    if (filter.jobType() == null || filter.jobType().isBlank()) {
      validationErrors.add(ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("jobType"));
    } else {
      builder.jobType(filter.jobType());
    }
    return validationErrors.isEmpty()
        ? Either.right(builder.build())
        : Either.left(validationErrors);
  }
}
