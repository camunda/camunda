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

import io.zeebe.protocol.clientapi.ControlMessageResponseEncoder;
import io.zeebe.protocol.clientapi.MessageHeaderEncoder;
import io.zeebe.test.broker.protocol.MsgPackHelper;
import java.util.Map;
import java.util.function.Function;
import org.agrona.MutableDirectBuffer;

public class ControlMessageResponseWriter extends AbstractMessageBuilder<ControlMessageRequest> {

  protected final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
  protected final ControlMessageResponseEncoder bodyEncoder = new ControlMessageResponseEncoder();
  protected final MsgPackHelper msgPackHelper;

  protected Function<ControlMessageRequest, Map<String, Object>> dataFunction;

  protected byte[] data;

  public ControlMessageResponseWriter(MsgPackHelper msgPackHelper) {
    this.msgPackHelper = msgPackHelper;
  }

  public void setDataFunction(Function<ControlMessageRequest, Map<String, Object>> dataFunction) {
    this.dataFunction = dataFunction;
  }

  @Override
  public int getLength() {
    return MessageHeaderEncoder.ENCODED_LENGTH
        + ControlMessageResponseEncoder.BLOCK_LENGTH
        + ControlMessageResponseEncoder.dataHeaderLength()
        + data.length;
  }

  @Override
  public void write(MutableDirectBuffer buffer, int offset) {
    // protocol header
    headerEncoder
        .wrap(buffer, offset)
        .blockLength(bodyEncoder.sbeBlockLength())
        .templateId(bodyEncoder.sbeTemplateId())
        .schemaId(bodyEncoder.sbeSchemaId())
        .version(bodyEncoder.sbeSchemaVersion());

    offset += headerEncoder.encodedLength();

    // protocol message
    bodyEncoder.wrap(buffer, offset).putData(data, 0, data.length);
  }

  @Override
  public void initializeFrom(ControlMessageRequest context) {
    final Map<String, Object> deserializedData = dataFunction.apply(context);
    data = msgPackHelper.encodeAsMsgPack(deserializedData);
  }
}
