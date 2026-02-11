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
 * Filter for job type statistics queries.
 *
 * @param from start of the time window to filter metrics
 * @param to end of the time window to filter metrics
 * @param jobTypePrefix optional job type prefix to filter the job types returned
 */
public record JobTypeStatisticsFilter(OffsetDateTime from, OffsetDateTime to, String jobTypePrefix)
    implements FilterBase {

  public static JobTypeStatisticsFilter of(
      final Function<Builder, ObjectBuilder<JobTypeStatisticsFilter>> fn) {
    return fn.apply(new Builder()).build();
  }

  public static final class Builder implements ObjectBuilder<JobTypeStatisticsFilter> {
    private OffsetDateTime from;
    private OffsetDateTime to;
    private String jobTypePrefix;

    public Builder from(final OffsetDateTime from) {
      this.from = from;
      return this;
    }

    public Builder to(final OffsetDateTime to) {
      this.to = to;
      return this;
    }

    public Builder jobTypePrefix(final String jobTypePrefix) {
      this.jobTypePrefix = jobTypePrefix;
      return this;
    }

    @Override
    public JobTypeStatisticsFilter build() {
      return new JobTypeStatisticsFilter(
          Objects.requireNonNull(from, "from must not be null"),
          Objects.requireNonNull(to, "to must not be null"),
          jobTypePrefix);
    }
  }
}
