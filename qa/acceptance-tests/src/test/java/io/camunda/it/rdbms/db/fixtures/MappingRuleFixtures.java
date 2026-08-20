/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db.fixtures;

import io.camunda.db.rdbms.write.RdbmsWriters;
import io.camunda.db.rdbms.write.domain.MappingRuleDbModel;
import io.camunda.db.rdbms.write.domain.MappingRuleDbModel.MappingRuleDbModelBuilder;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

public final class MappingRuleFixtures extends CommonFixtures {

  private MappingRuleFixtures() {}

  public static MappingRuleDbModel createRandomized() {
    return createRandomized(b -> b);
  }

  public static MappingRuleDbModel createRandomized(
      final Function<MappingRuleDbModelBuilder, MappingRuleDbModelBuilder> builderFunction) {
    final var id = nextKey();
    final var builder =
        new MappingRuleDbModelBuilder()
            .mappingRuleId(String.valueOf(id))
            .mappingRuleKey(id)
            .claimName("claimName-" + UUID.randomUUID())
            .claimValue("claimValue-" + UUID.randomUUID())
            .name("name" + UUID.randomUUID());

    return builderFunction.apply(builder).build();
  }

  public static void createAndSaveRandomMappingRules(
      final RdbmsWriters rdbmsWriters,
      final Function<MappingRuleDbModelBuilder, MappingRuleDbModelBuilder> builderFunction) {
    createAndSaveRandomMappingRules(rdbmsWriters, 20, builderFunction);
  }

  public static void createAndSaveRandomMappingRules(
      final RdbmsWriters rdbmsWriters,
      final int numberOfInstances,
      final Function<MappingRuleDbModelBuilder, MappingRuleDbModelBuilder> builderFunction) {
    for (int i = 0; i < numberOfInstances; i++) {
      rdbmsWriters
          .getMappingRuleWriter()
          .create(MappingRuleFixtures.createRandomized(builderFunction));
    }
    rdbmsWriters.flush();
  }

  public static void createAndSaveMappingRule(
      final RdbmsWriters rdbmsWriters, final MappingRuleDbModel mappingRule) {
    createAndSaveMappingRules(rdbmsWriters, List.of(mappingRule));
  }

  public static void createAndSaveMappingRules(
      final RdbmsWriters rdbmsWriters, final List<MappingRuleDbModel> mappingRuleList) {
    for (final MappingRuleDbModel mappingRule : mappingRuleList) {
      rdbmsWriters.getMappingRuleWriter().create(mappingRule);
    }
    rdbmsWriters.flush();
  }

  public static String nextStringId() {
    return UUID.randomUUID().toString();
  }
}
