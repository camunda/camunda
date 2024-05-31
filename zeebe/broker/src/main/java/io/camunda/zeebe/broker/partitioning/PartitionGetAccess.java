/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.partitioning;

import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import java.util.Optional;

public interface PartitionGetAccess {
  Optional<PartitionGetAccess> forPartition(int partitionId);

  ActorFuture<RecordValue> getEntity(long key);
}
