/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.immutable;

/**
 * Tracks, on the aggregating partition, which partitions still owe a drain report for a process
 * definition being deleted while it still has running instances. Seeded with all partitions at
 * delete time and drained down as each partition reports; an empty set means every partition has
 * drained.
 */
public interface ProcessDeleteDrainState {

  /** Whether the given partition still owes a drain report for the given definition. */
  boolean hasDrainingPartition(long processDefinitionKey, int partitionId);

  /** Whether any partition still owes a drain report for the given definition. */
  boolean hasDrainingPartition(long processDefinitionKey);
}
