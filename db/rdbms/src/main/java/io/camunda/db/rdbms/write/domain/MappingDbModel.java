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

public record MappingDbModel(
    String id, Long mappingKey, String claimName, String claimValue, String name)
    implements DbModel<MappingDbModel> {

  @Override
  public MappingDbModel copy(
      final Function<ObjectBuilder<MappingDbModel>, ObjectBuilder<MappingDbModel>>
          builderFunction) {
    return builderFunction
        .apply(
            new MappingDbModelBuilder()
                .id(id)
                .mappingKey(mappingKey)
                .claimName(claimName)
                .claimValue(claimValue)
                .name(name))
        .build();
  }

  public static class MappingDbModelBuilder implements ObjectBuilder<MappingDbModel> {

    private String id;
    private Long mappingKey;
    private String claimName;
    private String claimValue;
    private String name;

    public MappingDbModelBuilder() {}

    public MappingDbModelBuilder id(final String id) {
      this.id = id;
      return this;
    }

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

    public MappingDbModelBuilder name(final String name) {
      this.name = name;
      return this;
    }

    @Override
    public MappingDbModel build() {
      return new MappingDbModel(id, mappingKey, claimName, claimValue, name);
    }
  }
}
