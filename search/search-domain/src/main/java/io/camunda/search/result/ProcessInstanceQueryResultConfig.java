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

public record ProcessInstanceQueryResultConfig(Boolean onlyKey) implements QueryResultConfig {

  public static ProcessInstanceQueryResultConfig of(
      final Function<Builder, ObjectBuilder<ProcessInstanceQueryResultConfig>> fn) {
    return fn.apply(new Builder()).build();
  }

  public static final class Builder implements ObjectBuilder<ProcessInstanceQueryResultConfig> {

    private static final Boolean DEFAULT_ONLY_KEY = false;

    private Boolean onlyKey = DEFAULT_ONLY_KEY;

    public Builder onlyKey(final boolean onlyKey) {
      this.onlyKey = onlyKey;
      return this;
    }

    @Override
    public ProcessInstanceQueryResultConfig build() {
      return new ProcessInstanceQueryResultConfig(onlyKey);
    }
  }
}
