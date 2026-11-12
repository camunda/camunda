/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.cache;

/**
 * The gate of a storage that only this replica uses, like RocksDB: every transaction counts as
 * committed, since the replica throws the storage away when it stops being the leader.
 */
final class NoCommitGate implements CommitGate {

  static final CommitGate INSTANCE = new NoCommitGate();

  private NoCommitGate() {}

  @Override
  public long lastWrittenPosition() {
    return 0;
  }

  @Override
  public long committedPosition() {
    return Long.MAX_VALUE;
  }

  @Override
  public void addCommitListener(final Runnable listener) {}

  @Override
  public void removeCommitListener(final Runnable listener) {}
}
