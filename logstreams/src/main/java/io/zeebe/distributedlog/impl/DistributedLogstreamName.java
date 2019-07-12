/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.distributedlog.impl;

import io.atomix.primitive.partition.PartitionId;
import io.atomix.primitive.partition.Partitioner;
import java.util.List;
import java.util.Optional;

public final class DistributedLogstreamName implements Partitioner<String> {
  private static final DistributedLogstreamName DEFAULT_INSTANCE;

  static {
    DEFAULT_INSTANCE = new DistributedLogstreamName();
  }

  private DistributedLogstreamName() {}

  public static String getPartitionKey(int partitionId) {
    return String.valueOf(partitionId);
  }

  public static int getPartitionId(String partitionKey) {
    return Integer.parseInt(partitionKey);
  }

  public static DistributedLogstreamName getInstance() {
    return DEFAULT_INSTANCE;
  }

  @Override
  public PartitionId partition(String key, List<PartitionId> partitions) {
    final int id = Integer.parseInt(key);
    final Optional<PartitionId> partitionId =
        partitions.stream().filter(p -> p.id() == id).findFirst();
    return partitionId.get();
  }
}
