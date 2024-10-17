/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.identity.migration;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.identity.migration.dto.UserResourceAuthorization;
import io.camunda.service.AuthorizationServices;
import io.camunda.zeebe.protocol.impl.record.value.authorization.AuthorizationRecord;
import java.util.Collection;
import java.util.List;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class AuthorizationMigrationHandlerTest {

  @Mock AuthorizationServices<AuthorizationRecord> authorizationServices;
  @Mock ManagementIdentityProxy managementIdentityProxy;
  AuthorizationMigrationHandler migrationHandler;

  @BeforeEach
  void setUp() throws Exception {
    MockitoAnnotations.openMocks(this).close();
    migrationHandler =
        new AuthorizationMigrationHandler(authorizationServices, managementIdentityProxy);
  }

  @AfterEach
  void tearDown() {}

  @Test
  void stopWhenNoMoreRecords() {
    // given
    when(managementIdentityProxy.fetchUserResourceAuthorizations(any(), anyInt()))
        .thenReturn(
            List.of(
                new UserResourceAuthorization(
                    "username", "resourceId", "process-definition", "create")))
        .thenReturn(List.of());

    // when
    migrationHandler.migrate();

    // then
    verify(managementIdentityProxy, times(2)).fetchUserResourceAuthorizations(any(), anyInt());
  }

  @Test
  void groupedByOwnerResourceType() {
    // given
    when(managementIdentityProxy.fetchUserResourceAuthorizations(any(), anyInt()))
        .thenReturn(
            List.of(
                new UserResourceAuthorization(
                    "username-1", "resourceId-1", "process-definition", "create"),
                new UserResourceAuthorization(
                    "username-1", "resourceId-2", "process-definition", "delete"),
                new UserResourceAuthorization(
                    "username-1", "resourceId-4", "decision-definition", "create"),
                new UserResourceAuthorization(
                    "username-2", "resourceId-1", "decision-definition", "create"),
                new UserResourceAuthorization(
                    "username-1", "resourceId-3", "process-definition", "write")))
        .thenReturn(List.of());

    // when
    migrationHandler.migrate();

    // then
    verify(authorizationServices, times(3)).patchAuthorization(any());

    final ArgumentCaptor<Collection<UserResourceAuthorization>> migratedCaptor =
        ArgumentCaptor.forClass(Collection.class);

    verify(managementIdentityProxy, times(3)).markAsMigrated(migratedCaptor.capture());

    assertThat(
        migratedCaptor.getAllValues().get(0),
        Matchers.containsInAnyOrder(
            new UserResourceAuthorization(
                "username-1", "resourceId-1", "process-definition", "create"),
            new UserResourceAuthorization(
                "username-1", "resourceId-2", "process-definition", "delete"),
            new UserResourceAuthorization(
                "username-1", "resourceId-3", "process-definition", "write")));

    assertThat(
        migratedCaptor.getAllValues().get(1),
        Matchers.is(
            List.of(
                new UserResourceAuthorization(
                    "username-1", "resourceId-4", "decision-definition", "create"))));
    assertThat(
        migratedCaptor.getAllValues().get(2),
        Matchers.is(
            List.of(
                new UserResourceAuthorization(
                    "username-2", "resourceId-1", "decision-definition", "create"))));
  }
}
