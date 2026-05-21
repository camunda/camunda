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

public record DeployedResourceQueryResultConfig(boolean includeContent)
    implements QueryResultConfig {

  public static DeployedResourceQueryResultConfig of(
      final Function<Builder, ObjectBuilder<DeployedResourceQueryResultConfig>> fn) {
    return fn.apply(new Builder()).build();
  }

  public static final class Builder implements ObjectBuilder<DeployedResourceQueryResultConfig> {

    private boolean includeContent = false;

    public Builder includeContent(final boolean includeContent) {
      this.includeContent = includeContent;
      return this;
    }

    @Override
    public DeployedResourceQueryResultConfig build() {
      return new DeployedResourceQueryResultConfig(includeContent);
    }
  }
}
