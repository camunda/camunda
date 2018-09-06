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
import io.zeebe.exporter.record.value.TopicRecordValue;
import java.util.List;
import java.util.stream.Stream;

public class TopicRecordStream extends ExporterRecordStream<TopicRecordValue, TopicRecordStream> {

  public TopicRecordStream(final Stream<Record<TopicRecordValue>> wrappedStream) {
    super(wrappedStream);
  }

  @Override
  protected TopicRecordStream supply(final Stream<Record<TopicRecordValue>> wrappedStream) {
    return new TopicRecordStream(wrappedStream);
  }

  public TopicRecordStream withName(final String name) {
    return valueFilter(v -> name.equals(v.getName()));
  }

  public TopicRecordStream withPartitionCount(final int partitionCount) {
    return valueFilter(v -> v.getPartitionCount() == partitionCount);
  }

  public TopicRecordStream withReplicationFactor(final int replicationFactor) {
    return valueFilter(v -> v.getReplicationFactor() == replicationFactor);
  }

  public TopicRecordStream withPartitionIds(final List<Integer> partitionIds) {
    return valueFilter(v -> partitionIds.equals(v.getPartitionIds()));
  }

  public TopicRecordStream withPartitionId(final int partitionId) {
    return valueFilter(v -> v.getPartitionIds().contains(partitionId));
  }
}
