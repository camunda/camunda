/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.core;

import static io.camunda.search.clients.core.RequestBuilders.deleteRequest;

import io.camunda.util.ObjectBuilder;
import java.util.Objects;
import java.util.function.Function;

public record SearchDeleteRequest(String id, String index) {

  public static <T> SearchDeleteRequest of(
      final Function<SearchDeleteRequest.Builder, ObjectBuilder<SearchDeleteRequest>> fn) {
    return deleteRequest(fn);
  }

  public static final class Builder implements ObjectBuilder<SearchDeleteRequest> {

    private String id;
    private String index;

    public Builder id(final String value) {
      id = value;
      return this;
    }

    public Builder index(final String value) {
      index = value;
      return this;
    }

    @Override
    public SearchDeleteRequest build() {
      return new SearchDeleteRequest(
          Objects.requireNonNull(id, "Expected to delete a document, but given id was null"),
          Objects.requireNonNull(
              index, "Expected to create request for index, but given index was null."));
    }
  }
}
