/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.query;

import io.camunda.search.filter.AdHocSubprocessActivityFilter;
import io.camunda.util.ObjectBuilder;

public record AdHocSubprocessActivityQuery(AdHocSubprocessActivityFilter filter) {

  public static Builder builder() {
    return new Builder();
  }

  public static final class Builder implements ObjectBuilder<AdHocSubprocessActivityQuery> {

    private AdHocSubprocessActivityFilter filter;

    public Builder filter(final AdHocSubprocessActivityFilter filter) {
      this.filter = filter;
      return this;
    }

    @Override
    public AdHocSubprocessActivityQuery build() {
      return new AdHocSubprocessActivityQuery(filter);
    }
  }
}
