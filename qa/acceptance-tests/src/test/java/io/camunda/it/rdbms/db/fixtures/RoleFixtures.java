/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db.fixtures;

import io.camunda.db.rdbms.write.RdbmsWriters;
import io.camunda.db.rdbms.write.domain.RoleDbModel;
import io.camunda.db.rdbms.write.domain.RoleDbModel.Builder;
import java.util.List;
import java.util.function.Function;

public final class RoleFixtures extends CommonFixtures {

  private RoleFixtures() {}

  public static RoleDbModel createRandomized(final Function<Builder, Builder> builderFunction) {
    final var roleKey = nextKey();
    final var roleId = nextStringId();
    final var builder =
        new Builder()
            .roleKey(roleKey)
            .roleId(roleId)
            .name("Role " + roleId)
            .description("Description " + roleId);

    return builderFunction.apply(builder).build();
  }

  public static void createAndSaveRandomRoles(final RdbmsWriters rdbmsWriters) {
    createAndSaveRandomRoles(rdbmsWriters, b -> b);
  }

  public static RoleDbModel createAndSaveRandomRole(
      final RdbmsWriters rdbmsWriters, final Function<Builder, Builder> builderFunction) {
    final var definition = RoleFixtures.createRandomized(builderFunction);
    rdbmsWriters.getRoleWriter().create(definition);
    rdbmsWriters.flush();
    return definition;
  }

  public static void createAndSaveRandomRoles(
      final RdbmsWriters rdbmsWriters, final Function<Builder, Builder> builderFunction) {
    createAndSaveRandomRoles(rdbmsWriters, 20, builderFunction);
  }

  public static void createAndSaveRandomRoles(
      final RdbmsWriters rdbmsWriters,
      final int numberOfInstances,
      final Function<Builder, Builder> builderFunction) {
    for (int i = 0; i < numberOfInstances; i++) {
      rdbmsWriters.getRoleWriter().create(RoleFixtures.createRandomized(builderFunction));
    }

    rdbmsWriters.flush();
  }

  public static void createAndSaveRandomRolesWithMembers(final RdbmsWriters rdbmsWriters) {
    createAndSaveRandomRolesWithMembers(rdbmsWriters, b -> b);
  }

  public static void createAndSaveRandomRolesWithMembers(
      final RdbmsWriters rdbmsWriters, final Function<Builder, Builder> builderFunction) {
    createAndSaveRandomRolesWithMembers(rdbmsWriters, 20, builderFunction);
  }

  public static void createAndSaveRandomRolesWithMembers(
      final RdbmsWriters rdbmsWriters,
      final int numberOfInstances,
      final Function<Builder, Builder> builderFunction) {
    for (int i = 0; i < numberOfInstances; i++) {
      final var role = RoleFixtures.createRandomized(builderFunction);
      rdbmsWriters.getRoleWriter().create(role);
      RoleMemberFixtures.createAndSaveRandomRoleMembers(rdbmsWriters, b -> b.roleId(role.roleId()));
    }

    rdbmsWriters.flush();
  }

  public static RoleDbModel createAndSaveRole(
      final RdbmsWriters rdbmsWriters, final Function<Builder, Builder> builderFunction) {
    final var definition = createRandomized(builderFunction);
    createAndSaveRoles(rdbmsWriters, List.of(definition));
    return definition;
  }

  public static void createAndSaveRole(final RdbmsWriters rdbmsWriters, final RoleDbModel user) {
    createAndSaveRoles(rdbmsWriters, List.of(user));
  }

  public static void createAndSaveRoles(
      final RdbmsWriters rdbmsWriters, final List<RoleDbModel> userList) {
    for (final RoleDbModel user : userList) {
      rdbmsWriters.getRoleWriter().create(user);
    }
    rdbmsWriters.flush();
  }
}
