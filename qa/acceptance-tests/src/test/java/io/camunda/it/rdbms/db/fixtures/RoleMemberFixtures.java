/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db.fixtures;

import io.camunda.db.rdbms.write.RdbmsWriters;
import io.camunda.db.rdbms.write.domain.RoleMemberDbModel;
import io.camunda.db.rdbms.write.domain.RoleMemberDbModel.Builder;
import io.camunda.zeebe.protocol.record.value.EntityType;
import java.util.function.Function;

public final class RoleMemberFixtures extends CommonFixtures {
  private RoleMemberFixtures() {}

  public static RoleMemberDbModel createRandomized(
      final Function<Builder, Builder> builderFunction) {
    final var roleId = nextStringId();
    final var entityId = nextStringId();
    final var entityType = randomEnum(EntityType.class);
    final var builder =
        new Builder().roleId(roleId).entityId(entityId).entityType(entityType.name());

    return builderFunction.apply(builder).build();
  }

  public static void createAndSaveRandomRoleMember(
      final RdbmsWriters rdbmsWriters, final Function<Builder, Builder> builderFunction) {
    rdbmsWriters.getRoleWriter().addMember(createRandomized(builderFunction));
    rdbmsWriters.flush();
  }

  public static void createAndSaveRandomRoleMembers(
      final RdbmsWriters rdbmsWriters, final Function<Builder, Builder> builderFunction) {
    for (int i = 0; i < 20; i++) {
      rdbmsWriters.getRoleWriter().addMember(createRandomized(builderFunction));
    }

    rdbmsWriters.flush();
  }
}
