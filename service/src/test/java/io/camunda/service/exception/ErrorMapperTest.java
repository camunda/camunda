/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service.exception;

import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.cluster.messaging.MessagingException;
import io.camunda.service.exception.ServiceException.Status;
import io.camunda.zeebe.broker.client.api.PartitionInRecoveryException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.Test;

final class ErrorMapperTest {

  @Test
  void shouldMapUnknownCoordinatorMemberToUnavailable() {
    // given -- the coordinator was removed from the membership view, e.g. after a pod restart
    final var error = new MessagingException.NoSuchMemberException("member '0'");

    // when
    final ServiceException exception = ErrorMapper.mapCoordinatorError(error);

    // then -- a retryable status, not INTERNAL, so clients keep polling the endpoint
    assertThat(exception.getStatus()).isEqualTo(Status.UNAVAILABLE);
  }

  @Test
  void shouldMapCoordinatorNotReadyToUnavailable() {
    // given -- the coordinator is a member but has not registered its request handler yet
    final var error = new MessagingException.NoRemoteHandler("topology-query");

    // when
    final ServiceException exception = ErrorMapper.mapCoordinatorError(error);

    // then -- a retryable status, not INTERNAL
    assertThat(exception.getStatus()).isEqualTo(Status.UNAVAILABLE);
  }

  @Test
  void shouldMapCoordinatorCauseWrappedInCompletionAndExecutionExceptions() {
    // given -- the failure reaches the caller wrapped by the futures it travelled through
    final var error =
        new CompletionException(
            new ExecutionException(new MessagingException.NoSuchMemberException("member '0'")));

    // when
    final ServiceException exception = ErrorMapper.mapCoordinatorError(error);

    // then
    assertThat(exception.getStatus()).isEqualTo(Status.UNAVAILABLE);
  }

  @Test
  void shouldKeepUnknownMemberInternalOutsideCoordinatorRequests() {
    // given -- a request sent to a non-coordinator member must not be reported as retryable
    final var error = new MessagingException.NoSuchMemberException("member '2'");

    // when
    final ServiceException exception = ErrorMapper.mapError(error);

    // then
    assertThat(exception.getStatus()).isEqualTo(Status.INTERNAL);
  }

  @Test
  void shouldMapPartitionInRecoveryExceptionToUnavailable() {
    // given
    final var error = new PartitionInRecoveryException(1);

    // when
    final ServiceException exception = ErrorMapper.mapError(error);

    // then -- the client gets a retryable status with the recovery explanation, not INTERNAL
    assertThat(exception.getStatus()).isEqualTo(Status.UNAVAILABLE);
    assertThat(exception.getMessage()).contains("recovery mode");
  }
}
