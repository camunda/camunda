/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.snapshots;

import io.zeebe.util.CloseableSilently;
import java.nio.ByteBuffer;
import java.util.Iterator;

/**
 * Represents a snapshot chunk reader, which means it is used to chunk an {@link PersistedSnapshot}
 * and read it from it's persisted storage.
 */
public interface SnapshotChunkReader extends Iterator<SnapshotChunk>, CloseableSilently {

  /**
   * Skips all chunks up to the one with the given ID, inclusively, such that the next chunk would
   * be the chunk right after it (if any). If no ID is given then should not do anything.
   *
   * @param id the chunk ID to seek to; maybe null
   */
  void seek(ByteBuffer id);

  /**
   * Returns the next chunk ID; if {@link #hasNext()} should return false, then this will return
   * null.
   *
   * @return the next chunk ID
   */
  ByteBuffer nextId();
}
