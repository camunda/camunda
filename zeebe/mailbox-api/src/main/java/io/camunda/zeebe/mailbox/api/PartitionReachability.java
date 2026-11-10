/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.mailbox.api;

/**
 * Decides whether the leader of a partition can be reached over the network. A sender uses the
 * network if it can, and the mailbox otherwise.
 */
@FunctionalInterface
public interface PartitionReachability {

  /**
   * @param partitionId the partition that the sender wants to reach
   * @return true if the sender knows a live leader of the partition that it may contact directly
   */
  boolean isReachable(int partitionId);
}
