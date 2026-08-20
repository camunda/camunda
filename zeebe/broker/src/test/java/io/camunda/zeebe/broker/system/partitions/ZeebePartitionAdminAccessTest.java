/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.partitions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.scheduler.testing.TestConcurrencyControl;
import io.camunda.zeebe.stream.impl.StreamProcessor;
import java.io.IOException;
import java.time.Duration;
import org.junit.jupiter.api.Test;

final class ZeebePartitionAdminAccessTest {

  private final PartitionAdminControl adminControl = mock(PartitionAdminControl.class);
  private final ZeebePartitionAdminAccess sut =
      new ZeebePartitionAdminAccess(new TestConcurrencyControl(), 1, adminControl);

  @Test
  void shouldCompleteResumeProcessingOnlyAfterStreamProcessorResumes() throws IOException {
    // given
    final var streamProcessor = mock(StreamProcessor.class);
    final CompletableActorFuture<Void> processorResume = new CompletableActorFuture<>();
    when(adminControl.getStreamProcessor()).thenReturn(streamProcessor);
    when(adminControl.shouldProcess()).thenReturn(true);
    when(streamProcessor.resumeProcessing()).thenReturn(processorResume);

    // when
    final ActorFuture<Void> resumed = sut.resumeProcessing();

    // then
    verify(adminControl).resumeProcessing();
    assertThat(resumed).isNotDone();

    // when
    processorResume.complete(null);

    // then
    assertThat(resumed).succeedsWithin(Duration.ofSeconds(5));
  }
}
