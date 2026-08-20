/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db.fixtures;

import io.camunda.db.rdbms.write.RdbmsWriters;
import io.camunda.db.rdbms.write.domain.UserDbModel;
import io.camunda.db.rdbms.write.domain.UserDbModel.Builder;
import java.util.List;
import java.util.function.Function;

public final class UserFixtures extends CommonFixtures {

  private UserFixtures() {}

  public static UserDbModel createRandomized(final Function<Builder, Builder> builderFunction) {
    final var userKey = nextKey();
    final var builder =
        new Builder()
            .userKey(userKey)
            .username("user-" + userKey)
            .name("User " + userKey)
            .email("user-" + userKey + "@camunda-test.com")
            .password("password-" + userKey);

    return builderFunction.apply(builder).build();
  }

  public static void createAndSaveRandomUsers(final RdbmsWriters rdbmsWriters) {
    createAndSaveRandomUsers(rdbmsWriters, b -> b);
  }

  public static UserDbModel createAndSaveRandomUser(
      final RdbmsWriters rdbmsWriters, final Function<Builder, Builder> builderFunction) {
    final var definition = UserFixtures.createRandomized(builderFunction);
    rdbmsWriters.getUserWriter().create(definition);
    rdbmsWriters.flush();
    return definition;
  }

  public static void createAndSaveRandomUsers(
      final RdbmsWriters rdbmsWriters, final Function<Builder, Builder> builderFunction) {
    createAndSaveRandomUsers(rdbmsWriters, 20, builderFunction);
  }

  public static void createAndSaveRandomUsers(
      final RdbmsWriters rdbmsWriters,
      final int numberOfInstances,
      final Function<Builder, Builder> builderFunction) {
    for (int i = 0; i < numberOfInstances; i++) {
      rdbmsWriters.getUserWriter().create(UserFixtures.createRandomized(builderFunction));
    }

    rdbmsWriters.flush();
  }

  public static UserDbModel createAndSaveUser(
      final RdbmsWriters rdbmsWriters, final Function<Builder, Builder> builderFunction) {
    final var definition = createRandomized(builderFunction);
    createAndSaveUsers(rdbmsWriters, List.of(definition));
    return definition;
  }

  public static void createAndSaveUser(final RdbmsWriters rdbmsWriters, final UserDbModel user) {
    createAndSaveUsers(rdbmsWriters, List.of(user));
  }

  public static void createAndSaveUsers(
      final RdbmsWriters rdbmsWriters, final List<UserDbModel> userList) {
    for (final UserDbModel user : userList) {
      rdbmsWriters.getUserWriter().create(user);
    }
    rdbmsWriters.flush();
  }
}
