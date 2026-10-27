/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service.exception;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.service.exception.ServiceException.Status;
import io.camunda.zeebe.broker.client.api.PartitionInRecoveryException;
import org.junit.jupiter.api.Test;

final class ErrorMapperTest {

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
