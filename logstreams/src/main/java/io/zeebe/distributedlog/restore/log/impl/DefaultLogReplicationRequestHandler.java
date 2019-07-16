/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.distributedlog.restore.log.impl;

import io.zeebe.distributedlog.restore.RestoreServer.LogReplicationRequestHandler;
import io.zeebe.distributedlog.restore.log.LogReplicationRequest;
import io.zeebe.distributedlog.restore.log.LogReplicationResponse;
import io.zeebe.logstreams.log.BufferedLogStreamReader;
import io.zeebe.logstreams.log.LogStream;
import io.zeebe.logstreams.log.LogStreamReader;
import io.zeebe.logstreams.log.LoggedEvent;
import java.nio.ByteBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.slf4j.Logger;

public class DefaultLogReplicationRequestHandler implements LogReplicationRequestHandler {
  private static final int DEFAULT_READ_BUFFER_SIZE = 64 * 1024 * 1024;

  private final LogStreamReader reader;
  private final MutableDirectBuffer readerBuffer;

  public DefaultLogReplicationRequestHandler(LogStream logStream) {
    this(logStream, DEFAULT_READ_BUFFER_SIZE);
  }

  public DefaultLogReplicationRequestHandler(LogStream logStream, int bufferSize) {
    this.reader = new BufferedLogStreamReader(logStream);
    this.readerBuffer = new UnsafeBuffer(ByteBuffer.allocate(bufferSize));
  }

  @Override
  public final LogReplicationResponse onReplicationRequest(
      LogReplicationRequest request, Logger logger) {
    final DefaultLogReplicationResponse response = new DefaultLogReplicationResponse();

    logger.debug("Received log replication request {}", request);
    if (seekToRequestedPosition(request.getFromPosition(), !request.includeFromPosition())) {
      long lastReadPosition = reader.getPosition();
      int offset = 0;

      while (reader.hasNext()) {
        final LoggedEvent event = reader.next();

        if ((offset + event.getLength()) > readerBuffer.capacity()) {
          break;
        }

        if (event.getPosition() <= request.getToPosition()) {
          event.write(readerBuffer, offset);
          offset += event.getLength();
          lastReadPosition = event.getPosition();
        }
      }

      response.setToPosition(lastReadPosition);
      response.setMoreAvailable(lastReadPosition < request.getToPosition() && reader.hasNext());
      response.setSerializedEvents(readerBuffer, 0, offset);
    } else {
      logger.debug(
          "Ignoring log replication request {} - {}, no events found with position {}",
          request.getFromPosition(),
          request.getToPosition(),
          request.getFromPosition());
    }

    logger.debug("Responding log replication request with {}", response);
    return response;
  }

  private boolean seekToRequestedPosition(long position, boolean skipEventAtPosition) {
    if (position == -1) {
      reader.seekToFirstEvent();
      return true;
    }

    if (reader.seek(position) && reader.hasNext()) {
      if (skipEventAtPosition) {
        reader.next();
      }
      return true;
    }

    return false;
  }
}
