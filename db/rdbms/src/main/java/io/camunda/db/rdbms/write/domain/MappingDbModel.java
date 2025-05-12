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
    String mappingRuleId, Long mappingKey, String claimName, String claimValue, String name)
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
                .claimValue(claimValue)
                .name(name)
                .mappingRuleId(mappingRuleId))
        .build();
  }

  public static class MappingDbModelBuilder implements ObjectBuilder<MappingDbModel> {

    private Long mappingKey;
    private String claimName;
    private String claimValue;
    private String name;
    private String mappingRuleId;

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

    public MappingDbModelBuilder name(final String name) {
      this.name = name;
      return this;
    }

    public MappingDbModelBuilder mappingRuleId(final String mappingRuleId) {
      this.mappingRuleId = mappingRuleId;
      return this;
    }

    @Override
    public MappingDbModel build() {
      return new MappingDbModel(mappingRuleId, mappingKey, claimName, claimValue, name);
    }
  }
}
