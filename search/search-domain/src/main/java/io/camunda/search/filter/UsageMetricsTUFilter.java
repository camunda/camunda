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

public record UsageMetricsTUFilter(
    OffsetDateTime startTime, OffsetDateTime endTime, String tenantId, boolean withTenants)
    implements FilterBase {

  public static final class Builder implements ObjectBuilder<UsageMetricsTUFilter> {
    private OffsetDateTime startTime;
    private OffsetDateTime endTime;
    private String tenantId;
    private boolean withTenants = false;

    public Builder startTime(final OffsetDateTime value) {
      startTime = value;
      return this;
    }

    public Builder endTime(final OffsetDateTime value) {
      endTime = value;
      return this;
    }

    public Builder tenantId(final String value) {
      tenantId = value;
      return this;
    }

    public Builder withTenants(final boolean value) {
      withTenants = value;
      return this;
    }

    @Override
    public UsageMetricsTUFilter build() {
      return new UsageMetricsTUFilter(startTime, endTime, tenantId, withTenants);
    }
  }
}
