/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.distributedlog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.atomix.primitive.partition.PartitionId;
import io.zeebe.distributedlog.impl.DistributedLogstreamName;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.Test;

public class DistributedLogstreamNameTest {

  private static final int PARTITION_COUNT = 8;
  private static final List<PartitionId> PARTITION_IDS =
      IntStream.range(0, PARTITION_COUNT)
          .boxed()
          .map(i -> PartitionId.from("test", i))
          .collect(Collectors.toList());

  private static final DistributedLogstreamName PARTITIONER =
      DistributedLogstreamName.getInstance();

  @Test
  public void shouldFindPartition() {
    final String partitionKey = DistributedLogstreamName.getPartitionKey(5);
    final PartitionId id = PARTITIONER.partition(partitionKey, PARTITION_IDS);
    assertThat(id.id()).isEqualTo(5);
  }

  @Test
  public void shouldThrowExceptionForNonExistingPartition() {
    final String partitionKey = DistributedLogstreamName.getPartitionKey(10);
    assertThatThrownBy(() -> PARTITIONER.partition(partitionKey, PARTITION_IDS))
        .isInstanceOf(NoSuchElementException.class);
  }

  @Test
  public void shouldThrowExceptionForWrongKeyFormat() {
    final String wrongKey = "SomeKey";
    assertThatThrownBy(() -> PARTITIONER.partition(wrongKey, PARTITION_IDS)).isNotNull();
  }
}
