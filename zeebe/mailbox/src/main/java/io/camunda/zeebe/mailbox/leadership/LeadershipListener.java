/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.mailbox.leadership;

/** Learns when this node starts or stops leading a partition. Called from the elector's thread. */
public interface LeadershipListener {

  void onLeading(int partitionId, long term);

  void onNotLeading(int partitionId, long term, Reason reason);

  enum Reason {
    /** Another node claimed the partition with a higher term. */
    TAKEN_OVER,
    /** The node could not renew its lease in time, and stopped leading by itself. */
    FENCED,
    /** The elector was closed and gave the partition up. */
    RELEASED
  }
}
