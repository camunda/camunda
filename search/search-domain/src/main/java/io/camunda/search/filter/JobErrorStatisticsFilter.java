/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.filter;

import static io.camunda.util.CollectionUtil.addValuesToList;
import static io.camunda.util.CollectionUtil.collectValues;

import io.camunda.util.ObjectBuilder;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * Filter for job error statistics queries. Queries the job index to aggregate per-error metrics.
 *
 * @param from start of the time window (inclusive) to filter by job creation time
 * @param to end of the time window (inclusive) to filter by job creation time
 * @param jobType job type to return error metrics for (required)
 * @param errorCodeOperations optional filter on error code
 * @param errorMessageOperations optional filter on error message
 */
public record JobErrorStatisticsFilter(
    OffsetDateTime from,
    OffsetDateTime to,
    String jobType,
    List<Operation<String>> errorCodeOperations,
    List<Operation<String>> errorMessageOperations)
    implements FilterBase {

  public static JobErrorStatisticsFilter of(
      final Function<Builder, ObjectBuilder<JobErrorStatisticsFilter>> fn) {
    return fn.apply(new Builder()).build();
  }

  public static final class Builder implements ObjectBuilder<JobErrorStatisticsFilter> {
    private OffsetDateTime from;
    private OffsetDateTime to;
    private String jobType;
    private List<Operation<String>> errorCodeOperations;
    private List<Operation<String>> errorMessageOperations;

    public Builder from(final OffsetDateTime from) {
      this.from = from;
      return this;
    }

    public Builder to(final OffsetDateTime to) {
      this.to = to;
      return this;
    }

    public Builder jobType(final String jobType) {
      this.jobType = jobType;
      return this;
    }

    public Builder errorCodeOperations(final List<Operation<String>> operations) {
      errorCodeOperations = addValuesToList(errorCodeOperations, operations);
      return this;
    }

    @SafeVarargs
    public final Builder errorCodeOperations(
        final Operation<String> operation, final Operation<String>... operations) {
      return errorCodeOperations(collectValues(operation, operations));
    }

    public Builder errorMessageOperations(final List<Operation<String>> operations) {
      errorMessageOperations = addValuesToList(errorMessageOperations, operations);
      return this;
    }

    @SafeVarargs
    public final Builder errorMessageOperations(
        final Operation<String> operation, final Operation<String>... operations) {
      return errorMessageOperations(collectValues(operation, operations));
    }

    @Override
    public JobErrorStatisticsFilter build() {
      return new JobErrorStatisticsFilter(
          Objects.requireNonNull(from, "from must not be null"),
          Objects.requireNonNull(to, "to must not be null"),
          Objects.requireNonNull(jobType, "jobType must not be null"),
          Objects.requireNonNullElse(errorCodeOperations, Collections.emptyList()),
          Objects.requireNonNullElse(errorMessageOperations, Collections.emptyList()));
    }
  }
}
