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

@Value.Immutable
@ImmutableProtocol(builder = ImmutableAgentHistoryRecordValue.Builder.class)
public interface AgentHistoryRecordValue extends RecordValue {

  /** Returns the key of the agent instance that produced this history entry. */
  long getAgentInstanceKey();

  /** Returns the key of the element instance associated with this entry. */
  long getElementInstanceKey();

  /** Returns the key of the job that triggered the agent for this entry. */
  long getJobKey();

  /** Returns the attempt number for the job, starting at 1. */
  int getAttemptNumber();

  /** Returns the iteration counter (conversation round with the LLM). */
  int getIteration();

  /** Returns the role of the message author (e.g. USER, ASSISTANT, TOOL_RESULT). */
  AgentHistoryRole getRole();

  /** Returns the commit status of this history entry (e.g. PENDING, COMMITTED, DISCARDED). */
  AgentHistoryCommitStatus getCommitStatus();

  /** Returns the epoch-millis timestamp at which this entry was produced. */
  long getProducedAt();

  /** Returns optional free-form metadata attached to this entry. */
  String getMetadata();
}
