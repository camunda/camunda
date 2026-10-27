/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.partitioning.topology;

import io.atomix.primitive.partition.PartitionMetadata;
import java.util.Set;

/**
 * PartitionDistribution describes how partitions are distributed across broker. It doesn't keep
 * track of the current leader or followers.
 */
public record PartitionDistribution(Set<PartitionMetadata> partitions) {

  public static final PartitionDistribution NO_PARTITIONS = new PartitionDistribution(Set.of());
}
