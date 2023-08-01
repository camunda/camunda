/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.partitioning.topology;

import io.atomix.primitive.partition.PartitionMetadata;
import java.util.Set;

/**
 * ClusterTopology describes how partitions are distributed across broker. It doesn't keep track of
 * the current leader or followers.
 */
public record ClusterTopology(Set<PartitionMetadata> partitionDistribution) {}
