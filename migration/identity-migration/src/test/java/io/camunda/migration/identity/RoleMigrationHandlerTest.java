/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.camunda.security.auth.Authentication;
import io.camunda.service.RoleServices;
import io.camunda.service.RoleServices.CreateRoleRequest;
import io.camunda.zeebe.broker.client.api.BrokerRejectionException;
import io.camunda.zeebe.broker.client.api.dto.BrokerRejection;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.GroupIntent;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class RoleMigrationHandlerTest {
  private final RoleServices roleServices;

  private final RoleMigrationHandler migrationHandler;

  public RoleMigrationHandlerTest(
      @Mock(answer = Answers.RETURNS_SELF) final RoleServices roleServices) {
    this.roleServices = roleServices;
    migrationHandler = new RoleMigrationHandler(roleServices, Authentication.none());
  }

  @Test
  public void shouldMigrateRoles() {
    migrationHandler.migrate();

    final var rolesResult = ArgumentCaptor.forClass(CreateRoleRequest.class);
    verify(roleServices, times(4)).createRole(rolesResult.capture());
    final List<CreateRoleRequest> requests = rolesResult.getAllValues();
    assertThat(requests).hasSize(4);
    assertThat(requests.getFirst().roleId()).isEqualTo("developer");
    assertThat(requests.getFirst().name()).isEqualTo("Developer");
    assertThat(requests.get(1).roleId()).isEqualTo("operationsEngineer");
    assertThat(requests.get(1).name()).isEqualTo("Operations Engineer");
    assertThat(requests.get(2).roleId()).isEqualTo("taskUser");
    assertThat(requests.get(2).name()).isEqualTo("Task User");
    assertThat(requests.get(3).roleId()).isEqualTo("visitor");
    assertThat(requests.get(3).name()).isEqualTo("Visitor");
  }

  @Test
  public void shouldContinueMigrationIfOneRoleAlreadyExists() {
    doReturn(CompletableFuture.completedFuture(null))
        .doReturn(
            CompletableFuture.failedFuture(
                new BrokerRejectionException(
                    new BrokerRejection(
                        GroupIntent.CREATE,
                        -1,
                        RejectionType.ALREADY_EXISTS,
                        "role already exists"))))
        .doReturn(CompletableFuture.completedFuture(null))
        .doReturn(CompletableFuture.completedFuture(null))
        .when(roleServices)
        .createRole(any(CreateRoleRequest.class));

    migrationHandler.migrate();

    final var rolesResult = ArgumentCaptor.forClass(CreateRoleRequest.class);
    verify(roleServices, times(4)).createRole(rolesResult.capture());

    final List<CreateRoleRequest> requests = rolesResult.getAllValues();
    assertThat(requests).hasSize(4);
    assertThat(requests.getFirst().roleId()).isEqualTo("developer");
    assertThat(requests.get(1).roleId()).isEqualTo("operationsEngineer");
    assertThat(requests.get(2).roleId()).isEqualTo("taskUser");
    assertThat(requests.get(3).roleId()).isEqualTo("visitor");
  }
}
