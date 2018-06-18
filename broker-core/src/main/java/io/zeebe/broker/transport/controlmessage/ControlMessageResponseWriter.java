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

import io.zeebe.protocol.Protocol;
import io.zeebe.protocol.clientapi.ControlMessageResponseEncoder;
import io.zeebe.protocol.clientapi.MessageHeaderEncoder;
import io.zeebe.transport.ServerOutput;
import io.zeebe.transport.ServerResponse;
import io.zeebe.util.buffer.BufferWriter;
import java.util.Objects;
import org.agrona.MutableDirectBuffer;

public class ControlMessageResponseWriter implements BufferWriter {
  protected final MessageHeaderEncoder messageHeaderEncoder = new MessageHeaderEncoder();
  protected final ControlMessageResponseEncoder responseEncoder =
      new ControlMessageResponseEncoder();

  protected BufferWriter dataWriter;

  protected final ServerOutput output;
  protected final ServerResponse response = new ServerResponse();

  public ControlMessageResponseWriter(ServerOutput output) {
    this.output = output;
  }

  public ControlMessageResponseWriter dataWriter(BufferWriter writer) {
    this.dataWriter = writer;
    return this;
  }

  public boolean tryWriteResponse(int requestStreamId, long requestId) {
    Objects.requireNonNull(dataWriter);

    try {
      response.reset().remoteStreamId(requestStreamId).requestId(requestId).writer(this);

      return output.sendResponse(response);
    } finally {
      reset();
    }
  }

  @Override
  public void write(MutableDirectBuffer buffer, int offset) {
    // protocol header
    messageHeaderEncoder
        .wrap(buffer, offset)
        .blockLength(responseEncoder.sbeBlockLength())
        .templateId(responseEncoder.sbeTemplateId())
        .schemaId(responseEncoder.sbeSchemaId())
        .version(responseEncoder.sbeSchemaVersion());

    offset += messageHeaderEncoder.encodedLength();

    // protocol message
    responseEncoder.wrap(buffer, offset);

    final int dataLength = dataWriter.getLength();
    buffer.putShort(offset, (short) dataLength, Protocol.ENDIANNESS);

    offset += ControlMessageResponseEncoder.dataHeaderLength();
    dataWriter.write(buffer, offset);
  }

  @Override
  public int getLength() {
    return MessageHeaderEncoder.ENCODED_LENGTH
        + ControlMessageResponseEncoder.BLOCK_LENGTH
        + ControlMessageResponseEncoder.dataHeaderLength()
        + dataWriter.getLength();
  }

  protected void reset() {
    dataWriter = null;
  }
}
