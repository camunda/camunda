/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.rdbms.db.fixtures;

import io.camunda.client.protocol.rest.OwnerTypeEnum;
import io.camunda.client.protocol.rest.ResourceTypeEnum;
import io.camunda.db.rdbms.write.RdbmsWriter;
import io.camunda.db.rdbms.write.domain.AuthorizationDbModel;
import io.camunda.db.rdbms.write.domain.AuthorizationDbModel.Builder;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import java.util.List;
import java.util.function.Function;

public final class AuthorizationFixtures extends CommonFixtures {

  private AuthorizationFixtures() {}

  public static AuthorizationDbModel createRandomized(
      final Function<Builder, Builder> builderFunction) {
    final var ownerKey = nextKey();
    final var builder =
        new Builder()
            .ownerKey(ownerKey)
            .ownerType(randomEnum(OwnerTypeEnum.class).name())
            .resourceType(randomEnum(ResourceTypeEnum.class).name())
            .permissionType(randomEnum(PermissionType.class))
            .resourceId(nextStringId());

    return builderFunction.apply(builder).build();
  }

  public static void createAndSaveRandomAuthorizations(final RdbmsWriter rdbmsWriter) {
    createAndSaveRandomAuthorizations(rdbmsWriter, b -> b);
  }

  public static AuthorizationDbModel createAndSaveRandomAuthorization(
      final RdbmsWriter rdbmsWriter, final Function<Builder, Builder> builderFunction) {
    final var definition = AuthorizationFixtures.createRandomized(builderFunction);
    rdbmsWriter.getAuthorizationWriter().addPermissions(List.of(definition));
    rdbmsWriter.flush();
    return definition;
  }

  public static void createAndSaveRandomAuthorizations(
      final RdbmsWriter rdbmsWriter, final Function<Builder, Builder> builderFunction) {
    for (int i = 0; i < 20; i++) {
      rdbmsWriter
          .getAuthorizationWriter()
          .addPermissions(List.of(AuthorizationFixtures.createRandomized(builderFunction)));
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
      rdbmsWriter.getAuthorizationWriter().addPermissions(List.of(user));
    }
    rdbmsWriter.flush();
  }
}
