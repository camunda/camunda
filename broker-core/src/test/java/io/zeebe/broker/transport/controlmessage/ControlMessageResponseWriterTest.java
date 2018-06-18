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
package io.zeebe.broker.transport.controlmessage;

import static io.zeebe.util.StringUtil.getBytes;
import static io.zeebe.util.VarDataUtil.readBytes;
import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.protocol.clientapi.ControlMessageResponseDecoder;
import io.zeebe.protocol.clientapi.MessageHeaderDecoder;
import io.zeebe.util.buffer.DirectBufferWriter;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Before;
import org.junit.Test;

public class ControlMessageResponseWriterTest {
  private static final byte[] DATA = getBytes("state");

  private final MessageHeaderDecoder messageHeaderDecoder = new MessageHeaderDecoder();
  private final ControlMessageResponseDecoder responseDecoder = new ControlMessageResponseDecoder();

  private ControlMessageResponseWriter responseWriter;
  private DirectBufferWriter dataWriter;

  @Before
  public void setup() {
    dataWriter = new DirectBufferWriter();
  }

  @Test
  public void shouldWriteResponse() {
    // given
    responseWriter = new ControlMessageResponseWriter(null);

    dataWriter.wrap(new UnsafeBuffer(DATA), 0, DATA.length);
    responseWriter.dataWriter(dataWriter);

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

    assertThat(responseDecoder.dataLength()).isEqualTo(DATA.length);

    final byte[] data = readBytes(responseDecoder::getData, responseDecoder::dataLength);
    assertThat(data).isEqualTo(DATA);
  }
}
