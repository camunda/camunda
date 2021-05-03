/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.dispatcher.impl.log;

import static io.camunda.zeebe.dispatcher.impl.log.LogBufferDescriptor.PARTITION_COUNT;
import static io.camunda.zeebe.dispatcher.impl.log.LogBufferDescriptor.PARTITION_META_DATA_LENGTH;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.util.allocation.AllocatedBuffer;
import io.camunda.zeebe.util.allocation.BufferAllocators;
import org.junit.Before;
import org.junit.Test;

public final class PartitionBuilderTest {

  PartitionBuilder partitionBuilder;

  @Before
  public void setup() {
    partitionBuilder = new PartitionBuilder();
  }

  @Test
  public void shouldSlicePartitions() {
    final int partitionSize = 1024;
    final int capacity =
        (PARTITION_COUNT * partitionSize) + (PARTITION_COUNT * PARTITION_META_DATA_LENGTH);
    final AllocatedBuffer buffer = BufferAllocators.allocateDirect(capacity);

    final LogBufferPartition[] partitions = partitionBuilder.slicePartitions(partitionSize, buffer);

    assertThat(partitions.length).isEqualTo(PARTITION_COUNT);

    for (final LogBufferPartition logBufferPartition : partitions) {
      assertThat(logBufferPartition.getPartitionSize()).isEqualTo(partitionSize);
      assertThat(logBufferPartition.getDataBuffer().capacity()).isEqualTo(partitionSize);
    }
  }
}
