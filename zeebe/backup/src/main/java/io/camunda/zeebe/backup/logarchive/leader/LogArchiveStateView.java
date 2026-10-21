/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.logarchive.leader;

import io.camunda.zeebe.backup.logarchive.state.DbLogArchiveState;
import io.camunda.zeebe.backup.logarchive.state.DbLogArchiveState.OpenObject;
import io.camunda.zeebe.backup.logarchive.state.DbLogArchiveState.UnmergedObject;
import io.camunda.zeebe.backup.logarchive.state.LogArchiveMeta;
import io.camunda.zeebe.protocol.impl.record.value.management.IndexChunk;
import io.camunda.zeebe.protocol.impl.record.value.management.LogObject;
import java.util.List;

/**
 * What the merger and the cleaner read from the replicated state. They only read: proposals are
 * validated by the processor, so a read that lags a little behind the processor is harmless.
 *
 * <p>Each component needs its own view, backed by its own read-only {@link DbLogArchiveState} on
 * its own transaction context ({@link #of}), as {@code DbPositionSupplier} does, because a {@link
 * DbLogArchiveState} is not thread-safe.
 */
public interface LogArchiveStateView {

  LogArchiveMeta meta();

  /** Every archived object above M, in index order. */
  List<UnmergedObject> unmergedObjects();

  /** Every open (not yet sealed) object, in index order. */
  List<OpenObject> openObjects();

  /** Every sealed chunk, in chunk order. */
  List<IndexChunk> chunks();

  /** Every merged object above R, in index order. */
  List<LogObject> mergedObjects();

  static LogArchiveStateView of(final DbLogArchiveState state) {
    return new LogArchiveStateView() {
      @Override
      public LogArchiveMeta meta() {
        return state.meta();
      }

      @Override
      public List<UnmergedObject> unmergedObjects() {
        return state.unmergedObjects();
      }

      @Override
      public List<OpenObject> openObjects() {
        return state.openObjects();
      }

      @Override
      public List<IndexChunk> chunks() {
        return state.chunks();
      }

      @Override
      public List<LogObject> mergedObjects() {
        return state.mergedObjects();
      }
    };
  }
}
