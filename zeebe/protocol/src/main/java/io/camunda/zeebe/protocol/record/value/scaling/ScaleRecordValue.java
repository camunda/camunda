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
package io.camunda.zeebe.protocol.record.value.scaling;

import io.camunda.zeebe.protocol.record.ImmutableProtocol;
import io.camunda.zeebe.protocol.record.RecordValue;
import java.util.Collection;
import org.immutables.value.Value;

@Value.Immutable
@ImmutableProtocol(builder = ImmutableScaleRecordValue.Builder.class)
public interface ScaleRecordValue extends RecordValue {
  int getDesiredPartitionCount();

  Collection<Integer> getRedistributedPartitions();

  Collection<Integer> getRelocatedPartitions();

  /**
   * @return The number of partitions taking part in the message correlation
   */
  int getMessageCorrelationPartitions();

  long getScalingPosition();
}
