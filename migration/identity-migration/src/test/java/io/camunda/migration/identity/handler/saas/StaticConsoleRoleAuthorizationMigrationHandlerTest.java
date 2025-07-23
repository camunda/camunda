/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.identity.handler.saas;

import static io.camunda.migration.identity.config.saas.StaticEntities.ROLE_PERMISSIONS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.service.AuthorizationServices;
import io.camunda.service.AuthorizationServices.CreateAuthorizationRequest;
import io.camunda.zeebe.protocol.impl.record.value.authorization.AuthorizationRecord;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class StaticConsoleRoleAuthorizationMigrationHandlerTest {

  private final AuthorizationServices authorizationServices;
  private final StaticConsoleRoleAuthorizationMigrationHandler migrationHandler;

  public StaticConsoleRoleAuthorizationMigrationHandlerTest(
      @Mock(answer = Answers.RETURNS_SELF) final AuthorizationServices authorizationServices) {
    this.authorizationServices = authorizationServices;
    migrationHandler =
        new StaticConsoleRoleAuthorizationMigrationHandler(
            authorizationServices, CamundaAuthentication.none());
  }

  @Test
  public void shouldMigrateRoleAuthorizations() {
    when(authorizationServices.createAuthorization(Mockito.any()))
        .thenReturn(CompletableFuture.completedFuture(new AuthorizationRecord()));
    migrationHandler.migrate();

    final var results = ArgumentCaptor.forClass(CreateAuthorizationRequest.class);
    Mockito.verify(authorizationServices, Mockito.times(19)).createAuthorization(results.capture());
    final var requests = results.getAllValues();
    assertThat(requests).containsExactlyElementsOf(ROLE_PERMISSIONS);
  }
}
