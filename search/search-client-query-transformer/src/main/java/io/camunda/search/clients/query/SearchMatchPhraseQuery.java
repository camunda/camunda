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

public record SearchMatchPhraseQuery(String field, String query) implements SearchQueryOption {

  public static final class Builder implements ObjectBuilder<SearchMatchPhraseQuery> {

    private String field;
    private String query;

    public Builder field(final String value) {
      field = value;
      return this;
    }

    public Builder query(final String value) {
      query = value;
      return this;
    }

    @Override
    public SearchMatchPhraseQuery build() {
      return new SearchMatchPhraseQuery(
          Objects.requireNonNull(field, "Expected a non-null field for the match phrase query."),
          Objects.requireNonNull(
              query,
              () ->
                  String.format(
                      "Expected a non-null query parameter for the match phrase query, with field: '%s'.",
                      field)));
    }
  }
}
