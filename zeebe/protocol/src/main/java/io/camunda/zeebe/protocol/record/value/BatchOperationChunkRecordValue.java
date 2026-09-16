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
package io.camunda.zeebe.protocol.record.value;

import io.camunda.zeebe.protocol.record.ImmutableProtocol;
import io.camunda.zeebe.protocol.record.RecordValue;
import java.util.List;
import org.immutables.value.Value;

/**
 * A record value that represents a chunk of items for a batch operation. It contains a list of
 * itemKeys and their related processInstanceKey.<br>
 * <br>
 * A Zeebe record has a limited size (default 4MB). To overcome this limitation for large batch
 * operations, the total set of items is split into multiple chunk records.
 */
@Value.Immutable
@ImmutableProtocol(builder = ImmutableBatchOperationChunkRecordValue.Builder.class)
public interface BatchOperationChunkRecordValue extends BatchOperationRelated, RecordValue {

  /**
   * @return subset of items for the batch operation
   */
  List<BatchOperationItemValue> getItems();

  @Value.Immutable
  @ImmutableProtocol(builder = ImmutableBatchOperationItemValue.Builder.class)
  interface BatchOperationItemValue {
    long getItemKey();

    long getProcessInstanceKey();

    /**
     * Returns the key of the root process instance in the hierarchy. For items in top-level process
     * instances, this is equal to {@link #getProcessInstanceKey()}. For items in child process
     * instances (created via call activities), this is the key of the topmost parent process
     * instance.
     *
     * @return the key of the root process instance, or {@code -1} if not set
     */
    long getRootProcessInstanceKey();
  }
}
