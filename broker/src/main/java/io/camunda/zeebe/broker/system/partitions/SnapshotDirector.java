/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.system.partitions;

import io.atomix.raft.RaftCommittedEntryListener;
import io.camunda.zeebe.snapshots.PersistedSnapshot;
import java.util.concurrent.Future;

public interface SnapshotDirector extends RaftCommittedEntryListener, AutoCloseable {
  Future<PersistedSnapshot> forceSnapshot();
}
