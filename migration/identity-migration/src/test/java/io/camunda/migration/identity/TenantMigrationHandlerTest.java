/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.identity;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.migration.identity.dto.Tenant;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.command.CreateTenantCommandStep1;
import io.camunda.zeebe.client.api.command.ProblemException;
import io.camunda.zeebe.client.api.response.CreateTenantResponse;
import io.camunda.zeebe.client.impl.ZeebeClientFutureImpl;
import io.camunda.zeebe.client.impl.response.CreateTenantResponseImpl;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class TenantMigrationHandlerTest {
  TenantMigrationHandler migrationHandler;
  @Mock private ManagementIdentityProxy managementIdentityProxy;
  @Mock private ZeebeClient zeebeClient;
  @Mock private CreateTenantCommandStep1 createTenantCommandStep1;

  @BeforeEach
  void setUp() throws Exception {
    MockitoAnnotations.openMocks(this).close();
    when(zeebeClient.newCreateTenantCommand()).thenReturn(createTenantCommandStep1);
    when(createTenantCommandStep1.tenantId(any())).thenReturn(createTenantCommandStep1);
    when(createTenantCommandStep1.name(any())).thenReturn(createTenantCommandStep1);
    migrationHandler = new TenantMigrationHandler(managementIdentityProxy, zeebeClient);
  }

  @AfterEach
  void tearDown() {}

  @Test
  void stopWhenNoMoreRecords() {

    // given
    final ZeebeClientFutureImpl<CreateTenantResponse, Object> future =
        new ZeebeClientFutureImpl<>();
    future.complete(new CreateTenantResponseImpl());
    when(createTenantCommandStep1.send()).thenReturn(future);
    when(managementIdentityProxy.fetchTenants(any(), anyInt()))
        .thenReturn(List.of(new Tenant("id1", "t1"), new Tenant("id2", "t2")))
        .thenReturn(List.of());

    // when
    migrationHandler.migrate();

    // then
    verify(managementIdentityProxy, times(2)).fetchTenants(any(), anyInt());
    verify(createTenantCommandStep1, times(2)).send();
  }

  @Test
  void ignoreWhenTenantAlreadyExists() {
    final ZeebeClientFutureImpl<CreateTenantResponse, Object> future =
        new ZeebeClientFutureImpl<>();
    future.completeExceptionally(new ProblemException(0, "Failed with code 409: 'Conflict'", null));
    when(createTenantCommandStep1.send()).thenReturn(future);

    // given
    when(managementIdentityProxy.fetchTenants(any(), anyInt()))
        .thenReturn(List.of(new Tenant("id1", "t1"), new Tenant("id2", "t2")))
        .thenReturn(List.of());

    // when
    migrationHandler.migrate();

    // then
    verify(managementIdentityProxy, times(2)).fetchTenants(any(), anyInt());
    verify(createTenantCommandStep1, times(2)).send();
  }

  @Test
  void stopWhenTenantCreationHasError() {
    final ZeebeClientFutureImpl<CreateTenantResponse, Object> future =
        new ZeebeClientFutureImpl<>();
    future.completeExceptionally(new ProblemException(0, "runtime exception!", null));
    when(createTenantCommandStep1.send()).thenReturn(future);

    // given
    when(managementIdentityProxy.fetchTenants(any(), anyInt()))
        .thenReturn(List.of(new Tenant("id1", "t1"), new Tenant("id2", "t2")))
        .thenReturn(List.of());

    // when/then
    Assertions.assertThrows(Exception.class, () -> migrationHandler.migrate());

    // then
    verify(managementIdentityProxy, times(1)).fetchTenants(any(), anyInt());
    verify(createTenantCommandStep1, times(1)).send();
  }
}
