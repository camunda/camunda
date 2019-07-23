/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.transport.impl.sender;

import io.zeebe.dispatcher.impl.log.DataFrameDescriptor;
import io.zeebe.transport.impl.RequestResponseHeaderDescriptor;
import io.zeebe.transport.impl.TransportHeaderDescriptor;
import io.zeebe.util.buffer.BufferWriter;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public class TransportHeaderWriter {
  private static final int REQUEST_HEADER_LENGTH;
  private static final int MESSAGE_HEADER_LENGTH;
  private static final int STREAM_ID_OFFSET;
  private static final int REQUEST_ID_OFFSET;

  static {
    REQUEST_HEADER_LENGTH =
        DataFrameDescriptor.HEADER_LENGTH
            + TransportHeaderDescriptor.HEADER_LENGTH
            + RequestResponseHeaderDescriptor.HEADER_LENGTH;

    MESSAGE_HEADER_LENGTH =
        DataFrameDescriptor.HEADER_LENGTH + TransportHeaderDescriptor.HEADER_LENGTH;

    STREAM_ID_OFFSET = DataFrameDescriptor.STREAM_ID_OFFSET;

    REQUEST_ID_OFFSET =
        DataFrameDescriptor.HEADER_LENGTH
            + TransportHeaderDescriptor.HEADER_LENGTH
            + RequestResponseHeaderDescriptor.REQUEST_ID_OFFSET;
  }

  private final UnsafeBuffer bufferView = new UnsafeBuffer();

  public static int getFramedRequestLength(int messageLength) {
    return DataFrameDescriptor.alignedLength(REQUEST_HEADER_LENGTH + messageLength);
  }

  public static int getFramedMessageLength(int messageLength) {
    return DataFrameDescriptor.alignedLength(MESSAGE_HEADER_LENGTH + messageLength);
  }

  public void wrapRequest(MutableDirectBuffer buffer, BufferWriter messageWriter) {
    bufferView.wrap(buffer);

    final int fragmentLength = REQUEST_HEADER_LENGTH + messageWriter.getLength();

    // put static parts of data fragment header
    bufferView.putInt(DataFrameDescriptor.FRAME_LENGTH_OFFSET, fragmentLength);
    bufferView.putShort(DataFrameDescriptor.TYPE_OFFSET, DataFrameDescriptor.TYPE_MESSAGE);

    // put static parts transport header
    bufferView.putShort(
        DataFrameDescriptor.HEADER_LENGTH + TransportHeaderDescriptor.PROTOCOL_ID_OFFSET,
        TransportHeaderDescriptor.REQUEST_RESPONSE);

    // put in the message
    messageWriter.write(bufferView, REQUEST_HEADER_LENGTH);
  }

  public void wrapMessage(
      MutableDirectBuffer buffer, BufferWriter messageWriter, int remoteStreamId) {
    bufferView.wrap(buffer);

    final int fragmentLength = MESSAGE_HEADER_LENGTH + messageWriter.getLength();

    // put static parts of data fragment header
    bufferView.putInt(DataFrameDescriptor.FRAME_LENGTH_OFFSET, fragmentLength);
    bufferView.putShort(DataFrameDescriptor.TYPE_OFFSET, DataFrameDescriptor.TYPE_MESSAGE);
    bufferView.putInt(DataFrameDescriptor.STREAM_ID_OFFSET, remoteStreamId);

    bufferView.putShort(
        DataFrameDescriptor.HEADER_LENGTH + TransportHeaderDescriptor.PROTOCOL_ID_OFFSET,
        TransportHeaderDescriptor.FULL_DUPLEX_SINGLE_MESSAGE);

    // put in the message
    messageWriter.write(bufferView, MESSAGE_HEADER_LENGTH);
  }

  public TransportHeaderWriter setStreamId(int streamId) {
    bufferView.putInt(STREAM_ID_OFFSET, streamId);
    return this;
  }

  public TransportHeaderWriter setRequestId(long requestId) {
    bufferView.putLong(REQUEST_ID_OFFSET, requestId);
    return this;
  }
}
