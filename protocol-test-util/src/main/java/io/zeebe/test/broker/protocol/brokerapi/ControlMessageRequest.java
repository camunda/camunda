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
package io.zeebe.test.broker.protocol.brokerapi;

import io.zeebe.protocol.clientapi.ControlMessageRequestDecoder;
import io.zeebe.protocol.clientapi.ControlMessageType;
import io.zeebe.protocol.clientapi.MessageHeaderDecoder;
import io.zeebe.test.broker.protocol.MsgPackHelper;
import io.zeebe.transport.RemoteAddress;
import io.zeebe.util.buffer.BufferReader;
import java.util.Map;
import org.agrona.DirectBuffer;
import org.agrona.io.DirectBufferInputStream;

public class ControlMessageRequest implements BufferReader {
  protected final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
  protected final ControlMessageRequestDecoder bodyDecoder = new ControlMessageRequestDecoder();

  protected final MsgPackHelper msgPackHelper;
  protected final RemoteAddress source;

  protected Map<String, Object> data;

  public ControlMessageRequest(RemoteAddress source, MsgPackHelper msgPackHelper) {
    this.source = source;
    this.msgPackHelper = msgPackHelper;
  }

  public RemoteAddress getSource() {
    return source;
  }

  public ControlMessageType messageType() {
    return bodyDecoder.messageType();
  }

  public int partitionId() {
    return bodyDecoder.partitionId();
  }

  public Map<String, Object> getData() {
    return data;
  }

  @Override
  public void wrap(DirectBuffer buffer, int offset, int length) {
    headerDecoder.wrap(buffer, offset);

    bodyDecoder.wrap(
        buffer,
        offset + headerDecoder.encodedLength(),
        headerDecoder.blockLength(),
        headerDecoder.version());

    final int dataLength = bodyDecoder.dataLength();
    if (dataLength > 0) {
      data =
          msgPackHelper.readMsgPack(
              new DirectBufferInputStream(
                  buffer,
                  bodyDecoder.limit() + ControlMessageRequestDecoder.dataHeaderLength(),
                  dataLength));
    }
  }
}
