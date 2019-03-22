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
import io.zeebe.exporter.api.record.value.MessageStartEventSubscriptionRecordValue;
import java.util.stream.Stream;

public class MessageStartEventSubscriptionRecordStream
    extends ExporterRecordStream<
        MessageStartEventSubscriptionRecordValue, MessageStartEventSubscriptionRecordStream> {

  public MessageStartEventSubscriptionRecordStream(
      Stream<Record<MessageStartEventSubscriptionRecordValue>> wrappedStream) {
    super(wrappedStream);
  }

  @Override
  protected MessageStartEventSubscriptionRecordStream supply(
      Stream<Record<MessageStartEventSubscriptionRecordValue>> wrappedStream) {
    return new MessageStartEventSubscriptionRecordStream((wrappedStream));
  }

  public MessageStartEventSubscriptionRecordStream withWorkfloKey(long workflowKey) {
    return valueFilter(v -> v.getWorkflowKey() == workflowKey);
  }

  public MessageStartEventSubscriptionRecordStream withStartEventId(String startEventId) {
    return valueFilter(v -> startEventId.equals(v.getStartEventId()));
  }

  public MessageStartEventSubscriptionRecordStream withMessageName(String messageName) {
    return valueFilter(v -> messageName.equals(v.getMessageName()));
  }
}
