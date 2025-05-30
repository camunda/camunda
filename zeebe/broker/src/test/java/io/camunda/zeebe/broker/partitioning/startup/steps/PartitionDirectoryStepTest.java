/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.partitioning.startup.steps;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.broker.partitioning.startup.PartitionStartupContext;
import io.camunda.zeebe.scheduler.testing.TestConcurrencyControl;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Answers;

public class PartitionDirectoryStepTest {
  @TempDir Path path;
  PartitionStartupContext context = mock(PartitionStartupContext.class, Answers.RETURNS_DEEP_STUBS);
  TestConcurrencyControl concurrency = new TestConcurrencyControl();
  PartitionDirectoryStep step = new PartitionDirectoryStep();
  Path expectedPartitionPath;
  Path expectedTempPath;

  @BeforeEach
  void setup() {
    when(context.brokerConfig().getData().getDirectory()).thenReturn(path.toString());
    when(context.partitionId()).thenReturn(1);
    when(context.concurrencyControl()).thenReturn(concurrency);
    // to make fluent api work
    when(context.partitionDirectory(any())).thenReturn(context);
    expectedPartitionPath = path.resolve("raft-partition/partitions/1");
    expectedTempPath = path.resolve("raft-partition/temporary-partitions/1");
  }

  @Test
  void shouldCreatePartitionDirectories() {
    // when
    assertThat(step.startup(context)).succeedsWithin(Duration.ofSeconds(10));
    // then
    verify(context).partitionDirectory(argThat(p -> p.equals(expectedPartitionPath)));
    verify(context).temporaryPartitionDirectory(argThat(p -> p.equals(expectedTempPath)));

    assertThat(expectedPartitionPath).exists();
    assertThat(expectedTempPath).exists();
  }

  @Test
  void shouldDeleteTempPartitionWhenShuttingDown() {
    // when
    assertThat(step.startup(context)).succeedsWithin(Duration.ofSeconds(10));
    when(context.temporaryPartitionDirectory()).thenReturn(expectedTempPath);
    assertThat(step.shutdown(context)).succeedsWithin(Duration.ofSeconds(10));

    // then
    assertThat(expectedTempPath).doesNotExist();
    verify(context).temporaryPartitionDirectory(null);
    verify(context).partitionDirectory(null);
  }
}
