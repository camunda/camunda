/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.mailbox.api.leadership;

/**
 * The lease row of a partition: who leads it, in which term, and a counter that the leader
 * increases with each renewal.
 *
 * <p>Nobody compares timestamps. An observer treats the leader as failed when the counter has not
 * changed for the lease duration, measured on its own clock.
 *
 * @param partitionId the partition
 * @param leaderId the node that leads or led it, not blank
 * @param zone the zone (region) of the leader, not blank
 * @param term the term of the leader, at least 1. Each claim increases it by one.
 * @param renewalCounter increases with each renewal and with a release
 * @param released true if the leader gave the partition up. Another node can claim it at once.
 */
public record PartitionLease(
    int partitionId,
    String leaderId,
    String zone,
    long term,
    long renewalCounter,
    boolean released) {

  public PartitionLease {
    if (leaderId.isBlank()) {
      throw new IllegalArgumentException("leader id must not be blank");
    }
    if (zone.isBlank()) {
      throw new IllegalArgumentException("zone must not be blank");
    }
    if (term < 1) {
      throw new IllegalArgumentException("term must be at least 1");
    }
  }
}
