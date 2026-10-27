/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write.domain;

import io.camunda.util.ObjectBuilder;
import java.util.function.Function;

public record TenantDbModel(String tenantId, Long tenantKey, String name, String description)
    implements DbModel<TenantDbModel> {

  @Override
  public TenantDbModel copy(
      final Function<ObjectBuilder<TenantDbModel>, ObjectBuilder<TenantDbModel>> builderFunction) {
    return builderFunction
        .apply(
            new Builder()
                .tenantKey(tenantKey)
                .tenantId(tenantId)
                .name(name)
                .description(description))
        .build();
  }

  public static class Builder implements ObjectBuilder<TenantDbModel> {

    private Long tenantKey;
    private String tenantId;
    private String name;
    private String description;

    public Builder() {}

    public Builder tenantKey(final Long tenantKey) {
      this.tenantKey = tenantKey;
      return this;
    }

    public Builder tenantId(final String tenantId) {
      this.tenantId = tenantId;
      return this;
    }

    public Builder name(final String name) {
      this.name = name;
      return this;
    }

    public Builder description(final String description) {
      this.description = description;
      return this;
    }

    @Override
    public TenantDbModel build() {
      return new TenantDbModel(tenantId, tenantKey, name, description);
    }
  }
}
