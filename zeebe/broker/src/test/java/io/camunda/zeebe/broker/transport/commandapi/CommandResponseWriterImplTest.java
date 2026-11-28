/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.transport.commandapi;

import static io.camunda.zeebe.util.StringUtil.getBytes;
import static io.camunda.zeebe.util.VarDataUtil.readBytes;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.protocol.record.ExecuteCommandResponseDecoder;
import io.camunda.zeebe.protocol.record.MessageHeaderDecoder;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.util.buffer.DirectBufferWriter;
import java.util.ArrayList;
import java.util.List;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

final class CommandResponseWriterImplTest {
  private static final int PARTITION_ID = 1;
  private static final long KEY = 2L;
  private static final byte[] EVENT = getBytes("state");

  private final MessageHeaderDecoder messageHeaderDecoder = new MessageHeaderDecoder();
  private final ExecuteCommandResponseDecoder responseDecoder = new ExecuteCommandResponseDecoder();

  @Test
  void shouldWriteResponse() {
    // given
    final var responseWriter = new CommandResponseWriterImpl(null);

    final var eventWriter = new DirectBufferWriter(new UnsafeBuffer(EVENT), 0, EVENT.length);

    responseWriter
        .partitionId(PARTITION_ID)
        .key(KEY)
        .recordType(RecordType.EVENT)
        .valueType(ValueType.JOB)
        .intent(JobIntent.CREATED)
        .valueWriter(eventWriter);

    final UnsafeBuffer buf = new UnsafeBuffer(new byte[responseWriter.getLength()]);

    // when
    responseWriter.write(buf, 0);

    // then
    int offset = 0;

    messageHeaderDecoder.wrap(buf, offset);
    assertThat(messageHeaderDecoder.blockLength()).isEqualTo(responseDecoder.sbeBlockLength());
    assertThat(messageHeaderDecoder.templateId()).isEqualTo(responseDecoder.sbeTemplateId());
    assertThat(messageHeaderDecoder.schemaId()).isEqualTo(responseDecoder.sbeSchemaId());
    assertThat(messageHeaderDecoder.version()).isEqualTo(responseDecoder.sbeSchemaVersion());

    offset += messageHeaderDecoder.encodedLength();

    responseDecoder.wrap(
        buf, offset, responseDecoder.sbeBlockLength(), responseDecoder.sbeSchemaVersion());
    assertThat(responseDecoder.partitionId()).isEqualTo(PARTITION_ID);
    assertThat(responseDecoder.key()).isEqualTo(KEY);
    assertThat(responseDecoder.recordType()).isEqualTo(RecordType.EVENT);
    assertThat(responseDecoder.valueType()).isEqualTo(ValueType.JOB);
    assertThat(responseDecoder.intent()).isEqualTo(JobIntent.CREATED.value());

    assertThat(responseDecoder.valueLength()).isEqualTo(EVENT.length);

    final byte[] event = readBytes(responseDecoder::getValue, responseDecoder::valueLength);
    assertThat(event).isEqualTo(EVENT);
  }

  @Test
  void shouldAddressResponseToTheMemberThatReceivedTheRequest() {
    // given
    final List<String> receiverIds = new ArrayList<>();
    final var responseWriter =
        new CommandResponseWriterImpl(response -> receiverIds.add(response.getReceiverId()));
    final var eventWriter = new DirectBufferWriter(new UnsafeBuffer(EVENT), 0, EVENT.length);

    // when
    responseWriter.valueWriter(eventWriter).receiverId("zone-b_0").tryWriteResponse(1, 1L);
    responseWriter.valueWriter(eventWriter).tryWriteResponse(1, 2L);

    // then
    assertThat(receiverIds).containsExactly("zone-b_0", "");
  }
}
