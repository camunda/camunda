/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.filter;

import static io.camunda.util.CollectionUtil.addValuesToList;

import io.camunda.util.ObjectBuilder;
import java.util.List;

public record MappingFilter(
    Long mappingKey, String claimName, List<String> claimNames, String claimValue)
    implements FilterBase {
  public static final class Builder implements ObjectBuilder<MappingFilter> {
    private Long mappingKey;
    private String claimName;
    private List<String> claimNames;
    private String claimValue;

    public Builder mappingKey(final Long value) {
      mappingKey = value;
      return this;
    }

    public Builder claimName(final String value) {
      claimName = value;
      return this;
    }

    public Builder claimNames(final List<String> values) {
      claimNames = addValuesToList(claimNames, values);
      return this;
    }

    public Builder claimValue(final String value) {
      claimValue = value;
      return this;
    }

    @Override
    public MappingFilter build() {
      return new MappingFilter(mappingKey, claimName, claimNames, claimValue);
    }
  }
}