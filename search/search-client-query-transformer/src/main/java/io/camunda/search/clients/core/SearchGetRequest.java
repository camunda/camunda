/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.core;

import static io.camunda.search.clients.core.RequestBuilders.getRequest;
import static io.camunda.util.CollectionUtil.addValuesToList;

import io.camunda.util.ObjectBuilder;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

public record SearchGetRequest(
    String id, String index, String routing, List<String> sourceExcludes) {

  public static SearchGetRequest of(
      final Function<SearchGetRequest.Builder, ObjectBuilder<SearchGetRequest>> fn) {
    return getRequest(fn);
  }

  public static final class Builder implements ObjectBuilder<SearchGetRequest> {

    private String id;
    private String index;
    private String routing;
    private List<String> sourceExcludes;

    public SearchGetRequest.Builder id(final String value) {
      id = value;
      return this;
    }

    public SearchGetRequest.Builder index(final String value) {
      index = value;
      return this;
    }

    public SearchGetRequest.Builder routing(final String value) {
      routing = value;
      return this;
    }

    public SearchGetRequest.Builder sourceExcludes(final List<String> values) {
      sourceExcludes = addValuesToList(sourceExcludes, values);
      return this;
    }

    @Override
    public SearchGetRequest build() {
      return new SearchGetRequest(
          Objects.requireNonNull(
              id, "Expected to retrieve a document by id, but given id was null."),
          Objects.requireNonNull(
              index, "Expected to create request for index, but given index was null."),
          routing,
          sourceExcludes);
    }
  }
}
