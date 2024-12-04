/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.filter;

import io.camunda.util.ObjectBuilder;
import java.util.function.Function;

public record TenantFilter(Long key, String tenantId, String name, Long memberKey)
    implements FilterBase {

  public static TenantFilter of(final Function<Builder, Builder> builderFunction) {
    return builderFunction.apply(new Builder()).build();
  }

  public static final class Builder implements ObjectBuilder<TenantFilter> {

    private Long key;
    private String tenantId;
    private String name;
    private Long memberKey;

    public Builder key(final Long value) {
      key = value;
      return this;
    }

    public Builder tenantId(final String value) {
      tenantId = value;
      return this;
    }

    public Builder name(final String value) {
      name = value;
      return this;
    }

    public Builder memberKey(final Long value) {
      memberKey = value;
      return this;
    }

    @Override
    public TenantFilter build() {
      return new TenantFilter(key, tenantId, name, memberKey);
    }
  }
}
