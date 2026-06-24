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

public record GlobalJobStatisticsFilter(OffsetDateTime from, OffsetDateTime to, String jobType)
    implements FilterBase {

  public static final class Builder implements ObjectBuilder<GlobalJobStatisticsFilter> {
    private OffsetDateTime from;
    private OffsetDateTime to;
    private String jobType;

    public Builder from(final OffsetDateTime value) {
      from = value;
      return this;
    }

    public Builder to(final OffsetDateTime value) {
      to = value;
      return this;
    }

    public Builder jobType(final String value) {
      jobType = value;
      return this;
    }

    @Override
    public GlobalJobStatisticsFilter build() {
      return new GlobalJobStatisticsFilter(from, to, jobType);
    }
  }
}
