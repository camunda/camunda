/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db.fixtures;

import io.camunda.db.rdbms.write.RdbmsWriters;
import io.camunda.db.rdbms.write.domain.GroupDbModel;
import io.camunda.db.rdbms.write.domain.GroupDbModel.Builder;
import io.camunda.zeebe.test.util.Strings;
import java.util.List;
import java.util.function.Function;

public final class GroupFixtures extends CommonFixtures {

  private GroupFixtures() {}

  public static GroupDbModel createRandomized(final Function<Builder, Builder> builderFunction) {
    final var groupKey = nextKey();
    final var groupId = Strings.newRandomValidIdentityId();
    final var builder =
        new Builder()
            .groupKey(groupKey)
            .groupId(groupId)
            .name("Group " + groupId)
            .description("Description " + groupId);

    return builderFunction.apply(builder).build();
  }

  public static void createAndSaveRandomGroups(final RdbmsWriters rdbmsWriters) {
    createAndSaveRandomGroups(rdbmsWriters, b -> b);
  }

  public static GroupDbModel createAndSaveRandomGroup(
      final RdbmsWriters rdbmsWriters, final Function<Builder, Builder> builderFunction) {
    final var definition = GroupFixtures.createRandomized(builderFunction);
    rdbmsWriters.getGroupWriter().create(definition);
    rdbmsWriters.flush();
    return definition;
  }

  public static void createAndSaveRandomGroups(
      final RdbmsWriters rdbmsWriters, final Function<Builder, Builder> builderFunction) {
    createAndSaveRandomGroups(rdbmsWriters, 20, builderFunction);
  }

  public static void createAndSaveRandomGroups(
      final RdbmsWriters rdbmsWriters,
      final int numberOfInstances,
      final Function<Builder, Builder> builderFunction) {
    for (int i = 0; i < numberOfInstances; i++) {
      rdbmsWriters.getGroupWriter().create(GroupFixtures.createRandomized(builderFunction));
    }

    rdbmsWriters.flush();
  }

  public static void createAndSaveRandomGroupsWithMembers(
      final RdbmsWriters rdbmsWriters,
      final Function<GroupDbModel.Builder, GroupDbModel.Builder> builderFunction) {
    createAndSaveRandomGroupsWithMembers(rdbmsWriters, 20, builderFunction);
  }

  public static void createAndSaveRandomGroupsWithMembers(
      final RdbmsWriters rdbmsWriters,
      final int numberOfInstances,
      final Function<GroupDbModel.Builder, GroupDbModel.Builder> builderFunction) {
    for (int i = 0; i < numberOfInstances; i++) {
      final var group = GroupFixtures.createRandomized(builderFunction);
      rdbmsWriters.getGroupWriter().create(group);
      GroupMemberFixtures.createAndSaveRandomGroupMembers(
          rdbmsWriters, b -> b.groupId(group.groupId()));
    }

    rdbmsWriters.flush();
  }

  public static GroupDbModel createAndSaveGroup(
      final RdbmsWriters rdbmsWriters, final Function<Builder, Builder> builderFunction) {
    final var definition = createRandomized(builderFunction);
    createAndSaveGroups(rdbmsWriters, List.of(definition));
    return definition;
  }

  public static void createAndSaveGroup(final RdbmsWriters rdbmsWriters, final GroupDbModel group) {
    createAndSaveGroups(rdbmsWriters, List.of(group));
  }

  public static void createAndSaveGroups(
      final RdbmsWriters rdbmsWriters, final List<GroupDbModel> groupList) {
    for (final GroupDbModel group : groupList) {
      rdbmsWriters.getGroupWriter().create(group);
    }
    rdbmsWriters.flush();
  }
}
