/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.filter;

import io.camunda.util.ObjectBuilder;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.function.Function;

/**
 * Filter for job worker statistics queries.
 *
 * @param from start of the time window to filter metrics
 * @param to end of the time window to filter metrics
 * @param jobType the job type to filter worker metrics for
 */
public record JobWorkerStatisticsFilter(OffsetDateTime from, OffsetDateTime to, String jobType)
    implements FilterBase {

  public static JobWorkerStatisticsFilter of(
      final Function<Builder, ObjectBuilder<JobWorkerStatisticsFilter>> fn) {
    return fn.apply(new Builder()).build();
  }

  public static final class Builder implements ObjectBuilder<JobWorkerStatisticsFilter> {
    private OffsetDateTime from;
    private OffsetDateTime to;
    private String jobType;

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

    @Override
    public JobWorkerStatisticsFilter build() {
      return new JobWorkerStatisticsFilter(
          Objects.requireNonNull(from, "from must not be null"),
          Objects.requireNonNull(to, "to must not be null"),
          Objects.requireNonNull(jobType, "jobType must not be null"));
    }
  }
}
