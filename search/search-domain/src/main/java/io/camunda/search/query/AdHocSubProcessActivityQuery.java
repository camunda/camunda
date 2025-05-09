/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.query;

import io.camunda.search.filter.AdHocSubProcessActivityFilter;
import io.camunda.util.ObjectBuilder;

public record AdHocSubProcessActivityQuery(AdHocSubProcessActivityFilter filter) {

  public static Builder builder() {
    return new Builder();
  }

  public static final class Builder implements ObjectBuilder<AdHocSubProcessActivityQuery> {

    private AdHocSubProcessActivityFilter filter;

    public Builder filter(final AdHocSubProcessActivityFilter filter) {
      this.filter = filter;
      return this;
    }

    @Override
    public AdHocSubProcessActivityQuery build() {
      return new AdHocSubProcessActivityQuery(filter);
    }
  }
}
