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

public record MappingDbModel(Long mappingKey, String claimName, String claimValue)
    implements DbModel<MappingDbModel> {

  @Override
  public MappingDbModel copy(
      final Function<ObjectBuilder<MappingDbModel>, ObjectBuilder<MappingDbModel>>
          builderFunction) {
    return builderFunction
        .apply(
            new MappingDbModelBuilder()
                .mappingKey(mappingKey)
                .claimName(claimName)
                .claimValue(claimValue))
        .build();
  }

  public static class MappingDbModelBuilder implements ObjectBuilder<MappingDbModel> {

    private Long mappingKey;
    private String claimName;
    private String claimValue;

    public MappingDbModelBuilder() {}

    public MappingDbModelBuilder mappingKey(final Long mappingKey) {
      this.mappingKey = mappingKey;
      return this;
    }

    public MappingDbModelBuilder claimName(final String claimName) {
      this.claimName = claimName;
      return this;
    }

    public MappingDbModelBuilder claimValue(final String claimValue) {
      this.claimValue = claimValue;
      return this;
    }

    @Override
    public MappingDbModel build() {
      return new MappingDbModel(mappingKey, claimName, claimValue);
    }
  }
}
