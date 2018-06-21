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
package io.zeebe.raft;

import static io.zeebe.raft.AppendRequestEncoder.previousEventPositionNullValue;
import static io.zeebe.raft.AppendRequestEncoder.previousEventTermNullValue;

import io.zeebe.logstreams.impl.LoggedEventImpl;
import io.zeebe.logstreams.log.BufferedLogStreamReader;
import io.zeebe.logstreams.log.LogStream;
import io.zeebe.logstreams.log.LoggedEvent;
import io.zeebe.protocol.clientapi.ValueType;
import io.zeebe.protocol.impl.RecordMetadata;
import io.zeebe.raft.event.RaftConfigurationEvent;
import io.zeebe.raft.protocol.AppendRequest;
import io.zeebe.raft.protocol.AppendResponse;
import io.zeebe.transport.SocketAddress;
import io.zeebe.util.allocation.AllocatedBuffer;
import io.zeebe.util.allocation.BufferAllocators;
import java.nio.ByteBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.slf4j.Logger;

public class BufferedLogStorageAppender {
  private static final Logger LOG = Loggers.RAFT_LOGGER;
  public static final int INITIAL_CAPACITY = 1024 * 32;

  private final RecordMetadata metadata = new RecordMetadata();
  private final RaftConfigurationEvent configuration = new RaftConfigurationEvent();
  private final AppendResponse appendResponse = new AppendResponse();

  private final Raft raft;
  private final LogStream logStream;
  private final BufferedLogStreamReader reader;

  private final DeferredAck deferredAck = new DeferredAck();

  // event buffer and offset
  private AllocatedBuffer allocatedBuffer;
  private final MutableDirectBuffer buffer = new UnsafeBuffer(0, 0);
  private int offset;

  // last event written to log storage
  private long lastWrittenPosition;
  private int lastWrittenTerm;

  // last event added to buffer
  private long lastBufferedPosition;
  private int lastBufferedTerm;
  private boolean closed;

  public BufferedLogStorageAppender(final Raft raft) {
    this.raft = raft;
    this.logStream = raft.getLogStream();
    this.reader = new BufferedLogStreamReader(logStream, true);

    lastWrittenPosition = previousEventPositionNullValue();
    lastWrittenTerm = previousEventTermNullValue();

    allocateMemory(INITIAL_CAPACITY);
    closed = false;
  }

  public void reset() {
    reader.seekToLastEvent();

    if (reader.hasNext()) {
      final LoggedEvent lastEvent = reader.next();

      lastWrittenPosition = lastEvent.getPosition();
      lastWrittenTerm = lastEvent.getRaftTerm();
    } else {
      lastWrittenPosition = previousEventPositionNullValue();
      lastWrittenTerm = previousEventTermNullValue();
    }

    discardBufferedEvents();
  }

  public boolean isClosed() {
    return closed;
  }

  public void close() {
    closed = true;
    reader.close();
    allocatedBuffer.close();
  }

  public boolean isLastEvent(final long position, final int term) {
    return (lastBufferedPosition == position && lastBufferedTerm == term)
        || (lastWrittenPosition == position && lastWrittenTerm == term);
  }

  public boolean isAfterOrEqualsLastEvent(final long position, final int term) {
    return term > lastBufferedTerm
        || (term == lastBufferedTerm && position >= lastBufferedPosition);
  }

  public void flushAndAck() {
    try {
      if (deferredAck.hasDeferredAck()) {
        if (!flushBufferedEvents()) {
          // unable to flush events, abort and try again with last buffered position
          rejectAppendRequest(lastBufferedPosition, deferredAck.socketAddress);
        } else {
          acceptAppendRequest(
              lastWrittenPosition, deferredAck.commitPosistion, deferredAck.socketAddress);
        }
      }
    } finally {
      deferredAck.reset();
    }
  }

  public void appendEvent(final AppendRequest appendRequest, final LoggedEventImpl event) {
    deferredAck.reset();

    if (event != null) {
      final long previousPosition = appendRequest.getPreviousEventPosition();
      final long previousTerm = appendRequest.getPreviousEventTerm();

      if (previousPosition == lastWrittenPosition && previousTerm == lastWrittenTerm) {
        discardBufferedEvents();
      }

      if (previousPosition == lastBufferedPosition && previousTerm == lastBufferedTerm) {
        final int eventLength = event.getFragmentLength();
        if (remainingCapacity() < eventLength) {
          if (!flushBufferedEvents()) {
            // unable to flush events, abort and try again with last buffered position
            rejectAppendRequest(lastBufferedPosition, appendRequest.getSocketAddress());
            return;
          } else {
            acceptAppendRequest(
                lastWrittenPosition,
                appendRequest.getCommitPosition(),
                appendRequest.getSocketAddress());
          }
        }

        if (remainingCapacity() < eventLength) {
          allocateMemory(eventLength);
        }

        buffer.putBytes(offset, event.getBuffer(), event.getFragmentOffset(), eventLength);
        offset += eventLength;

        event.readMetadata(metadata);

        lastBufferedPosition = event.getPosition();
        lastBufferedTerm = event.getRaftTerm();

        if (metadata.getValueType() == ValueType.RAFT) {
          // update configuration
          event.readValue(configuration);
          raft.replaceMembersOnConfigurationChange(configuration.members());
        }
      } else {
        LOG.warn(
            "Event to append does not follow previous event {}/{} != {}/{}",
            lastBufferedPosition,
            lastBufferedTerm,
            previousPosition,
            previousTerm);
      }

      if (lastWrittenPosition != lastBufferedPosition) {
        deferredAck.deferAck(appendRequest);
      }
    } else {
      acceptAppendRequest(
          lastWrittenPosition, appendRequest.getCommitPosition(), appendRequest.getSocketAddress());
    }
  }

