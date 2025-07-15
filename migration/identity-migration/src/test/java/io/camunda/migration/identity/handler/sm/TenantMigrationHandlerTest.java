/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.identity.handler.sm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.identity.sdk.users.dto.User;
import io.camunda.migration.identity.client.ManagementIdentityClient;
import io.camunda.migration.identity.dto.Client;
import io.camunda.migration.identity.dto.Group;
import io.camunda.migration.identity.dto.Tenant;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.service.TenantServices;
import io.camunda.service.TenantServices.TenantDTO;
import io.camunda.service.TenantServices.TenantMemberRequest;
import io.camunda.zeebe.broker.client.api.BrokerRejectionException;
import io.camunda.zeebe.broker.client.api.dto.BrokerRejection;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.GroupIntent;
import io.camunda.zeebe.protocol.record.value.EntityType;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.apache.commons.lang3.NotImplementedException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class TenantMigrationHandlerTest {

  private final ManagementIdentityClient managementIdentityClient;
  private final TenantServices tenantServices;
  private final TenantMigrationHandler migrationHandler;

  public TenantMigrationHandlerTest(
      @Mock final ManagementIdentityClient managementIdentityClient,
      @Mock(answer = Answers.RETURNS_SELF) final TenantServices tenantServices) {
    this.managementIdentityClient = managementIdentityClient;
    this.tenantServices = tenantServices;
    migrationHandler =
        new TenantMigrationHandler(
            managementIdentityClient, tenantServices, CamundaAuthentication.none());
  }

  @Test
  public void shouldMigrateTenants() {
    // given
    when(managementIdentityClient.fetchTenants())
        .thenReturn(
            List.of(
                new Tenant("tenant1", "Tenant 1"),
                new Tenant("tenant2", "Tenant 2"),
                new Tenant("<default>", "Default Tenant")));
    when(tenantServices.createTenant(any(TenantDTO.class)))
        .thenReturn(CompletableFuture.completedFuture(null));
    when(tenantServices.addMember(any(TenantMemberRequest.class)))
        .thenReturn(CompletableFuture.completedFuture(null));
    when(managementIdentityClient.fetchTenantUsers("tenant1"))
        .thenReturn(
            List.of(
                new User("id", "username1", "name", "email"),
                new User("id2", "username2", "name2", "email2")));
    when(managementIdentityClient.fetchTenantUsers("tenant2"))
        .thenReturn(
            List.of(
                new User("id3", "username3", "name3", "email3"),
                new User("id4", "username4", "name4", "email4")));
    when(managementIdentityClient.fetchTenantUsers("<default>"))
        .thenReturn(List.of(new User("id4", "username4", "name4", "email4")));

    when(managementIdentityClient.fetchTenantGroups("tenant1"))
        .thenReturn(List.of(new Group("group1", "Group 1")));
    when(managementIdentityClient.fetchTenantGroups("tenant2"))
        .thenReturn(List.of(new Group("group2", "Group 2")));
    when(managementIdentityClient.fetchTenantGroups("<default>"))
        .thenReturn(List.of(new Group("group3", "Group 3")));
    when(managementIdentityClient.fetchTenantClients("tenant1"))
        .thenReturn(List.of(new Client("client1", "Client 1"), new Client("client2", "Client 2")));
    when(managementIdentityClient.fetchTenantClients("tenant2"))
        .thenReturn(List.of(new Client("client3", "Client 3")));
    when(managementIdentityClient.fetchTenantClients("<default>"))
        .thenReturn(List.of(new Client("client4", "Client 4")));

    // when
    migrationHandler.migrate();

    // then
    final var tenantCapture = ArgumentCaptor.forClass(TenantDTO.class);
    verify(tenantServices, times(2)).createTenant(tenantCapture.capture());
    final var capturedTenants = tenantCapture.getAllValues();
    assertThat(capturedTenants).hasSize(2);
    assertThat(capturedTenants.getFirst().tenantId()).isEqualTo("tenant1");
    assertThat(capturedTenants.getFirst().name()).isEqualTo("Tenant 1");
    assertThat(capturedTenants.getLast().tenantId()).isEqualTo("tenant2");
    assertThat(capturedTenants.getLast().name()).isEqualTo("Tenant 2");

    final var memberCapture = ArgumentCaptor.forClass(TenantMemberRequest.class);
    verify(tenantServices, times(12)).addMember(memberCapture.capture());
    assertThat(memberCapture.getAllValues())
        .extracting(
            TenantMemberRequest::tenantId,
            TenantMemberRequest::entityId,
            TenantMemberRequest::entityType)
        .containsExactlyInAnyOrder(
            // Users for tenant1
            tuple("tenant1", "username1", EntityType.USER),
            tuple("tenant1", "username2", EntityType.USER),
            // Groups for tenant1
            tuple("tenant1", "group_1", EntityType.GROUP),
            // Clients for tenant1
            tuple("tenant1", "client_1", EntityType.CLIENT),
            tuple("tenant1", "client_2", EntityType.CLIENT),
            // Users for tenant2
            tuple("tenant2", "username3", EntityType.USER),
            tuple("tenant2", "username4", EntityType.USER),
            // Groups for tenant2
            tuple("tenant2", "group_2", EntityType.GROUP),
            // Clients for tenant2
            tuple("tenant2", "client_3", EntityType.CLIENT),
            // Users for default tenant
            tuple("<default>", "username4", EntityType.USER),
            // Groups for default tenant
            tuple("<default>", "group_3", EntityType.GROUP),
            // Clients for default tenant
            tuple("<default>", "client_4", EntityType.CLIENT));
  }

  @Test
  public void shouldContinueMigrationWithEndpointsUnavailable() {
    when(managementIdentityClient.fetchTenants())
        .thenThrow(new NotImplementedException("Tenants endpoint unavailable"));
    verify(tenantServices, times(0)).createTenant(any(TenantDTO.class));

    when(managementIdentityClient.fetchTenantUsers(anyString()))
        .thenThrow(new NotImplementedException("Tenant users endpoint unavailable"));
    when(managementIdentityClient.fetchTenantGroups(anyString()))
        .thenThrow(new NotImplementedException("Tenant groups endpoint unavailable"));
    when(managementIdentityClient.fetchTenantClients(anyString()))
        .thenThrow(new NotImplementedException("Tenant clients endpoint unavailable"));
    verify(tenantServices, times(0)).addMember(any(TenantMemberRequest.class));
  }

  @Test
  public void shouldContinueMigrationIfConflicts() {
    // given
    when(managementIdentityClient.fetchTenants())
        .thenReturn(List.of(new Tenant("tenant1", "Tenant 1"), new Tenant("tenant2", "Tenant 2")));
    when(tenantServices.createTenant(any(TenantDTO.class)))
        .thenReturn(
            CompletableFuture.failedFuture(
                new BrokerRejectionException(
                    new BrokerRejection(
                        GroupIntent.CREATE,
                        -1,
                        RejectionType.ALREADY_EXISTS,
                        "tenant already exists"))));
    when(tenantServices.addMember(any(TenantMemberRequest.class)))
        .thenReturn(
            CompletableFuture.failedFuture(
                new BrokerRejectionException(
                    new BrokerRejection(
                        GroupIntent.ADD_ENTITY,
                        -1,
                        RejectionType.ALREADY_EXISTS,
                        "member already exists"))));
    when(managementIdentityClient.fetchTenantUsers(anyString()))
        .thenReturn(
            List.of(
                new User("id", "username1", "name", "email"),
                new User("id2", "username2", "name2", "email2")))
        .thenReturn(
            List.of(
                new User("id3", "username3", "name3", "email3"),
                new User("id4", "username4", "name4", "email4")));

    when(managementIdentityClient.fetchTenantGroups(anyString()))
        .thenReturn(List.of(new Group("group1", "Group 1")))
        .thenReturn(List.of(new Group("group2", "Group 2")));
    when(managementIdentityClient.fetchTenantClients(anyString()))
        .thenReturn(List.of(new Client("client1", "Client 1"), new Client("client2", "Client 2")))
        .thenReturn(List.of(new Client("client3", "Client 3")));

    // when
    migrationHandler.migrate();

    // then
    verify(tenantServices, times(2)).createTenant(any(TenantDTO.class));
    verify(tenantServices, times(9)).addMember(any(TenantMemberRequest.class));
  }
}
