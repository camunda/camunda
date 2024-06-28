/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.query;

import io.camunda.util.ObjectBuilder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public record SearchIdsQuery(List<String> values) implements SearchQueryOption {

  public static final class Builder implements ObjectBuilder<SearchIdsQuery> {

    private final List<String> ids = new ArrayList<>();

    public Builder values(final List<String> values) {
      ids.addAll(Objects.requireNonNullElse(values, List.of()));
      return this;
    }

    public Builder values(final String... values) {
      return values(Arrays.stream(Objects.requireNonNullElse(values, new String[0])).toList());
    }

    @Override
    public SearchIdsQuery build() {
      return new SearchIdsQuery(ids);
    }
  }
}
