/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.snapshots;

/**
 * Represents a listener which can be added to the {@link PersistedSnapshotStore} to be notified
 * when a new {@link PersistedSnapshot} is persisted at this store.
 */
@FunctionalInterface
public interface PersistedSnapshotListener {

  /**
   * Is called when a new {@link PersistedSnapshot} was persisted.
   *
   * @param newPersistedSnapshot the new persisted snapshots
   */
  void onNewSnapshot(PersistedSnapshot newPersistedSnapshot);
}
