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

public record MappingRuleDbModel(
    String mappingRuleId, Long mappingRuleKey, String claimName, String claimValue, String name)
    implements DbModel<MappingRuleDbModel> {

  @Override
  public MappingRuleDbModel copy(
      final Function<ObjectBuilder<MappingRuleDbModel>, ObjectBuilder<MappingRuleDbModel>>
          builderFunction) {
    return builderFunction
        .apply(
            new MappingRuleDbModelBuilder()
                .mappingRuleKey(mappingRuleKey)
                .claimName(claimName)
                .claimValue(claimValue)
                .name(name)
                .mappingRuleId(mappingRuleId))
        .build();
  }

  public static class MappingRuleDbModelBuilder implements ObjectBuilder<MappingRuleDbModel> {

    private Long mappingRuleKey;
    private String claimName;
    private String claimValue;
    private String name;
    private String mappingRuleId;

    public MappingRuleDbModelBuilder() {}

    public MappingRuleDbModelBuilder mappingRuleKey(final Long mappingRuleKey) {
      this.mappingRuleKey = mappingRuleKey;
      return this;
    }

    public MappingRuleDbModelBuilder claimName(final String claimName) {
      this.claimName = claimName;
      return this;
    }

    public MappingRuleDbModelBuilder claimValue(final String claimValue) {
      this.claimValue = claimValue;
      return this;
    }

    public MappingRuleDbModelBuilder name(final String name) {
      this.name = name;
      return this;
    }

    public MappingRuleDbModelBuilder mappingRuleId(final String mappingRuleId) {
      this.mappingRuleId = mappingRuleId;
      return this;
    }

    @Override
    public MappingRuleDbModel build() {
      return new MappingRuleDbModel(mappingRuleId, mappingRuleKey, claimName, claimValue, name);
    }
  }
}
