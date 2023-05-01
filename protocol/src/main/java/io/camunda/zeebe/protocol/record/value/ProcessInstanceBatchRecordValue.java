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
import org.immutables.value.Value;

/**
 * Represents a batch action that's performed for a process instance. An example for this is
 * termination of child instances. Instead of writing a TERMINATE command for all children, this
 * command is used to batch these commands in smaller chunks.
 */
@Value.Immutable
@ImmutableProtocol(builder = ImmutableProcessInstanceBatchRecordValue.Builder.class)
public interface ProcessInstanceBatchRecordValue extends RecordValue, ProcessInstanceRelated {

  /**
   * @return the element instance for which a batch action is being performed. This should be a
   *     container element (e.g. a subprocess).
   */
  long getBatchElementInstanceKey();

  /**
   * @return an index used to keep track of where we are in our batch process and where to start the
   *     next batch.
   */
  long getIndex();
}
