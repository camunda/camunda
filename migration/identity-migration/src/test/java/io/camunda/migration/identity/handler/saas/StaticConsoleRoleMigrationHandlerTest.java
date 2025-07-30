/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.identity.handler.saas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.migration.identity.client.ConsoleClient;
import io.camunda.migration.identity.client.ConsoleClient.Member;
import io.camunda.migration.identity.client.ConsoleClient.Members;
import io.camunda.migration.identity.client.ConsoleClient.Role;
import io.camunda.migration.identity.config.IdentityMigrationProperties;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.service.RoleServices;
import io.camunda.service.RoleServices.CreateRoleRequest;
import io.camunda.service.RoleServices.RoleMemberRequest;
import io.camunda.service.exception.ErrorMapper;
import io.camunda.zeebe.broker.client.api.BrokerErrorException;
import io.camunda.zeebe.broker.client.api.BrokerRejectionException;
import io.camunda.zeebe.broker.client.api.dto.BrokerError;
import io.camunda.zeebe.broker.client.api.dto.BrokerRejection;
import io.camunda.zeebe.protocol.impl.record.value.authorization.RoleRecord;
import io.camunda.zeebe.protocol.record.ErrorCode;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.GroupIntent;
import io.camunda.zeebe.protocol.record.value.EntityType;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class StaticConsoleRoleMigrationHandlerTest {
  private final RoleServices roleServices;
  private final ConsoleClient consoleClient;

  private final StaticConsoleRoleMigrationHandler migrationHandler;

  public StaticConsoleRoleMigrationHandlerTest(
      @Mock(answer = Answers.RETURNS_SELF) final RoleServices roleServices,
      @Mock(answer = Answers.RETURNS_SELF) final ConsoleClient consoleClient) {
    this.roleServices = roleServices;
    this.consoleClient = consoleClient;
    final var migrationProperties = new IdentityMigrationProperties();
    migrationProperties.setBackpressureDelay(100);
    migrationHandler =
        new StaticConsoleRoleMigrationHandler(
            roleServices, CamundaAuthentication.none(), consoleClient, migrationProperties);
  }

  @Test
  public void shouldMigrateRoles() {
    when(roleServices.createRole(any(CreateRoleRequest.class)))
        .thenReturn(CompletableFuture.completedFuture(new RoleRecord()));
    when(consoleClient.fetchMembers()).thenReturn(new Members(List.of(), List.of()));
    migrationHandler.migrate();

    final var rolesResult = ArgumentCaptor.forClass(CreateRoleRequest.class);
    verify(roleServices, times(4)).createRole(rolesResult.capture());
    final List<CreateRoleRequest> requests = rolesResult.getAllValues();
    assertThat(requests).hasSize(4);
    assertThat(requests.getFirst().roleId()).isEqualTo("developer");
    assertThat(requests.getFirst().name()).isEqualTo("Developer");
    assertThat(requests.get(1).roleId()).isEqualTo("operationsengineer");
    assertThat(requests.get(1).name()).isEqualTo("Operations Engineer");
    assertThat(requests.get(2).roleId()).isEqualTo("taskuser");
    assertThat(requests.get(2).name()).isEqualTo("Task User");
    assertThat(requests.get(3).roleId()).isEqualTo("visitor");
    assertThat(requests.get(3).name()).isEqualTo("Visitor");
  }

  @Test
  public void shouldContinueMigrationIfOneRoleAlreadyExists() {
    when(consoleClient.fetchMembers()).thenReturn(new Members(List.of(), List.of()));
    doReturn(CompletableFuture.completedFuture(null))
        .doReturn(
            CompletableFuture.failedFuture(
                ErrorMapper.mapError(
                    new BrokerRejectionException(
                        new BrokerRejection(
                            GroupIntent.CREATE,
                            -1,
                            RejectionType.ALREADY_EXISTS,
                            "role already exists")))))
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
    assertThat(requests.get(1).roleId()).isEqualTo("operationsengineer");
    assertThat(requests.get(2).roleId()).isEqualTo("taskuser");
    assertThat(requests.get(3).roleId()).isEqualTo("visitor");
  }

  @Test
  public void shouldMigrateRolesAndAddUsersMembership() {
    final var members =
        new ConsoleClient.Members(
            List.of(
                new Member("user1", List.of(Role.DEVELOPER), "user1@email.com", "User One"),
                new Member(
                    "user2", List.of(Role.OPERATIONS_ENGINEER), "user2@email.com", "User Two"),
                new Member("user3", List.of(Role.IGNORED), "user3@email.com", "User Three"),
                new Member("user4", List.of(Role.OWNER), "user4@email.com", "User Four")),
            List.of());
    when(consoleClient.fetchMembers()).thenReturn(members);
    when(roleServices.createRole(any(CreateRoleRequest.class)))
        .thenReturn(CompletableFuture.completedFuture(new RoleRecord()));
    when(roleServices.addMember(any())).thenReturn(CompletableFuture.completedFuture(null));

    migrationHandler.migrate();

    final var membershipResult = ArgumentCaptor.forClass(RoleMemberRequest.class);
    verify(roleServices, times(3)).addMember(membershipResult.capture());

    final List<RoleMemberRequest> requests = membershipResult.getAllValues();
    assertThat(requests).hasSize(3);
    assertThat(requests.getFirst().roleId()).isEqualTo("developer");
    assertThat(requests.getFirst().entityId()).isEqualTo("user1@email.com");
    assertThat(requests.getFirst().entityType()).isEqualTo(EntityType.USER);
    assertThat(requests.get(1).roleId()).isEqualTo("operationsengineer");
    assertThat(requests.get(1).entityId()).isEqualTo("user2@email.com");
    assertThat(requests.get(1).entityType()).isEqualTo(EntityType.USER);

    assertThat(requests.get(2).roleId()).isEqualTo("admin");
    assertThat(requests.get(2).entityId()).isEqualTo("user4@email.com");
    assertThat(requests.get(2).entityType()).isEqualTo(EntityType.USER);
  }

  @Test
  public void shouldRetryWithBackpressureOnRoleCreation() {
    // given
    when(roleServices.createRole(any(CreateRoleRequest.class)))
        .thenReturn(
            CompletableFuture.failedFuture(
                ErrorMapper.mapError(
                    new BrokerErrorException(
                        new BrokerError(ErrorCode.RESOURCE_EXHAUSTED, "backpressure")))))
        .thenReturn(CompletableFuture.completedFuture(new RoleRecord()));
    final var members =
        new ConsoleClient.Members(
            List.of(
                new Member("user1", List.of(Role.DEVELOPER), "user1@email.com", "User One"),
                new Member(
                    "user2", List.of(Role.OPERATIONS_ENGINEER), "user2@email.com", "User Two"),
                new Member("user3", List.of(Role.IGNORED), "user3@email.com", "User Three"),
                new Member("user4", List.of(Role.OWNER), "user4@email.com", "User Four")),
            List.of());
    when(consoleClient.fetchMembers()).thenReturn(members);
    when(roleServices.addMember(any())).thenReturn(CompletableFuture.completedFuture(null));

    // when
    migrationHandler.migrate();

    // then
    verify(roleServices, times(5)).createRole(any(CreateRoleRequest.class));
    verify(roleServices, times(3)).addMember(any(RoleMemberRequest.class));
  }

  @Test
  public void shouldRetryWithBackpressureOnRoleMembershipAssignation() {
    // given
    when(roleServices.createRole(any(CreateRoleRequest.class)))
        .thenReturn(CompletableFuture.completedFuture(new RoleRecord()));
    final var members =
        new ConsoleClient.Members(
            List.of(
                new Member("user1", List.of(Role.DEVELOPER), "user1@email.com", "User One"),
                new Member(
                    "user2", List.of(Role.OPERATIONS_ENGINEER), "user2@email.com", "User Two"),
                new Member("user3", List.of(Role.IGNORED), "user3@email.com", "User Three"),
                new Member("user4", List.of(Role.OWNER), "user4@email.com", "User Four")),
            List.of());
    when(consoleClient.fetchMembers()).thenReturn(members);
    when(roleServices.addMember(any()))
        .thenReturn(
            CompletableFuture.failedFuture(
                ErrorMapper.mapError(
                    new BrokerErrorException(
                        new BrokerError(ErrorCode.RESOURCE_EXHAUSTED, "backpressure")))))
        .thenReturn(CompletableFuture.completedFuture(null));

    // when
    migrationHandler.migrate();

    // then
    verify(roleServices, times(4)).createRole(any(CreateRoleRequest.class));
    verify(roleServices, times(4)).addMember(any(RoleMemberRequest.class));
  }
}
