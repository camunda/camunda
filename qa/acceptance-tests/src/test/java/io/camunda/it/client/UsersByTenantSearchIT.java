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
import io.camunda.qa.util.auth.TenantDefinition;
import io.camunda.qa.util.auth.TestTenant;
import io.camunda.qa.util.auth.TestUser;
import io.camunda.qa.util.auth.UserDefinition;
import io.camunda.qa.util.compatibility.CompatibilityTest;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.zeebe.test.util.Strings;
import java.util.List;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

@MultiDbTest
@CompatibilityTest
public class UsersByTenantSearchIT {

  private static CamundaClient camundaClient;

  private static final String USER_USERNAME_1 = "Alice" + Strings.newRandomValidUsername();
  private static final String USER_USERNAME_2 = "Bob" + Strings.newRandomValidUsername();
  private static final String USER_USERNAME_3 = Strings.newRandomValidUsername();
  private static final String TENANT_ID = Strings.newRandomValidTenantId();

  @UserDefinition
  private static final TestUser USER_1 = new TestUser(USER_USERNAME_1, "password", List.of());

  @UserDefinition
  private static final TestUser USER_2 = new TestUser(USER_USERNAME_2, "password", List.of());

  @TenantDefinition
  private static final TestTenant TENANT =
      new TestTenant(TENANT_ID).setName("tenant_name").addUsers(USER_USERNAME_1, USER_USERNAME_2);

  @BeforeAll
  static void setup() {
    createUser(USER_USERNAME_3);

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
