/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.domain;

import io.camunda.search.filter.ProcessInstanceStatisticsFilter;
import io.camunda.util.ObjectBuilder;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

public record ProcessInstanceStatisticsDbQuery(
    ProcessInstanceStatisticsFilter filter,
    List<String> authorizedResourceIds,
    List<String> authorizedTenantIds) {

  public static ProcessInstanceStatisticsDbQuery of(
      final Function<Builder, ObjectBuilder<ProcessInstanceStatisticsDbQuery>> fn) {
    return fn.apply(new Builder()).build();
  }

  public static final class Builder implements ObjectBuilder<ProcessInstanceStatisticsDbQuery> {
    private ProcessInstanceStatisticsFilter filter;
    private List<String> authorizedResourceIds;
    private List<String> authorizedTenantIds;

    public Builder filter(final ProcessInstanceStatisticsFilter filter) {
      this.filter = filter;
      return this;
    }

    public Builder authorizedResourceIds(final List<String> authorizedResourceIds) {
      this.authorizedResourceIds = authorizedResourceIds;
      return this;
    }

    public Builder authorizedTenantIds(final List<String> authorizedTenantIds) {
      this.authorizedTenantIds = authorizedTenantIds;
      return this;
    }

    @Override
    public ProcessInstanceStatisticsDbQuery build() {
      return new ProcessInstanceStatisticsDbQuery(
          Objects.requireNonNull(filter), authorizedResourceIds, authorizedTenantIds);
    }
  }
}
