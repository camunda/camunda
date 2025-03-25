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
    String id,
    Long mappingKey,
    String claimName,
    List<String> claimNames,
    String claimValue,
    String name,
    List<Claim> claims)
    implements FilterBase {
  public static final class Builder implements ObjectBuilder<MappingFilter> {
    private String id;
    private Long mappingKey;
    private String claimName;
    private List<String> claimNames;
    private String claimValue;
    private String name;
    private List<Claim> claims;

    public Builder id(final String value) {
      id = value;
      return this;
    }

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

    public Builder name(final String value) {
      name = value;
      return this;
    }

    public Builder claims(final List<Claim> claims) {
      this.claims = claims;
      return this;
    }

    @Override
    public MappingFilter build() {
      return new MappingFilter(id, mappingKey, claimName, claimNames, claimValue, name, claims);
    }
  }

  public record Claim(String name, String value) {}
}
