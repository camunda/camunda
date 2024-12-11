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
import java.util.List;

public record UsageMetricsFilter(
    List<String> events, Operation<OffsetDateTime> startTime, Operation<OffsetDateTime> endTime)
    implements FilterBase {

  public static final class Builder implements ObjectBuilder<UsageMetricsFilter> {
    private List<String> events;
    private Operation<OffsetDateTime> startTime;
    private Operation<OffsetDateTime> endTime;

    public Builder events(final List<String> events) {
      this.events = addValuesToList(this.events, events);
      return this;
    }

    public Builder events(final String event, final String... events) {
      events(collectValues(event, events));
      return this;
    }

    public Builder startTime(final Operation<OffsetDateTime> value) {
      startTime = value;
      return this;
    }

    public Builder endTime(final Operation<OffsetDateTime> value) {
      endTime = value;
      return this;
    }

    @Override
    public UsageMetricsFilter build() {
      return new UsageMetricsFilter(events, startTime, endTime);
    }
  }
}
