/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db.fixtures;

import io.camunda.db.rdbms.write.RdbmsWriter;
import io.camunda.db.rdbms.write.domain.GroupMemberDbModel;
import io.camunda.db.rdbms.write.domain.GroupMemberDbModel.Builder;
import io.camunda.zeebe.protocol.record.value.EntityType;
import java.util.function.Function;

public final class GroupMemberFixtures extends CommonFixtures {
  private GroupMemberFixtures() {}

  public static GroupMemberDbModel createRandomized(
      final Function<Builder, Builder> builderFunction) {
    final var groupId = nextStringId();
    final var entityId = nextStringId();
    final var entityType = randomEnum(EntityType.class);
    final var builder =
        new Builder().groupId(groupId).entityId(entityId).entityType(entityType.name());

    return builderFunction.apply(builder).build();
  }

  public static void createAndSaveRandomGroupMember(
      final RdbmsWriter rdbmsWriter, final Function<Builder, Builder> builderFunction) {
    rdbmsWriter.getGroupWriter().addMember(createRandomized(builderFunction));
    rdbmsWriter.flush();
  }

  public static void createAndSaveRandomGroupMembers(
      final RdbmsWriter rdbmsWriter,
      final Function<GroupMemberDbModel.Builder, GroupMemberDbModel.Builder> builderFunction) {
    for (int i = 0; i < 20; i++) {
      rdbmsWriter.getGroupWriter().addMember(createRandomized(builderFunction));
    }

    rdbmsWriter.flush();
  }
}
