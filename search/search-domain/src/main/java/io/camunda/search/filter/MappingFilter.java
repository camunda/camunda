/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.filter;

import io.camunda.util.ObjectBuilder;

public record MappingFilter(Long mappingKey, String claimName, String claimValue)
    implements FilterBase {
  public static final class Builder implements ObjectBuilder<MappingFilter> {
    private Long mappingKey;
    private String claimName;
    private String claimValue;

    public Builder mappingKey(final Long value) {
      mappingKey = value;
      return this;
    }

    public Builder claimName(final String value) {
      claimName = value;
      return this;
    }

    public Builder claimValue(final String value) {
      claimValue = value;
      return this;
    }

    @Override
    public MappingFilter build() {
      return new MappingFilter(mappingKey, claimName, claimValue);
    }
  }
}
