/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.broker.system.partitions.impl;

import io.zeebe.broker.system.partitions.SnapshotReplication;
import io.zeebe.snapshots.SnapshotChunk;
import java.util.function.Consumer;

public final class NoneSnapshotReplication implements SnapshotReplication {

  @Override
  public void replicate(final SnapshotChunk snapshot) {}

  @Override
  public void consume(final Consumer<SnapshotChunk> consumer) {}

  @Override
  public void close() {}
}