  public void truncateLog(final AppendRequest appendRequest, final LoggedEventImpl event) {
    deferredAck.reset();

    final long currentCommit = logStream.getCommitPosition();

    final long previousEventPosition = appendRequest.getPreviousEventPosition();
    final int previousEventTerm = appendRequest.getPreviousEventTerm();

    if (previousEventPosition >= lastBufferedPosition) {
      // event is either after our last position or the log stream controller
      // is still appendEvent, which does not allow to truncate the log
      rejectAppendRequest(lastBufferedPosition, appendRequest.getSocketAddress());
    } else if (previousEventPosition < currentCommit) {
      rejectAppendRequest(currentCommit, appendRequest.getSocketAddress());
    } else if (reader.seek(previousEventPosition) && reader.hasNext()) {
      final LoggedEvent writtenEvent = reader.next();

      if (writtenEvent.getPosition() == previousEventPosition
          && writtenEvent.getRaftTerm() == previousEventTerm) {
        if (event != null) {
          if (reader.hasNext()) {
            final LoggedEvent nextEvent = reader.next();

            final long nextEventPosition = nextEvent.getPosition();
            final int nextEventTerm = nextEvent.getRaftTerm();

            final long eventPosition = event.getPosition();
            final int eventTerm = event.getRaftTerm();

            if (nextEventPosition == eventPosition && nextEventTerm == eventTerm) {
              // not truncating the log as the event is already appended
              acceptAppendRequest(
                  nextEventPosition,
                  appendRequest.getCommitPosition(),
                  appendRequest.getSocketAddress());
            } else {
              // truncate log and append event
              logStream.truncate(nextEventPosition);

              // reset positions
              lastWrittenPosition = previousEventPosition;
              lastWrittenTerm = previousEventTerm;

              lastBufferedPosition = lastWrittenPosition;
              lastBufferedTerm = lastWrittenTerm;

              appendEvent(appendRequest, event);
            }
          }
        } else {
          acceptAppendRequest(
              writtenEvent.getPosition(),
              appendRequest.getCommitPosition(),
              appendRequest.getSocketAddress());
        }
      } else {
        rejectAppendRequest(writtenEvent.getPosition() - 1, appendRequest.getSocketAddress());
      }
    } else {
      rejectAppendRequest(lastWrittenPosition, appendRequest.getSocketAddress());
    }
  }

  private void allocateMemory(final int capacity) {
    if (allocatedBuffer != null) {
      allocatedBuffer.close();
    }

    allocatedBuffer = BufferAllocators.allocateDirect(capacity);

    buffer.wrap(allocatedBuffer.getRawBuffer());
    offset = 0;

    lastBufferedPosition = lastWrittenPosition;
    lastBufferedTerm = lastWrittenTerm;
  }

  private int remainingCapacity() {
    return buffer.capacity() - offset;
  }

  private void discardBufferedEvents() {
    buffer.setMemory(0, offset, (byte) 0);
    offset = 0;

    lastBufferedPosition = lastWrittenPosition;
    lastBufferedTerm = lastWrittenTerm;
  }

  public boolean flushBufferedEvents() {
    if (offset > 0) {
      final ByteBuffer byteBuffer = buffer.byteBuffer();
      byteBuffer.position(0);
      byteBuffer.limit(offset);

      final long address = logStream.getLogStorage().append(byteBuffer);

      if (address >= 0) {
        lastWrittenPosition = lastBufferedPosition;
        lastWrittenTerm = lastBufferedTerm;

        discardBufferedEvents();
        return true;
      } else {
        byteBuffer.clear();
        return false;
      }
    }

    return true;
  }

  protected void acceptAppendRequest(long position, long commitPosition, SocketAddress remote) {
    final long currentCommitPosition = logStream.getCommitPosition();
    final long nextCommitPosition = Math.min(position, commitPosition);

    if (nextCommitPosition >= 0 && nextCommitPosition > currentCommitPosition) {
      logStream.setCommitPosition(nextCommitPosition);
    }

    appendResponse.reset().setRaft(raft).setPreviousEventPosition(position).setSucceeded(true);

    raft.sendMessage(remote, appendResponse);
  }

  protected void rejectAppendRequest(final long position, SocketAddress remote) {
    appendResponse.reset().setRaft(raft).setPreviousEventPosition(position).setSucceeded(false);

    raft.sendMessage(remote, appendResponse);
  }

  public long getLastPosition() {
    return lastBufferedPosition;
  }

  class DeferredAck {
    long commitPosistion = -1;
    SocketAddress socketAddress;

    void deferAck(AppendRequest request) {
      socketAddress = request.getSocketAddress();
      commitPosistion = request.getCommitPosition();
    }

    void onSend() {
      reset();
    }

    boolean hasDeferredAck() {
      return socketAddress != null;
    }

    void reset() {
      commitPosistion = -1;
      socketAddress = null;
    }
  }
}
