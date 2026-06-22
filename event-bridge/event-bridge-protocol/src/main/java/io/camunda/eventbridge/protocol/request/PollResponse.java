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
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

public class PollResponse implements BufferReader, BufferWriter {

  /** groupSizeEncoding dimension header: blockLength(uint16) + numInGroup(uint16). */
  private static final int GROUP_HEADER_LENGTH = 4;

  private final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
  private final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();

  private final PollResponseEncoder bodyEncoder = new PollResponseEncoder();
  private final PollResponseDecoder bodyDecoder = new PollResponseDecoder();

  private ErrorCode errorCode = ErrorCode.NONE;
  private long nextPosition;
  private long epoch;
  private List<PollEvent> events = new ArrayList<>();
  private String errorMessage = "";

  public ErrorCode getErrorCode() {
    return errorCode;
  }

  public long getNextPosition() {
    return nextPosition;
  }

  public long getEpoch() {
    return epoch;
  }

  public List<PollEvent> getEvents() {
    return events;
  }

  public String getErrorMessage() {
    return errorMessage;
  }

  public PollResponse errorCode(final ErrorCode errorCode) {
    this.errorCode = errorCode;
    return this;
  }

  public PollResponse nextPosition(final long nextPosition) {
    this.nextPosition = nextPosition;
    return this;
  }

  public PollResponse epoch(final long epoch) {
    this.epoch = epoch;
    return this;
  }

  public PollResponse events(final List<PollEvent> events) {
    this.events = events;
    return this;
  }

  public PollResponse errorMessage(final String errorMessage) {
    this.errorMessage = errorMessage;
    return this;
  }

  @Override
  public void wrap(final DirectBuffer buffer, final int offset, final int length) {
    bodyDecoder.wrapAndApplyHeader(buffer, offset, headerDecoder);

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
            + GROUP_HEADER_LENGTH
            + PollResponseEncoder.errorMessageHeaderLength()
            + (errorMessage == null ? 0 : errorMessage.getBytes(StandardCharsets.UTF_8).length);

    for (final var event : events) {
      length +=
          PollResponseEncoder.EventsEncoder.sbeBlockLength()
              + PollResponseEncoder.EventsEncoder.payloadHeaderLength()
              + event.payload().length;
    }

    return length;
  }

  @Override
  public int write(final MutableDirectBuffer buffer, final int offset) {
    // SBE requires fields in schema order: block fields, then the events group, then the
    // var-length errorMessage. Encoding errorMessage before the group corrupts the message.
    bodyEncoder
        .wrapAndApplyHeader(buffer, offset, headerEncoder)
        .errorCode(errorCode)
        .nextPosition(nextPosition)
        .epoch(epoch);

    final var data = bodyEncoder.eventsCount(events.size());
    for (final var event : events) {
      data.next().position(event.position()).putPayload(event.payload, 0, event.payload.length);
    }

    bodyEncoder.errorMessage(errorMessage == null ? "" : errorMessage);

    return headerEncoder.encodedLength() + bodyEncoder.encodedLength();
  }

  public record PollEvent(long position, byte[] payload) {}
}
