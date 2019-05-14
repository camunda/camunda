/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
   * Initialize the reader and seek to the first event.
   *
   * @param log the stream which provides the log
   */
  void wrap(LogStream log);

  /**
   * Initialize the reader and seek to the given log position.
   *
   * @param log the stream which provides the log
   * @param position the position in the log to seek to
   */
  void wrap(LogStream log, long position);

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

  /** Seek to the log position of the last event. */
  void seekToLastEvent();

  /**
   * Returns the current log position of the reader.
   *
   * @return the current log position, or negative value if the log is empty or not initialized
   */
  long getPosition();

  /**
   * Returns true if the log stream reader was closed.
   *
   * @return true if closed, false otherwise
   */
  boolean isClosed();
}
