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
package io.zeebe.client.impl.record;

import io.zeebe.client.api.record.Record;
import io.zeebe.client.impl.data.PayloadField;
import io.zeebe.client.impl.data.ZeebeObjectMapperImpl;
import io.zeebe.protocol.clientapi.RecordType;
import io.zeebe.protocol.clientapi.ValueType;

public class UntypedRecordImpl extends RecordImpl {
  private final PayloadField content;

  public UntypedRecordImpl(
      final ZeebeObjectMapperImpl objectMapper,
      final RecordType recordType,
      final ValueType valueType,
      final byte[] rawContent) {
    super(objectMapper, recordType, valueType);

    this.content = new PayloadField(objectMapper);
    this.content.setMsgPack(rawContent);
  }

  public byte[] getAsMsgPack() {
    return content.getMsgPack();
  }

  public <T extends Record> T asRecordType(Class<T> recordClass) {
    return objectMapper.asRecordType(this, recordClass);
  }

  @Override
  public Class<? extends RecordImpl> getEventClass() {
    // not available for an untyped record
    throw new UnsupportedOperationException();
  }

  @Override
  public String toJson() {
    // not available for an untyped record
    throw new UnsupportedOperationException();
  }

  @Override
  public String toString() {
    return "UntypedRecord [metadata=" + getMetadata() + "]";
  }
}
