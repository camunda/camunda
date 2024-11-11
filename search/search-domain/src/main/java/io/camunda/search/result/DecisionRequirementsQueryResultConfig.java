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

public record DecisionRequirementsQueryResultConfig(boolean includeXml)
    implements QueryResultConfig {

  public static DecisionRequirementsQueryResultConfig of(
      final Function<Builder, ObjectBuilder<DecisionRequirementsQueryResultConfig>> fn) {
    return fn.apply(new Builder()).build();
  }

  public static final class Builder
      implements ObjectBuilder<DecisionRequirementsQueryResultConfig> {

    private static final Boolean DEFAULT_INCLUDE_XML = false;

    private Boolean includeXml = DEFAULT_INCLUDE_XML;

    public Builder includeXml(final boolean includeXml) {
      this.includeXml = includeXml;
      return this;
    }

    @Override
    public DecisionRequirementsQueryResultConfig build() {
      return new DecisionRequirementsQueryResultConfig(includeXml);
    }
  }
}
