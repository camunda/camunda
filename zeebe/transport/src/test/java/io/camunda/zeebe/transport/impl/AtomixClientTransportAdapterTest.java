/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.transport.impl;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.atomix.cluster.messaging.MessagingService;
import io.camunda.zeebe.transport.ClientRequest;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.Test;

final class AtomixClientTransportAdapterTest {

  @Test
  void shouldFailBeforeSerializingRequestWithoutPartitionGroup() {
    // given
    final var request = mock(ClientRequest.class);
    when(request.getPartitionGroup()).thenReturn(null);
    final var transport = new AtomixClientTransportAdapter(mock(MessagingService.class));

    // when
    final var responseFuture =
        transport.sendRequest(() -> "localhost:26500", request, Duration.ofSeconds(1));

    // then
    assertThatThrownBy(responseFuture::join)
        .isInstanceOf(ExecutionException.class)
        .hasCauseInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Cannot send a request without a partition group");
    verify(request, never()).getLength();
    verify(request, never()).write(any(), anyInt());
  }
}
