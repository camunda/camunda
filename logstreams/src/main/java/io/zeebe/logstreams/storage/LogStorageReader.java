/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.logstreams.storage;

import io.zeebe.logstreams.log.LoggedEvent;
import io.zeebe.logstreams.storage.LogStorage.AppendListener;
import java.io.Closeable;
import java.nio.ByteBuffer;
import java.util.Iterator;
import org.agrona.DirectBuffer;

/**
 * The LogStorageReader provides a way to iterate over the blocks that were appended to the log
 * storage via {@link LogStorage#append(long, long, ByteBuffer, AppendListener)}.
 *
 * <p>On creation the reader should be positioned such that {@link #next()} would return the first
 * block (assuming there is one).
 *
 * <p>The expected access pattern is to seek first, then read iteratively by calling {@link
 * #next()}.
 */
public interface LogStorageReader extends Iterator<DirectBuffer>, Closeable {

  /**
   * Positions the reader such that the next call to {@link #next()} would return a block which
   * contains a {@link io.zeebe.logstreams.log.LoggedEvent} with {@link LoggedEvent#getPosition()}
   * equal to the given {@code position}, or the highest one which is less than the given {@code
   * position}.
   *
   * <p>If the {@code position} is negative, it should seek to the first position.
   *
   * <p>If the {@code position} is greater than the greatest position stored, it should seek to the
   * last block, such that {@link #next()} would return that block.
   *
   * @param position the position to seek to
   */
  void seek(final long position);

  @Override
  void close();
}
