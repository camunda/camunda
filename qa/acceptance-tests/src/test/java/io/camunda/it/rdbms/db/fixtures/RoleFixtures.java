/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db.fixtures;

import io.camunda.db.rdbms.write.RdbmsWriter;
import io.camunda.db.rdbms.write.domain.RoleDbModel;
import io.camunda.db.rdbms.write.domain.RoleDbModel.Builder;
import java.util.List;
import java.util.function.Function;

public final class RoleFixtures extends CommonFixtures {

  private RoleFixtures() {}

  public static RoleDbModel createRandomized(final Function<Builder, Builder> builderFunction) {
    final var userKey = nextKey();
    final var builder = new Builder().roleKey(userKey).name("Role " + userKey);

    return builderFunction.apply(builder).build();
  }

  public static void createAndSaveRandomRoles(final RdbmsWriter rdbmsWriter) {
    createAndSaveRandomRoles(rdbmsWriter, b -> b);
  }

  public static RoleDbModel createAndSaveRandomRole(
      final RdbmsWriter rdbmsWriter, final Function<Builder, Builder> builderFunction) {
    final var definition = RoleFixtures.createRandomized(builderFunction);
    rdbmsWriter.getRoleWriter().create(definition);
    rdbmsWriter.flush();
    return definition;
  }

  public static void createAndSaveRandomRoles(
      final RdbmsWriter rdbmsWriter, final Function<Builder, Builder> builderFunction) {
    for (int i = 0; i < 20; i++) {
      rdbmsWriter.getRoleWriter().create(RoleFixtures.createRandomized(builderFunction));
    }

    rdbmsWriter.flush();
  }

  public static RoleDbModel createAndSaveRole(
      final RdbmsWriter rdbmsWriter, final Function<Builder, Builder> builderFunction) {
    final var definition = createRandomized(builderFunction);
    createAndSaveRoles(rdbmsWriter, List.of(definition));
    return definition;
  }

  public static void createAndSaveRole(final RdbmsWriter rdbmsWriter, final RoleDbModel user) {
    createAndSaveRoles(rdbmsWriter, List.of(user));
  }

  public static void createAndSaveRoles(
      final RdbmsWriter rdbmsWriter, final List<RoleDbModel> userList) {
    for (final RoleDbModel user : userList) {
      rdbmsWriter.getRoleWriter().create(user);
    }
    rdbmsWriter.flush();
  }
}
