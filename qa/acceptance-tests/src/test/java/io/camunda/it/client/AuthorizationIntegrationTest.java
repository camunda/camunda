/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.client;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ProblemException;
import io.camunda.client.api.response.CreateAuthorizationResponse;
import io.camunda.client.api.search.enums.OwnerType;
import io.camunda.client.api.search.enums.PermissionType;
import io.camunda.client.api.search.enums.ResourceType;
import io.camunda.client.api.search.response.Authorization;
import io.camunda.client.protocol.rest.OwnerTypeEnum;
import io.camunda.client.protocol.rest.PermissionTypeEnum;
import io.camunda.client.protocol.rest.ResourceTypeEnum;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.zeebe.test.util.Strings;
import java.util.List;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

@MultiDbTest
public class AuthorizationIntegrationTest {

  private static CamundaClient camundaClient;

  @Test
  void shouldCreateAndGetAuthorizationByAuthorizationKey() {
    // given
    final var ownerId = Strings.newRandomValidIdentityId();
    final var resourceId = Strings.newRandomValidIdentityId();

    // when
    final CreateAuthorizationResponse authorization =
        camundaClient
            .newCreateAuthorizationCommand()
            .ownerId(ownerId)
            .ownerType(OwnerType.USER)
            .resourceId(resourceId)
            .resourceType(ResourceType.RESOURCE)
            .permissionTypes(PermissionType.CREATE)
            .send()
            .join();
    final long authorizationKey = authorization.getAuthorizationKey();
    assertThat(authorizationKey).isGreaterThan(0);

    // then
    Awaitility.await()
        .ignoreExceptionsInstanceOf(ProblemException.class)
        .untilAsserted(
            () -> {
              final Authorization retrievedAuthorization =
                  camundaClient.newAuthorizationGetRequest(authorizationKey).send().join();
              assertThat(retrievedAuthorization.getAuthorizationKey())
                  .isEqualTo(String.valueOf(authorizationKey));
              assertThat(retrievedAuthorization.getResourceId()).isEqualTo(resourceId);
              assertThat(retrievedAuthorization.getResourceType())
                  .isEqualTo(ResourceTypeEnum.RESOURCE);
              assertThat(retrievedAuthorization.getOwnerId()).isEqualTo(ownerId);
              assertThat(retrievedAuthorization.getOwnerType()).isEqualTo(OwnerTypeEnum.USER);
              assertThat(retrievedAuthorization.getPermissionTypes())
                  .isEqualTo(List.of(PermissionTypeEnum.CREATE));
            });
  }
}
