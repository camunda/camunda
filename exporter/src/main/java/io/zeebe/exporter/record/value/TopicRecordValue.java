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
package io.zeebe.exporter.record.value;

import io.zeebe.exporter.record.RecordValue;
import java.util.List;

/**
 * Represents a topic command or event, e.g. topic creation.
 *
 * <p>See {@link io.zeebe.protocol.intent.TopicIntent} for intents.
 */
public interface TopicRecordValue extends RecordValue {
  /** @return the topic name */
  String getName();

  /** @return the number of partition for this topic */
  int getPartitionCount();

  /** @return the replication factor of this topic */
  int getReplicationFactor();

  /** @return the list of partition IDs associated to this topic */
  List<Integer> getPartitionIds();
}
