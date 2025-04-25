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

public record MappingDbModel(String mappingId, String claimName, String claimValue, String name)
    implements DbModel<MappingDbModel> {

  @Override
  public MappingDbModel copy(
      final Function<ObjectBuilder<MappingDbModel>, ObjectBuilder<MappingDbModel>>
          builderFunction) {
    return builderFunction
        .apply(
            new MappingDbModelBuilder()
                .claimName(claimName)
                .claimValue(claimValue)
                .name(name)
                .mappingId(mappingId))
        .build();
  }

  public static class MappingDbModelBuilder implements ObjectBuilder<MappingDbModel> {

    private String claimName;
    private String claimValue;
    private String name;
    private String mappingId;

    public MappingDbModelBuilder() {}

    public MappingDbModelBuilder claimName(final String claimName) {
      this.claimName = claimName;
      return this;
    }

    public MappingDbModelBuilder claimValue(final String claimValue) {
      this.claimValue = claimValue;
      return this;
    }

    public MappingDbModelBuilder name(final String name) {
      this.name = name;
      return this;
    }

    public MappingDbModelBuilder mappingId(final String mappingId) {
      this.mappingId = mappingId;
      return this;
    }

    @Override
    public MappingDbModel build() {
      return new MappingDbModel(mappingId, claimName, claimValue, name);
    }
  }
}
