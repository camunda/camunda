/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.protocol.request;

import io.camunda.eventbridge.protocol.ErrorCode;
import io.camunda.eventbridge.protocol.MessageHeaderDecoder;
import io.camunda.eventbridge.protocol.MessageHeaderEncoder;
import io.camunda.eventbridge.protocol.PollResponseDecoder;
import io.camunda.eventbridge.protocol.PollResponseEncoder;
import io.camunda.zeebe.util.buffer.BufferReader;
import io.camunda.zeebe.util.buffer.BufferWriter;
import java.util.ArrayList;
import java.util.List;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

public class PollResponse implements BufferReader, BufferWriter {

  private final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
  private final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();

  private final PollResponseEncoder bodyEncoder = new PollResponseEncoder();
  private final PollResponseDecoder bodyDecoder = new PollResponseDecoder();

  private ErrorCode errorCode;
  private long nextPosition;
  private long epoch;
  private List<PollEvent> events;
  private String errorMessage;

  @Override
  public void wrap(final DirectBuffer buffer, final int offset, final int length) {
    bodyDecoder.wrapAndApplyHeader(buffer, 0, headerDecoder);

    errorCode = bodyDecoder.errorCode();
    nextPosition = bodyDecoder.nextPosition();
    epoch = bodyDecoder.epoch();

    events = new ArrayList<>();
    for (final var evt : bodyDecoder.events()) {
      final int len = evt.payloadLength();
      final byte[] payload = new byte[len];
      evt.getPayload(payload, 0, len);
      events.add(new PollEvent(evt.position(), payload));
    }

    errorMessage = bodyDecoder.errorMessage();
  }

  @Override
  public int getLength() {
    var length =
        headerEncoder.encodedLength()
            + bodyEncoder.sbeBlockLength()
            + PollResponseEncoder.errorMessageHeaderLength()
            + (errorMessage == null ? 0 : errorMessage.length())
            + PollResponseEncoder.EventsEncoder.sbeBlockLength();

    if (events != null && !events.isEmpty()) {
      for (final var event : events) {
        length += Integer.BYTES + event.payload().length;
      }
    }

    return length;
  }

  @Override
  public int write(final MutableDirectBuffer buffer, final int offset) {
    bodyEncoder
        .wrapAndApplyHeader(buffer, offset, headerEncoder)
        .errorCode(errorCode)
        .nextPosition(nextPosition)
        .epoch(epoch)
        .errorMessage(errorMessage);

    final var data = bodyEncoder.eventsCount(events.size());
    for (final var event : events) {
      data.next().position(event.position()).putPayload(event.payload, 0, event.payload.length);
    }

    return headerDecoder.encodedLength() + bodyEncoder.encodedLength();
  }

  public record PollEvent(long position, byte[] payload) {}
}
