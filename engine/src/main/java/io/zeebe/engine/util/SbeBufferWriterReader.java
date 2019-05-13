/*
 * Zeebe Workflow Engine
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
package io.zeebe.engine.util;

import io.zeebe.engine.processor.workflow.message.command.MessageHeaderDecoder;
import io.zeebe.engine.processor.workflow.message.command.MessageHeaderEncoder;
import io.zeebe.util.buffer.BufferReader;
import io.zeebe.util.buffer.BufferWriter;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.agrona.sbe.MessageDecoderFlyweight;
import org.agrona.sbe.MessageEncoderFlyweight;

public abstract class SbeBufferWriterReader<
        E extends MessageEncoderFlyweight, D extends MessageDecoderFlyweight>
    implements BufferWriter, BufferReader {
  protected final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
  protected final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();

  protected abstract E getBodyEncoder();

  protected abstract D getBodyDecoder();

  public void reset() {}

  public void wrap(final DirectBuffer buffer) {
    wrap(buffer, 0, buffer.capacity());
  }

  @Override
  public int getLength() {
    return headerDecoder.encodedLength() + getBodyEncoder().sbeBlockLength();
  }

  @Override
  public void wrap(final DirectBuffer buffer, final int offset, final int length) {
    reset();

    headerDecoder.wrap(buffer, offset);
    getBodyDecoder()
        .wrap(
            buffer,
            offset + headerDecoder.encodedLength(),
            headerDecoder.blockLength(),
            headerDecoder.version());
  }

  @Override
  public void write(final MutableDirectBuffer buffer, final int offset) {
    headerEncoder
        .wrap(buffer, offset)
        .blockLength(getBodyEncoder().sbeBlockLength())
        .templateId(getBodyEncoder().sbeTemplateId())
        .schemaId(getBodyEncoder().sbeSchemaId())
        .version(getBodyEncoder().sbeSchemaVersion());

    getBodyEncoder().wrap(buffer, offset + headerEncoder.encodedLength());
  }

  public boolean tryWrap(DirectBuffer buffer) {
    return tryWrap(buffer, 0, buffer.capacity());
  }

  public boolean tryWrap(DirectBuffer buffer, int offset, int length) {
    headerDecoder.wrap(buffer, offset);

    return headerDecoder.schemaId() == getBodyDecoder().sbeSchemaId()
        && headerDecoder.templateId() == getBodyDecoder().sbeTemplateId();
  }

  public byte[] toBytes() {
    final byte bytes[] = new byte[getLength()];
    final MutableDirectBuffer buffer = new UnsafeBuffer(bytes);
    write(buffer, 0);
    return bytes;
  }
}
