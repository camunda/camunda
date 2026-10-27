/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.filter;

import io.camunda.util.DurationUtil;
import io.camunda.util.ObjectBuilder;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.function.Function;

/**
 * Filter for job time-series statistics queries.
 *
 * @param from start of the time window to filter metrics
 * @param to end of the time window to filter metrics
 * @param jobType the job type to return time-series metrics for
 * @param resolution time bucket resolution (e.g. PT1M for 1 minute); automatically computed from
 *     {@code (to - from) / DEFAULT_DESIRED_DATA_POINTS} when not provided explicitly
 */
public record JobTimeSeriesStatisticsFilter(
    OffsetDateTime from, OffsetDateTime to, String jobType, Duration resolution)
    implements FilterBase {

  /** Desired number of data points when resolution is not explicitly provided. */
  public static final int DEFAULT_DESIRED_DATA_POINTS = 1000;

  /** Minimum allowed resolution. */
  public static final Duration MIN_RESOLUTION = Duration.ofMinutes(1);

  public static JobTimeSeriesStatisticsFilter of(
      final Function<Builder, ObjectBuilder<JobTimeSeriesStatisticsFilter>> fn) {
    return fn.apply(new Builder()).build();
  }

  public static final class Builder implements ObjectBuilder<JobTimeSeriesStatisticsFilter> {
    private OffsetDateTime from;
    private OffsetDateTime to;
    private String jobType;
    private Duration resolution;

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

    public Builder resolution(final Duration resolution) {
      this.resolution = resolution;
      return this;
    }

    private static Duration computeResolution(
        final OffsetDateTime from, final OffsetDateTime to, final Duration explicitResolution) {
      final var computed =
          explicitResolution != null
              ? explicitResolution
              : Duration.between(from, to).dividedBy(DEFAULT_DESIRED_DATA_POINTS);
      return DurationUtil.max(computed, MIN_RESOLUTION);
    }

    @Override
    public JobTimeSeriesStatisticsFilter build() {
      final var validFrom = Objects.requireNonNull(from, "from must not be null");
      final var validTo = Objects.requireNonNull(to, "to must not be null");
      return new JobTimeSeriesStatisticsFilter(
          validFrom,
          validTo,
          Objects.requireNonNull(jobType, "jobType must not be null"),
          computeResolution(validFrom, validTo, resolution));
    }
  }
}
