/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.logstreams.restore;

import io.atomix.cluster.MemberId;
import io.atomix.primitive.partition.Partition;
import io.zeebe.distributedlog.restore.RestoreNodeProvider;
import io.zeebe.util.ZbLogger;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.function.Supplier;
import org.slf4j.Logger;

/**
 * Iterates over partition members cyclically, skipping the local node. After each loop, it will
 * refresh the list of members it should use.
 */
public class CyclicPartitionNodeProvider implements RestoreNodeProvider {
  private final Supplier<Partition> partitionSupplier;
  private final String localMemberId;
  private final Queue<MemberId> members;
  private final Logger logger;

  public CyclicPartitionNodeProvider(Supplier<Partition> partitionSupplier, String localMemberId) {
    this.partitionSupplier = partitionSupplier;
    this.localMemberId = localMemberId;
    this.members = new ArrayDeque<>();
    this.logger =
        new ZbLogger(
            String.format(
                "%s-%s", CyclicPartitionNodeProvider.class.getCanonicalName(), localMemberId));
  }

  @Override
  public MemberId provideRestoreNode() {
    return memberQueue().poll();
  }

  private Queue<MemberId> memberQueue() {
    if (members.isEmpty()) {
      final Partition partition = partitionSupplier.get();
      if (partition != null) {
        partition.members().stream()
            .filter(m -> !m.id().equals(localMemberId))
            .forEach(members::add);
      } else {
        logger.warn("No partition provided, cannot provide a restore node");
      }
    }

    return members;
  }
}
