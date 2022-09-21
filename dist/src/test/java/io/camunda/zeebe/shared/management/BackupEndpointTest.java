/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.shared.management;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import io.camunda.zeebe.gateway.admin.backup.BackupApi;
import io.camunda.zeebe.shared.management.BackupEndpoint.TakeBackupError;
import io.camunda.zeebe.shared.management.BackupEndpoint.TakeBackupResponse;
import java.util.concurrent.CompletableFuture;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.boot.actuate.endpoint.web.WebEndpointResponse;

@Execution(ExecutionMode.CONCURRENT)
final class BackupEndpointTest {
  @Test
  void shouldReturnErrorOnCompletionException() {
    // given
    final var api = mock(BackupApi.class);
    final var endpoint = new BackupEndpoint(api);
    final var failure = new RuntimeException("failure");
    doReturn(CompletableFuture.failedFuture(failure)).when(api).takeBackup(anyLong());

    // when
    final WebEndpointResponse<?> response = endpoint.take(1);

    // then
    assertThat(response.getBody())
        .asInstanceOf(InstanceOfAssertFactories.type(TakeBackupError.class))
        .isEqualTo(new TakeBackupError(1, "failure"));
  }

  @Test
  void shouldReturnErrorOnException() {
    // given
    final var api = mock(BackupApi.class);
    final var endpoint = new BackupEndpoint(api);
    final var failure = new RuntimeException("failure");
    doThrow(failure).when(api).takeBackup(anyLong());

    // when
    final WebEndpointResponse<?> response = endpoint.take(1);

    // then
    assertThat(response.getBody())
        .asInstanceOf(InstanceOfAssertFactories.type(TakeBackupError.class))
        .isEqualTo(new TakeBackupError(1, "failure"));
  }

  @Test
  void shouldReturnNewBackupIdOnSuccess() {
    // given
    final var api = mock(BackupApi.class);
    final var endpoint = new BackupEndpoint(api);
    doReturn(CompletableFuture.completedFuture(3L)).when(api).takeBackup(anyLong());

    // when
    final WebEndpointResponse<?> response = endpoint.take(1);

    // then
    assertThat(response.getBody())
        .asInstanceOf(InstanceOfAssertFactories.type(TakeBackupResponse.class))
        .isEqualTo(new TakeBackupResponse(3));
  }
}
