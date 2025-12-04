/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.identity;

import static io.camunda.client.api.search.enums.PermissionType.CREATE;
import static io.camunda.client.api.search.enums.PermissionType.CREATE_PROCESS_INSTANCE;
import static io.camunda.client.api.search.enums.PermissionType.DELETE;
import static io.camunda.client.api.search.enums.PermissionType.READ;
import static io.camunda.client.api.search.enums.PermissionType.READ_PROCESS_INSTANCE;
import static io.camunda.client.api.search.enums.ResourceType.AUTHORIZATION;
import static io.camunda.client.api.search.enums.ResourceType.PROCESS_DEFINITION;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.search.response.Authorization;
import io.camunda.qa.util.auth.Authenticated;
import io.camunda.qa.util.auth.Permissions;
import io.camunda.qa.util.auth.TestUser;
import io.camunda.qa.util.auth.UserDefinition;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.qa.util.multidb.MultiDbTestApplication;
import io.camunda.security.configuration.ConfiguredAuthorization;
import io.camunda.zeebe.protocol.record.value.AuthorizationOwnerType;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

@MultiDbTest
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "AWS_OS")
class InitializeAuthorizationIT {

  private static final String ADMIN = "admin";
  private static final String RESTRICTED = "restrictedUser";
  private static final String DEFAULT_PASSWORD = "password";

  private static final ConfiguredAuthorization CONFIGURED_AUTH_1 =
      new ConfiguredAuthorization(
          AuthorizationOwnerType.USER,
          RESTRICTED,
          AuthorizationResourceType.PROCESS_DEFINITION,
          "*",
          Set.of(PermissionType.READ_PROCESS_INSTANCE, PermissionType.CREATE_PROCESS_INSTANCE));

  @MultiDbTestApplication
  static final TestStandaloneBroker BROKER =
      new TestStandaloneBroker()
          .withBasicAuth()
          .withAuthorizationsEnabled()
          .withSecurityConfig(
              conf -> conf.getInitialization().setAuthorizations(List.of(CONFIGURED_AUTH_1)));

  @UserDefinition
  private static final TestUser ADMIN_USER =
      new TestUser(
          ADMIN,
          DEFAULT_PASSWORD,
          List.of(
              new Permissions(AUTHORIZATION, READ, List.of("*")),
              new Permissions(AUTHORIZATION, CREATE, List.of("*")),
              new Permissions(AUTHORIZATION, DELETE, List.of("*"))));

  @UserDefinition
  private static final TestUser RESTRICTED_USER =
      new TestUser(RESTRICTED, DEFAULT_PASSWORD, List.of());

  @Test
  void searchShouldReturnAuthorizationsFromInitialization(
      @Authenticated(ADMIN) final CamundaClient adminClient) {
    final var response =
        adminClient
            .newAuthorizationSearchRequest()
            .filter(f -> f.ownerId(RESTRICTED).resourceType(PROCESS_DEFINITION))
            .send()
            .join();

    assertThat(response.items()).hasSize(1);
    final Authorization authorization = response.items().getFirst();
    assertThat(authorization.getOwnerId()).isEqualTo(RESTRICTED);
    assertThat(authorization.getResourceType()).isEqualTo(PROCESS_DEFINITION);
    assertThat(authorization.getPermissionTypes())
        .containsExactlyInAnyOrder(READ_PROCESS_INSTANCE, CREATE_PROCESS_INSTANCE);
  }
}
