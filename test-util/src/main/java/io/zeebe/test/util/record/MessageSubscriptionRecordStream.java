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

import io.zeebe.exporter.record.Record;
import io.zeebe.exporter.record.value.MessageSubscriptionRecordValue;
import java.util.stream.Stream;

public class MessageSubscriptionRecordStream
    extends ExporterRecordStream<MessageSubscriptionRecordValue, MessageSubscriptionRecordStream> {

  public MessageSubscriptionRecordStream(
      final Stream<Record<MessageSubscriptionRecordValue>> wrappedStream) {
    super(wrappedStream);
  }

  @Override
  protected MessageSubscriptionRecordStream supply(
      final Stream<Record<MessageSubscriptionRecordValue>> wrappedStream) {
    return new MessageSubscriptionRecordStream(wrappedStream);
  }

  public MessageSubscriptionRecordStream withWorkflowInstancePartitionId(
      final int workflowInstancePartitionId) {
    return valueFilter(v -> v.getWorkflowInstancePartitionId() == workflowInstancePartitionId);
  }

  public MessageSubscriptionRecordStream withWorkflowInstanceKey(final long workflowInstanceKey) {
    return valueFilter(v -> v.getWorkflowInstanceKey() == workflowInstanceKey);
  }

  public MessageSubscriptionRecordStream withActivityInstanceKey(final long activityInstanceKey) {
    return valueFilter(v -> v.getActivityInstanceKey() == activityInstanceKey);
  }

  public MessageSubscriptionRecordStream withMessageName(final String messageName) {
    return valueFilter(v -> messageName.equals(v.getMessageName()));
  }

  public MessageSubscriptionRecordStream withCorrelationKey(final String correlationKey) {
    return valueFilter(v -> correlationKey.equals(v.getCorrelationKey()));
  }
}
