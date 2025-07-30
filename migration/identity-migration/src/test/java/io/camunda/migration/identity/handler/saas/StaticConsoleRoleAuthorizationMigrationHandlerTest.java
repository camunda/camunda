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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.migration.identity.config.IdentityMigrationProperties;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.service.AuthorizationServices;
import io.camunda.service.AuthorizationServices.CreateAuthorizationRequest;
import io.camunda.service.exception.ErrorMapper;
import io.camunda.zeebe.broker.client.api.BrokerErrorException;
import io.camunda.zeebe.broker.client.api.dto.BrokerError;
import io.camunda.zeebe.protocol.impl.record.value.authorization.AuthorizationRecord;
import io.camunda.zeebe.protocol.record.ErrorCode;
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
    final var migrationProperties = new IdentityMigrationProperties();
    migrationProperties.setBackpressureDelay(100);
    migrationHandler =
        new StaticConsoleRoleAuthorizationMigrationHandler(
            authorizationServices, CamundaAuthentication.none(), migrationProperties);
  }

  @Test
  public void shouldMigrateRoleAuthorizations() {
    when(authorizationServices.createAuthorization(Mockito.any()))
        .thenReturn(CompletableFuture.completedFuture(new AuthorizationRecord()));
    migrationHandler.migrate();

    final var results = ArgumentCaptor.forClass(CreateAuthorizationRequest.class);
    verify(authorizationServices, times(19)).createAuthorization(results.capture());
    final var requests = results.getAllValues();
    assertThat(requests).containsExactlyElementsOf(ROLE_PERMISSIONS);
  }

  @Test
  public void shouldRetryWithBackpressure() {
    // given
    when(authorizationServices.createAuthorization(any(CreateAuthorizationRequest.class)))
        .thenReturn(
            CompletableFuture.failedFuture(
                ErrorMapper.mapError(
                    new BrokerErrorException(
                        new BrokerError(ErrorCode.RESOURCE_EXHAUSTED, "backpressure")))))
        .thenReturn(CompletableFuture.completedFuture(new AuthorizationRecord()));

    // when
    migrationHandler.migrate();

    // then
    verify(authorizationServices, times(20)).createAuthorization(any());
  }
}
