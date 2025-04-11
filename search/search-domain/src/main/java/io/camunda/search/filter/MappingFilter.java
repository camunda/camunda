/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.filter;

import static io.camunda.util.CollectionUtil.addValuesToList;

import io.camunda.search.filter.UserFilter.Builder;
import io.camunda.util.ObjectBuilder;
import java.util.List;
import java.util.Set;

public record MappingFilter(
    String mappingId,
    Long mappingKey,
    String claimName,
    List<String> claimNames,
    String claimValue,
    String name,
    List<Claim> claims,
    String tenantId,
    Set<String> mappingIds)
    implements FilterBase {

  public MappingFilter.Builder toBuilder() {
    return new Builder()
        .mappingId(mappingId)
        .mappingKey(mappingKey)
        .claimName(claimName)
        .claimNames(claimNames)
        .claimValue(claimValue)
        .name(name)
        .claims(claims)
        .tenantId(tenantId)
        .mappingIds(mappingIds);
  }

  public static final class Builder implements ObjectBuilder<MappingFilter> {
    private String mappingId;
    private Set<String> mappingIds;
    private Long mappingKey;
    private String claimName;
    private List<String> claimNames;
    private String claimValue;
    private String name;
    private List<Claim> claims;
    private String tenantId;

    public Builder mappingId(final String value) {
      mappingId = value;
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

    public Builder tenantId(final String tenantId) {
      this.tenantId = tenantId;
      return this;
    }

    public Builder mappingIds(final Set<String> mappingIds) {
      this.mappingIds = mappingIds;
      return this;
    }

    @Override
    public MappingFilter build() {
      return new MappingFilter(
          mappingId,
          mappingKey,
          claimName,
          claimNames,
          claimValue,
          name,
          claims,
          tenantId,
          mappingIds);
    }
  }

  public record Claim(String name, String value) {}
}
