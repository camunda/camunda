/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.logstreams.log;

import io.zeebe.util.CloseableSilently;
import java.util.Iterator;

/**
 * Reads the log stream in an iterator-like pattern. Common usage:
 *
 * <pre>
 * <code>
 * reader.wrap(log);
 *
 * // optionally seek to a position
 * reader.seek(position);
 *
 * while(reader.hasNext())
 * {
 *     final LoggedEvent event = reader.next();
 *     // process the event..
 * }
 * </code>
 * </pre>
 */
public interface LogStreamReader extends Iterator<LoggedEvent>, CloseableSilently {

  /**
   * Seeks to the event after the given position. On negative position it seeks to the first event.
   *
   * @param position the position which should be used
   * @return <code>true</code>, if the position is negative or exists
   */
  boolean seekToNextEvent(long position);

  /**
   * Seek to the given log position if exists. Otherwise, it seek to the next position after this.
   *
   * @param position the position in the log to seek to
   * @return <code>true</code>, if the given position exists.
   */
  boolean seek(long position);

  /** Seek to the log position of the first event. */
  void seekToFirstEvent();

  /**
   * Seek to the end of the log, which means after the last event.
   *
   * @return the position of the last entry
   */
  long seekToEnd();

  /**
   * Returns the current log position of the reader.
   *
   * @return the current log position, or negative value if the log is empty or not initialized
   */
  long getPosition();

  /**
   * The last log storage address, from which the last block of events was read.
   *
   * <p/>
   * Useful if you want to found out the related block address, then just seek to a given position
   * and call {@link #lastReadAddress).
   *
   * *Note:* The returned address is not the exact log event address.
   *
   * @return the last log storage address, from which the last block of events was read.
   */
  long lastReadAddress();

  /**
   * Returns true if the log stream reader was closed.
   *
   * @return true if closed, false otherwise
   */
  boolean isClosed();
}
