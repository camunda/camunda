/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.transport;

import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.util.buffer.BufferWriter;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.jspecify.annotations.Nullable;

public interface ClientRequest extends BufferWriter {

  /**
   * @return the partition id to which the request should be sent to
   */
  int getPartitionId();

  /**
   * @return the type of this request
   */
  RequestType getRequestType();

  /**
   * @return the partition group (physical tenant) this request targets; defaults to {@code
   *     "default"} for backward compatibility
   */
  default String getPartitionGroup() {
    return Protocol.DEFAULT_PARTITION_GROUP_NAME;
  }

  /**
   * Optional bulk payload carried verbatim at the end of the request frame. When non-null, the
   * transport serializes only the header via {@link #writeHeader(MutableDirectBuffer, int)} and
   * references the returned buffer instead of re-serializing the whole request; the buffer must
   * stay valid and unmodified until the request future completes (retries re-read it). {@link
   * #getLength()} must equal the header length plus this buffer's capacity.
   *
   * @return the bulk payload, or null if the request has none and is serialized via {@link
   *     #write(MutableDirectBuffer, int)}
   */
  default @Nullable DirectBuffer bulkPayload() {
    return null;
  }

  /**
   * Serializes everything except the {@link #bulkPayload()} bytes, i.e. the frame prefix after
   * which the bulk payload follows verbatim. Only invoked when {@link #bulkPayload()} is non-null.
   *
   * @return the number of header bytes written
   */
  default int writeHeader(final MutableDirectBuffer buffer, final int offset) {
    throw new UnsupportedOperationException(
        getClass().getSimpleName() + " does not support split serialization");
  }
}
