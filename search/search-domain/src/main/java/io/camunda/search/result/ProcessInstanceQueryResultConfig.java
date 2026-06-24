/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.result;

import io.camunda.util.ObjectBuilder;
import java.util.function.Function;

public record ProcessInstanceQueryResultConfig(Boolean onlyKeys) implements QueryResultConfig {

  public static ProcessInstanceQueryResultConfig of(
      final Function<Builder, ObjectBuilder<ProcessInstanceQueryResultConfig>> fn) {
    return fn.apply(new Builder()).build();
  }

  public static final class Builder implements ObjectBuilder<ProcessInstanceQueryResultConfig> {

    private static final Boolean DEFAULT_ONLY_KEYS = false;

    private Boolean onlyKeys = DEFAULT_ONLY_KEYS;

    public Builder onlyKeys(final boolean onlyKey) {
      onlyKeys = onlyKey;
      return this;
    }

    @Override
    public ProcessInstanceQueryResultConfig build() {
      return new ProcessInstanceQueryResultConfig(onlyKeys);
    }
  }
}
