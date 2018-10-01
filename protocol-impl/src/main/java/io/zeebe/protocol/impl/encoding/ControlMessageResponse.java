/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.zeebe.protocol.impl.encoding;

import io.zeebe.protocol.clientapi.ControlMessageResponseDecoder;
import io.zeebe.protocol.clientapi.ControlMessageResponseEncoder;
import io.zeebe.protocol.clientapi.MessageHeaderDecoder;
import io.zeebe.protocol.clientapi.MessageHeaderEncoder;
import io.zeebe.util.buffer.BufferReader;
import io.zeebe.util.buffer.BufferWriter;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public class ControlMessageResponse implements BufferReader, BufferWriter {

  protected final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
  protected final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();

  protected final ControlMessageResponseEncoder bodyEncoder = new ControlMessageResponseEncoder();
  protected final ControlMessageResponseDecoder bodyDecoder = new ControlMessageResponseDecoder();

  protected final DirectBuffer data = new UnsafeBuffer(0, 0);

  public ControlMessageResponse() {
    reset();
  }

  public ControlMessageResponse reset() {
    data.wrap(0, 0);

    return this;
  }

  public DirectBuffer getData() {
    return data;
  }

  public ControlMessageResponse setData(DirectBuffer buffer, int offset, int length) {
    this.data.wrap(buffer, offset, length);
    return this;
  }

  @Override
  public void wrap(DirectBuffer buffer, int offset, int length) {
    reset();

    final int frameEnd = offset + length;

    headerDecoder.wrap(buffer, offset);

    offset += headerDecoder.encodedLength();

    bodyDecoder.wrap(buffer, offset, headerDecoder.blockLength(), headerDecoder.version());

    offset += bodyDecoder.sbeBlockLength();

    final int dataLength = bodyDecoder.dataLength();
    offset += ControlMessageResponseDecoder.dataHeaderLength();

    data.wrap(buffer, offset, dataLength);
    offset += dataLength;

    bodyDecoder.limit(offset);

    assert bodyDecoder.limit() == frameEnd
        : "Decoder read only to position "
            + bodyDecoder.limit()
            + " but expected "
            + frameEnd
            + " as final position";
  }

  @Override
  public int getLength() {
    return MessageHeaderEncoder.ENCODED_LENGTH
        + ControlMessageResponseEncoder.BLOCK_LENGTH
        + ControlMessageResponseEncoder.dataHeaderLength()
        + data.capacity();
  }

  @Override
  public void write(MutableDirectBuffer buffer, int offset) {
    headerEncoder
        .wrap(buffer, offset)
        .blockLength(bodyEncoder.sbeBlockLength())
        .templateId(bodyEncoder.sbeTemplateId())
        .schemaId(bodyEncoder.sbeSchemaId())
        .version(bodyEncoder.sbeSchemaVersion());

    offset += headerEncoder.encodedLength();

    bodyEncoder.wrap(buffer, offset).putData(data, 0, data.capacity());
  }
}
