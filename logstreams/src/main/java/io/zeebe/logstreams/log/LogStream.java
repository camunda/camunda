/**
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

import static io.zeebe.util.buffer.BufferUtil.wrapString;

import java.util.concurrent.CompletableFuture;

import org.agrona.DirectBuffer;
import io.zeebe.dispatcher.Dispatcher;
import io.zeebe.logstreams.impl.LogBlockIndexController;
import io.zeebe.logstreams.impl.LogStreamController;
import io.zeebe.logstreams.impl.log.index.LogBlockIndex;
import io.zeebe.logstreams.spi.LogStorage;
import io.zeebe.util.actor.ActorScheduler;


/**
 * Represents a stream of events from a log storage.
 *
 * Opening the log stream will start the indexing of the current existing log storage.
 * The log storage will separated into blocks, these blocks will be indexed by an LogController.
 * The LogBlockIndex is available and can be accessed via {@link LogStream#getLogBlockIndex()}.
 *
 * The LogStream will append available events to the log storage with the help of an LogController.
 * The events are read from a given Dispatcher, if available. This can be stopped with the
 * {@link LogStream#closeLogStreamController()}  method and re-/started with {@link LogStream#openLogStreamController(AgentRunnerService)}
 * or {@link LogStream#openLogStreamController(AgentRunnerService, int)}.
 *
 * To access the current LogStorage the {@link LogStream#getLogStorage()} can be used. The {@link #close()}
 * method will close all LogController and the log storage.
 */
public interface LogStream extends AutoCloseable
{
    int DEFAULT_WRITE_BUFFER_SIZE = 1024 * 1024 * 16;
    int DEFAULT_MAX_APPEND_BLOCK_SIZE = 1024 * 1024 * 4;

    String DEFAULT_TOPIC_NAME = "default-topic";
    DirectBuffer DEFAULT_TOPIC_NAME_BUFFER = wrapString(DEFAULT_TOPIC_NAME);
    int DEFAULT_PARTITION_ID = 0;
    String DEFAULT_LOG_NAME = String.format("%s.%d", DEFAULT_TOPIC_NAME, DEFAULT_PARTITION_ID);

    int MAX_TOPIC_NAME_LENGTH = 128;

    /**
     * @return the topic name of the log stream
     */
    DirectBuffer getTopicName();

    /**
     * @return the partition id of the log stream
     */
    int getPartitionId();

    /**
     * Returns the name of the log stream. Composed from topic name and partition id.
     *
     * @return the log stream name
     */
    String getLogName();

    /**
     * Opens the log stream synchronously. This blocks until the log stream is
     * opened.
     */
    void open();

    /**
     * Opens the log stream asynchronously.
     */
    CompletableFuture<Void> openAsync();

    /**
     * Closes the log stream synchronously. This blocks until the log stream is
     * closed.
     */
    void close();

    /**
     * Closes the log stream asynchronous.
     */
    CompletableFuture<Void> closeAsync();

    /**
     * @return the current position of the log appender, or a negative value if
     *         the log stream is not open
     */
    long getCurrentAppenderPosition();

    /**
     * @return the current commit position, or a negative value if no entry
     *         is committed.
     */
    long getCommitPosition();

    /**
     * Sets the log streams commit position to the given position.
     */
    void setCommitPosition(long commitPosition);

    /**
     * @return the current term in which the log stream is active
     */
    int getTerm();

    /**
     * Sets the log streams term.
     */
    void setTerm(int term);

    /**
     * Register a failure listener.
     */
    void registerFailureListener(LogStreamFailureListener listener);

    /**
     * Remove a registered failure listener.
     */
    void removeFailureListener(LogStreamFailureListener listener);

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
     * Returns the maximum size of a block for which an index is created.
     *
     * @return  the index block size
     */
    int getIndexBlockSize();

    /**
     * Returns the writeBuffer, which is used by the LogStreamController to stream the content into the log storage.
     *
     * @return the writebuffer, which is used by the LogStreamController
     */
    Dispatcher getWriteBuffer();

    /**
     * Returns the log stream controller, which streams the logged events from the write buffer into the log storage.
     *
     * @return the log stream controller
     */
    LogStreamController getLogStreamController();

    /**
     * Returns the log block index controller, which creates periodically the block index for the log storage.
     * @return the log block index controller
     */
    LogBlockIndexController getLogBlockIndexController();

    /**
     * Stops the streaming to the log storage. New events are no longer append to the log storage.
     */
    CompletableFuture<Void> closeLogStreamController();

    /**
     * This method delegates to {@link #openLogStreamController(AgentRunnerService, int)}.
     *
     * The {@link #DEFAULT_MAX_APPEND_BLOCK_SIZE} is used as default max append block size
     * and the old agent runner service is reused.
     *
     * @see {@link #openLogStreamController(AgentRunnerService, int)}
     * @return returns the future for the log stream controller opening
     */
    CompletableFuture<Void> openLogStreamController();

    /**
     * This method delegates to {@link #openLogStreamController(AgentRunnerService, int)}.
     *
     * The {@link #DEFAULT_MAX_APPEND_BLOCK_SIZE} is used as default max append block size.
     *
     * @see {@link #openLogStreamController(AgentRunnerService, int)}
     * @param actorScheduler the agent runner service which is used for the scheduling
     * @return returns the future for the log stream controller opening
     */
    CompletableFuture<Void> openLogStreamController(ActorScheduler actorScheduler);

    /**
     * Starts the log streaming from the write buffer into log storage. The write buffer
     * is internally created .The given agent runner service is used to schedule the writing.
     *
     * The {@link #DEFAULT_MAX_APPEND_BLOCK_SIZE} is used as default max append block size.
     * This method delegates to {@link #openLogStreamController(AgentRunnerService, int)}.
     *
     * @param actorScheduler the agent runner service which is used for the scheduling
     * @param maxAppendBlockSize the maximum block size which should been appended
     * @return returns the future for the log stream controller opening
     */
    CompletableFuture<Void> openLogStreamController(ActorScheduler actorScheduler, int maxAppendBlockSize);

    /**
     * Truncates the log stream from the given position to the end of the stream.
     * Each event which has a higher position as the given will be truncated.
     * This method will truncated the log storage and block index.
     *
     * @param position the position to start the truncation
     * @return the future which is completed if the truncation was successful
     */
    void truncate(long position);

}
