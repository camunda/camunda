/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.read.domain;

import io.camunda.search.filter.CorrelatedMessagesFilter;
import io.camunda.search.page.SearchQueryPage;
import io.camunda.search.sort.CorrelatedMessagesSort;
import io.camunda.util.ObjectBuilder;
import java.util.function.Function;

public record CorrelatedMessagesDbQuery(
    CorrelatedMessagesFilter filter, CorrelatedMessagesSort sort, SearchQueryPage page) {

  public static CorrelatedMessagesDbQuery of(
      final Function<Builder, ObjectBuilder<CorrelatedMessagesDbQuery>> fn) {
    return fn.apply(new Builder()).build();
  }

  public static final class Builder implements ObjectBuilder<CorrelatedMessagesDbQuery> {
    private CorrelatedMessagesFilter filter;
    private CorrelatedMessagesSort sort;
    private SearchQueryPage page;

    public Builder filter(final CorrelatedMessagesFilter filter) {
      this.filter = filter;
      return this;
    }

    public Builder sort(final CorrelatedMessagesSort sort) {
      this.sort = sort;
      return this;
    }

    public Builder page(final SearchQueryPage page) {
      this.page = page;
      return this;
    }

    @Override
    public CorrelatedMessagesDbQuery build() {
      return new CorrelatedMessagesDbQuery(filter, sort, page);
    }
  }
}