/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db.fixtures;

import io.camunda.client.api.search.enums.OwnerType;
import io.camunda.client.api.search.enums.ResourceType;
import io.camunda.db.rdbms.write.RdbmsWriter;
import io.camunda.db.rdbms.write.domain.AuthorizationDbModel;
import io.camunda.db.rdbms.write.domain.AuthorizationDbModel.Builder;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceMatcher;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

public final class AuthorizationFixtures extends CommonFixtures {

  private AuthorizationFixtures() {}

  public static AuthorizationDbModel createRandomized(
      final Function<Builder, Builder> builderFunction) {
    final var ownerId = nextStringId();

    final var randomPermissionType1 = randomEnum(PermissionType.class);
    PermissionType randomPermissionType2;
    do {
      randomPermissionType2 = randomEnum(PermissionType.class);
    } while (randomPermissionType2 == randomPermissionType1);

    final var builder =
        new Builder()
            .authorizationKey(nextKey())
            .ownerId(ownerId)
            .ownerType(randomEnum(OwnerType.class).name())
            .resourceType(randomEnum(ResourceType.class).name())
            .resourceMatcher(randomEnum(AuthorizationResourceMatcher.class).value())
            .resourceId(nextStringId())
            .permissionTypes(Set.of(randomPermissionType1, randomPermissionType2));

    return builderFunction.apply(builder).build();
  }

  public static void createAndSaveRandomAuthorizations(final RdbmsWriter rdbmsWriter) {
    createAndSaveRandomAuthorizations(rdbmsWriter, b -> b);
  }

  public static AuthorizationDbModel createAndSaveRandomAuthorization(
      final RdbmsWriter rdbmsWriter, final Function<Builder, Builder> builderFunction) {
    final var definition = AuthorizationFixtures.createRandomized(builderFunction);
    rdbmsWriter.getAuthorizationWriter().createAuthorization(definition);
    rdbmsWriter.flush();
    return definition;
  }

  public static void createAndSaveRandomAuthorizations(
      final RdbmsWriter rdbmsWriter, final Function<Builder, Builder> builderFunction) {
    for (int i = 0; i < 20; i++) {
      rdbmsWriter
          .getAuthorizationWriter()
          .createAuthorization(AuthorizationFixtures.createRandomized(builderFunction));
    }

    rdbmsWriter.flush();
  }

  public static AuthorizationDbModel createAndSaveAuthorization(
      final RdbmsWriter rdbmsWriter, final Function<Builder, Builder> builderFunction) {
    final var definition = createRandomized(builderFunction);
    createAndSaveAuthorizations(rdbmsWriter, List.of(definition));
    return definition;
  }

  public static void createAndSaveAuthorization(
      final RdbmsWriter rdbmsWriter, final AuthorizationDbModel user) {
    createAndSaveAuthorizations(rdbmsWriter, List.of(user));
  }

  public static void createAndSaveAuthorizations(
      final RdbmsWriter rdbmsWriter, final List<AuthorizationDbModel> userList) {
    for (final AuthorizationDbModel user : userList) {
      rdbmsWriter.getAuthorizationWriter().createAuthorization(user);
    }
    rdbmsWriter.flush();
  }
}
