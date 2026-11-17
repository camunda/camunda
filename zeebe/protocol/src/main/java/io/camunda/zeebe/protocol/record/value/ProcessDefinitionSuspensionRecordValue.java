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

/** Suspends or resumes a process definition, or all of its versions. */
@Value.Immutable
@ImmutableProtocol(builder = ImmutableProcessDefinitionSuspensionRecordValue.Builder.class)
public interface ProcessDefinitionSuspensionRecordValue extends RecordValue, TenantOwned {

  /** The key of the process definition, or -1 if it is addressed by its BPMN process ID. */
  long getProcessDefinitionKey();

  /** The BPMN process ID of the process definition, or empty if it is addressed by its key. */
  String getBpmnProcessId();

  /** The version of the process definition, or -1 for all versions. */
  int getVersion();

  /** Whether the running process instances of the process definition are suspended too. */
  boolean isIncludeProcessInstances();

  /** The process definition versions the command resolved to, with the outcome for each. */
  List<ProcessDefinitionSuspensionResolvedVersionValue> getResolvedVersions();

  /** The batch operation that tracks the suspension, or -1 if there is none. */
  long getBatchOperationKey();
}
