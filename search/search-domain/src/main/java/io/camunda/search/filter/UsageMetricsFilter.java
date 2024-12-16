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

public record UsageMetricsFilter(OffsetDateTime startTime, OffsetDateTime endTime)
    implements FilterBase {

  public static final class Builder implements ObjectBuilder<UsageMetricsFilter> {
    private OffsetDateTime startTime;
    private OffsetDateTime endTime;

    public Builder startTime(final OffsetDateTime value) {
      startTime = value;
      return this;
    }

    public Builder endTime(final OffsetDateTime value) {
      endTime = value;
      return this;
    }

    @Override
    public UsageMetricsFilter build() {
      return new UsageMetricsFilter(startTime, endTime);
    }
  }
}
