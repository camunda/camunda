/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.query;

import io.camunda.util.ObjectBuilder;
import java.util.Objects;

public record SearchWildcardQuery(String field, String value) implements SearchQueryOption {

  public static final class Builder implements ObjectBuilder<SearchWildcardQuery> {

    private String field;
    private String value;

    public Builder field(final String value) {
      field = value;
      return this;
    }

    public Builder value(final String value) {
      this.value = value;
      return this;
    }

    @Override
    public SearchWildcardQuery build() {
      return new SearchWildcardQuery(Objects.requireNonNull(field), value);
    }
  }
}
