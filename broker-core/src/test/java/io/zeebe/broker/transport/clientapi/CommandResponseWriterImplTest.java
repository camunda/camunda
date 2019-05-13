/*
 * Zeebe Broker Core
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.zeebe.broker.transport.clientapi;

import static io.zeebe.util.StringUtil.getBytes;
import static io.zeebe.util.VarDataUtil.readBytes;
import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.protocol.clientapi.ExecuteCommandResponseDecoder;
import io.zeebe.protocol.clientapi.MessageHeaderDecoder;
import io.zeebe.protocol.clientapi.RecordType;
import io.zeebe.protocol.clientapi.ValueType;
import io.zeebe.protocol.intent.JobIntent;
import io.zeebe.util.buffer.DirectBufferWriter;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Before;
import org.junit.Test;

public class CommandResponseWriterImplTest {
  private static final int PARTITION_ID = 1;
  private static final long KEY = 2L;
  private static final byte[] EVENT = getBytes("state");

  private final MessageHeaderDecoder messageHeaderDecoder = new MessageHeaderDecoder();
  private final ExecuteCommandResponseDecoder responseDecoder = new ExecuteCommandResponseDecoder();

  private CommandResponseWriterImpl responseWriter;
  private DirectBufferWriter eventWriter;

  @Before
  public void setup() {
    eventWriter = new DirectBufferWriter();
  }

  @Test
  public void shouldWriteResponse() {
    // given
    responseWriter = new CommandResponseWriterImpl(null);

    eventWriter.wrap(new UnsafeBuffer(EVENT), 0, EVENT.length);

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
}
