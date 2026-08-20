/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.core;

import io.camunda.search.clients.core.SearchGetResponse.Builder;
import io.camunda.util.ObjectBuilder;
import java.util.Objects;
import java.util.function.Function;

public record SearchWriteResponse(String id, String index, Result result) {

  public static <T> SearchWriteResponse of(
      final Function<SearchWriteResponse.Builder, ObjectBuilder<SearchWriteResponse>> fn) {
    return fn.apply(new SearchWriteResponse.Builder()).build();
  }

  public static final class Builder implements ObjectBuilder<SearchWriteResponse> {

    private String id;
    private String index;
    private Result result;

    public Builder id(final String value) {
      id = value;
      return this;
    }

    public Builder index(final String value) {
      index = value;
      return this;
    }

    public Builder result(final Result value) {
      result = value;
      return this;
    }

    @Override
    public SearchWriteResponse build() {
      return new SearchWriteResponse(
          Objects.requireNonNull(id),
          Objects.requireNonNull(index),
          Objects.requireNonNull(result));
    }
  }

  public enum Result {
    CREATED,
    UPDATED,
    DELETED,
    NOT_FOUND,
    NOOP;
  }
}
