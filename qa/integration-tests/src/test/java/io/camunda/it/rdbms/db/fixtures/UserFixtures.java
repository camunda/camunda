/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db.fixtures;

import io.camunda.db.rdbms.write.RdbmsWriter;
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

  public static void createAndSaveRandomUsers(final RdbmsWriter rdbmsWriter) {
    createAndSaveRandomUsers(rdbmsWriter, b -> b);
  }

  public static UserDbModel createAndSaveRandomUser(
      final RdbmsWriter rdbmsWriter, final Function<Builder, Builder> builderFunction) {
    final var definition = UserFixtures.createRandomized(builderFunction);
    rdbmsWriter.getUserWriter().create(definition);
    rdbmsWriter.flush();
    return definition;
  }

  public static void createAndSaveRandomUsers(
      final RdbmsWriter rdbmsWriter, final Function<Builder, Builder> builderFunction) {
    for (int i = 0; i < 20; i++) {
      rdbmsWriter.getUserWriter().create(UserFixtures.createRandomized(builderFunction));
    }

    rdbmsWriter.flush();
  }

  public static UserDbModel createAndSaveUser(
      final RdbmsWriter rdbmsWriter, final Function<Builder, Builder> builderFunction) {
    final var definition = createRandomized(builderFunction);
    createAndSaveUsers(rdbmsWriter, List.of(definition));
    return definition;
  }

  public static void createAndSaveUser(final RdbmsWriter rdbmsWriter, final UserDbModel user) {
    createAndSaveUsers(rdbmsWriter, List.of(user));
  }

  public static void createAndSaveUsers(
      final RdbmsWriter rdbmsWriter, final List<UserDbModel> userList) {
    for (final UserDbModel user : userList) {
      rdbmsWriter.getUserWriter().create(user);
    }
    rdbmsWriter.flush();
  }
}
