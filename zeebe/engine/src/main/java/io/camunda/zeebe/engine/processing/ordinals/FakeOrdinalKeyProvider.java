/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.ordinals;

import io.camunda.zeebe.protocol.Protocol;

public class FakeOrdinalKeyProvider implements OrdinalKeyProvider {
  private static final long APPROX_IDS_BETWEEN_ROOT_PROCESS_INSTANCES = 20;

  // aim for roughly 1 hours worth at 300 PI/s (across 3 partitions)
  private final long idsPerOrdinal;

  public FakeOrdinalKeyProvider(final int partitionCount) {
    idsPerOrdinal = (300 * 60 * 60 * APPROX_IDS_BETWEEN_ROOT_PROCESS_INSTANCES) / partitionCount;
  }

  @Override
  public int getOrdinal(final long rootProcessInstanceKey) {
    final long key = Protocol.decodeKeyInPartition(rootProcessInstanceKey);
    return (int) (key / idsPerOrdinal) + 1;
  }
}
