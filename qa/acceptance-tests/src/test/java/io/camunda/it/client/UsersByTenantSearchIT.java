/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.client;

import static io.camunda.qa.util.multidb.CamundaMultiDBExtension.TIMEOUT_DATA_AVAILABILITY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.search.response.TenantUser;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.zeebe.test.util.Strings;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

@MultiDbTest
public class UsersByTenantSearchIT {

  private static CamundaClient camundaClient;

  private static final String USER_USERNAME_1 = "Alice" + Strings.newRandomValidUsername();
  private static final String USER_USERNAME_2 = "Bob" + Strings.newRandomValidUsername();
  private static final String USER_USERNAME_3 = Strings.newRandomValidUsername();
  private static final String TENANT_ID = Strings.newRandomValidTenantId();

  @BeforeAll
  static void setup() {
    createUser(USER_USERNAME_1);
    createUser(USER_USERNAME_2);
    createUser(USER_USERNAME_3);

    camundaClient.newCreateTenantCommand().tenantId(TENANT_ID).name("tenant_name").send().join();
    camundaClient
        .newAssignUserToTenantCommand()
        .username(USER_USERNAME_1)
        .tenantId(TENANT_ID)
        .send()
        .join();
    camundaClient
        .newAssignUserToTenantCommand()
        .username(USER_USERNAME_2)
        .tenantId(TENANT_ID)
        .send()
        .join();

    waitForTenantToBeUpdated();
  }

  @Test
  void shouldReturnUsersByTenant() {
    final var users = camundaClient.newUsersByTenantSearchRequest(TENANT_ID).send().join();

    assertThat(users.items().size()).isEqualTo(2);
    assertThat(users.items())
        .extracting(TenantUser::getUsername)
        .containsExactly(USER_USERNAME_1, USER_USERNAME_2);
  }

  @Test
  void shouldReturnUsersByTenantSorted() {
    final var roles =
        camundaClient
            .newUsersByTenantSearchRequest(TENANT_ID)
            .sort(fn -> fn.username().desc())
            .send()
            .join();

    assertThat(roles.items().size()).isEqualTo(2);
    assertThat(roles.items())
        .extracting(TenantUser::getUsername)
        .containsExactly(USER_USERNAME_2, USER_USERNAME_1);
  }

  @Test
  void shouldRejectIfMissingTenantId() {
    // when / then
    assertThatThrownBy(() -> camundaClient.newUsersByTenantSearchRequest(null).send().join())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("tenantId must not be null");
  }

  @Test
  void shouldRejectIfEmptyTenantId() {
    // when / then
    assertThatThrownBy(() -> camundaClient.newUsersByTenantSearchRequest("").send().join())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("tenantId must not be empty");
  }

  private static void createUser(final String username) {
    camundaClient
        .newCreateUserCommand()
        .username(username)
        .password("password")
        .name("name")
        .email("email@email.com")
        .send()
        .join();
  }

  private static void waitForTenantToBeUpdated() {
    Awaitility.await("should receive data from ES")
        .atMost(TIMEOUT_DATA_AVAILABILITY)
        .ignoreExceptions() // Ignore exceptions and continue retrying
        .untilAsserted(
            () -> {
              final var users =
                  camundaClient.newUsersByTenantSearchRequest(TENANT_ID).send().join();
              assertThat(users.items().size()).isEqualTo(2);
            });
  }
}
