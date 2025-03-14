/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.schema.configuration;

import static java.util.Optional.ofNullable;

import io.camunda.search.connect.configuration.ConnectConfiguration;
import java.util.function.Function;

public record SearchEngineConfiguration(
    ConnectConfiguration connect, IndexConfiguration index, RetentionConfiguration retention) {

  public static SearchEngineConfiguration of(final Function<Builder, Builder> fn) {
    return fn.apply(new Builder()).build();
  }

  public static class Builder {
    private ConnectConfiguration connect;
    private IndexConfiguration index;
    private RetentionConfiguration retention;

    public Builder connect(final ConnectConfiguration value) {
      connect = value;
      return this;
    }

    public Builder index(final IndexConfiguration value) {
      index = value;
      return this;
    }

    public Builder retention(final RetentionConfiguration value) {
      retention = value;
      return this;
    }

    public SearchEngineConfiguration build() {
      return new SearchEngineConfiguration(
          ofNullable(connect).orElseGet(ConnectConfiguration::new),
          ofNullable(index).orElseGet(IndexConfiguration::new),
          ofNullable(retention).orElseGet(RetentionConfiguration::new));
    }
  }
}
