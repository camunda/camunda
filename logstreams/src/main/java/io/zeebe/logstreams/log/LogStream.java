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

import io.zeebe.dispatcher.Dispatcher;
import io.zeebe.logstreams.impl.LogBlockIndexWriter;
import io.zeebe.logstreams.impl.LogStorageAppender;
import io.zeebe.logstreams.impl.log.index.LogBlockIndex;
import io.zeebe.logstreams.spi.LogStorage;
import io.zeebe.util.sched.ActorCondition;
import io.zeebe.util.sched.future.ActorFuture;
import java.util.function.Supplier;

/**
 * Represents a stream of events from a log storage.
 *
 * <p>Opening the log stream will start the indexing of the current existing log storage. The log
 * storage will separated into blocks, these blocks will be indexed by an LogController. The
 * LogBlockIndex is available and can be accessed via {@link LogStream#getLogBlockIndex()}.
 *
 * <p>The LogStream will append available events to the log storage with the help of an
 * LogController. The events are read from a given Dispatcher, if available. This can be stopped
 * with the {@link LogStream#closeAppender()} method and re-/started with {@link
 * LogStream#openAppender()} or {@link LogStream#closeAppender()}.
 *
 * <p>To access the current LogStorage the {@link LogStream#getLogStorage()} can be used. The {@link
 * #close()} method will close all LogController and the log storage.
 */
public interface LogStream extends AutoCloseable {

  /** @return the partition id of the log stream */
  int getPartitionId();

  /**
   * Returns the name of the log stream.
   *
   * @return the log stream name
   */
  String getLogName();

  /** Closes the log stream synchronously. This blocks until the log stream is closed. */
  @Override
  void close();

  /** Closes the log stream asynchronous. */
  ActorFuture<Void> closeAsync();

  /** @return the current commit position, or a negative value if no entry is committed. */
  long getCommitPosition();

  /** Sets the log streams commit position to the given position. */
  void setCommitPosition(long commitPosition);

  /**
   * Returns the log storage, which is accessed by the LogStream.
   *
   * @return the log storage
   */
  LogStorage getLogStorage();

  /**
   * Returns the LogBlockIndex object, which is used for indexing the LogStorage.
   *
   * @return the log block index
   */
  LogBlockIndex getLogBlockIndex();

  /**
   * Returns the writeBuffer, which is used by the LogStreamController to stream the content into
   * the log storage.
   *
   * @return the writebuffer, which is used by the LogStreamController
   */
  Dispatcher getWriteBuffer();

  /**
   * Returns the log stream controller, which streams the logged events from the write buffer into
   * the log storage.
   *
   * @return the log stream controller
   */
  LogStorageAppender getLogStorageAppender();

  /**
   * Returns the log block index controller, which creates periodically the block index for the log
   * storage.
   *
   * @return the log block index controller
   */
  LogBlockIndexWriter getLogBlockIndexWriter();

  /** Stops the streaming to the log storage. New events are no longer append to the log storage. */
  ActorFuture<Void> closeAppender();

  /**
   * This method installs and opens the log storage appender.
   *
   * @return the future which contains the log storage appender on completion
   */
  ActorFuture<LogStorageAppender> openAppender();

  /**
   * Triggers deletion of data from the log stream, where the given position is used as upper bound.
   *
   * @param position the position as upper bound
   */
  void delete(long position);

  void registerOnCommitPositionUpdatedCondition(ActorCondition condition);

  void removeOnCommitPositionUpdatedCondition(ActorCondition condition);

  void setExporterPositionSupplier(Supplier<Long> supplier);
}
