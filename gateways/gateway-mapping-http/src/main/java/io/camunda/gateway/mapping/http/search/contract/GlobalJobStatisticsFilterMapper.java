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
import io.camunda.search.filter.GlobalJobStatisticsFilter;
import io.camunda.zeebe.util.Either;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public final class GlobalJobStatisticsFilterMapper {

  private GlobalJobStatisticsFilterMapper() {}

  public static Either<List<String>, GlobalJobStatisticsFilter> toGlobalJobStatisticsFilter(
      @Nullable final OffsetDateTime from,
      @Nullable final OffsetDateTime to,
      @Nullable final String jobType) {
    final var builder = FilterBuilders.globalJobStatistics();
    final List<String> validationErrors = new ArrayList<>();

    if (from == null) {
      validationErrors.add(ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("from"));
    } else {
      builder.from(from);
    }

    if (to == null) {
      validationErrors.add(ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("to"));
    } else {
      builder.to(to);
    }

    Optional.ofNullable(jobType).ifPresent(builder::jobType);

    return validationErrors.isEmpty()
        ? Either.right(builder.build())
        : Either.left(validationErrors);
  }
}
