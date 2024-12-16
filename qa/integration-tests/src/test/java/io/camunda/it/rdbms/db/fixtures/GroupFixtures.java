/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db.fixtures;

import io.camunda.db.rdbms.write.RdbmsWriter;
import io.camunda.db.rdbms.write.domain.GroupDbModel;
import io.camunda.db.rdbms.write.domain.GroupDbModel.Builder;
import java.util.List;
import java.util.function.Function;

public final class GroupFixtures extends CommonFixtures {

  private GroupFixtures() {}

  public static GroupDbModel createRandomized(final Function<Builder, Builder> builderFunction) {
    final var userKey = nextKey();
    final var builder = new Builder().groupKey(userKey).name("Group " + userKey);

    return builderFunction.apply(builder).build();
  }

  public static void createAndSaveRandomGroups(final RdbmsWriter rdbmsWriter) {
    createAndSaveRandomGroups(rdbmsWriter, b -> b);
  }

  public static GroupDbModel createAndSaveRandomGroup(
      final RdbmsWriter rdbmsWriter, final Function<Builder, Builder> builderFunction) {
    final var definition = GroupFixtures.createRandomized(builderFunction);
    rdbmsWriter.getGroupWriter().create(definition);
    rdbmsWriter.flush();
    return definition;
  }

  public static void createAndSaveRandomGroups(
      final RdbmsWriter rdbmsWriter, final Function<Builder, Builder> builderFunction) {
    for (int i = 0; i < 20; i++) {
      rdbmsWriter.getGroupWriter().create(GroupFixtures.createRandomized(builderFunction));
    }

    rdbmsWriter.flush();
  }

  public static GroupDbModel createAndSaveGroup(
      final RdbmsWriter rdbmsWriter, final Function<Builder, Builder> builderFunction) {
    final var definition = createRandomized(builderFunction);
    createAndSaveGroups(rdbmsWriter, List.of(definition));
    return definition;
  }

  public static void createAndSaveGroup(final RdbmsWriter rdbmsWriter, final GroupDbModel user) {
    createAndSaveGroups(rdbmsWriter, List.of(user));
  }

  public static void createAndSaveGroups(
      final RdbmsWriter rdbmsWriter, final List<GroupDbModel> userList) {
    for (final GroupDbModel user : userList) {
      rdbmsWriter.getGroupWriter().create(user);
    }
    rdbmsWriter.flush();
  }
}
