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
 * Represents a batch action performed for the agent instances of a process instance: completing
 * every agent instance still associated with a process instance that has ended. Instead of writing
 * a COMPLETE command for every agent instance in one go, this command batches that work into
 * smaller chunks, following the same approach as {@link ProcessInstanceBatchRecordValue}.
 */
@Value.Immutable
@ImmutableProtocol(builder = ImmutableAgentInstanceBatchRecordValue.Builder.class)
public interface AgentInstanceBatchRecordValue extends RecordValue, ProcessInstanceRelated {

  /**
   * @return the agent instance key to resume iteration from on the next batch cycle, or {@code -1}
   *     if iteration should start from the beginning (there is no previous cycle to resume from)
   */
  long getAgentInstanceKey();
}
