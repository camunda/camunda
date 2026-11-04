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

/** Suspension or resumption of one process definition version, identified by its key. */
@Value.Immutable
@ImmutableProtocol(builder = ImmutableProcessDefinitionSuspensionRecordValue.Builder.class)
public interface ProcessDefinitionSuspensionRecordValue extends RecordValue, TenantOwned {

  /**
   * @return the key of the process definition version to suspend or resume
   */
  long getProcessDefinitionKey();

  /**
   * @return the BPMN process id of the version; set by the engine
   */
  String getBpmnProcessId();

  /**
   * @return the version number; set by the engine
   */
  int getVersion();
}
