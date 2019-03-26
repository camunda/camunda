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
package io.zeebe.test.util.record;

import io.zeebe.exporter.api.record.Record;
import io.zeebe.exporter.api.record.value.MessageRecordValue;
import java.util.stream.Stream;

public class MessageRecordStream
    extends ExporterRecordWithVariablesStream<MessageRecordValue, MessageRecordStream> {

  public MessageRecordStream(final Stream<Record<MessageRecordValue>> wrappedStream) {
    super(wrappedStream);
  }

  @Override
  protected MessageRecordStream supply(final Stream<Record<MessageRecordValue>> wrappedStream) {
    return new MessageRecordStream(wrappedStream);
  }

  public MessageRecordStream withName(final String name) {
    return valueFilter(v -> name.equals(v.getName()));
  }

  public MessageRecordStream withCorrelationKey(final String correlationKey) {
    return valueFilter(v -> correlationKey.equals(v.getCorrelationKey()));
  }

  public MessageRecordStream withMessageId(final String messageId) {
    return valueFilter(v -> messageId.equals(v.getMessageId()));
  }

  public MessageRecordStream withTimeToLive(final long timeToLive) {
    return valueFilter(v -> v.getTimeToLive() == timeToLive);
  }
}
