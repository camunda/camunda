/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.query;

import io.camunda.util.CollectionUtil;
import io.camunda.util.ObjectBuilder;
import java.util.ArrayList;
import java.util.List;

public record SearchIdsQuery(List<String> values) implements SearchQueryOption {

  public static final class Builder implements ObjectBuilder<SearchIdsQuery> {

    private List<String> ids = new ArrayList<>();

    public Builder values(final List<String> values) {
      ids = CollectionUtil.addValuesToList(ids, values);
      return this;
    }

    public Builder values(final String... values) {
      return values(CollectionUtil.collectValuesAsList(values));
    }

    @Override
    public SearchIdsQuery build() {
      return new SearchIdsQuery(ids);
    }
  }
}
