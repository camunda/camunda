/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.partitioning;

import io.camunda.zeebe.protocol.impl.record.RecordMetadata;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import java.util.Optional;

public interface PartitionGetAccess {
  Optional<PartitionGetAccess> forPartition(int partitionId);

  ActorFuture<Record<? extends UnifiedRecordValue>> getEntity(
      long key, final RecordMetadata metadata);
}
