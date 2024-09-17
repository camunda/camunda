/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.stream.api;

/**
 * Provides context information about the cluster such as all partition ids. This information is not
 * updated when cluster topology changes and is only meant for initialization where correctness or
 * consistency is not required.
 */
public interface ClusterContext {

  /**
   * Returns the number of partitions in the cluster. Reflects some state of the cluster at the time
   * of processor initialization and does not update when the cluster topology changes.
   */
  int partitionCount();
}
