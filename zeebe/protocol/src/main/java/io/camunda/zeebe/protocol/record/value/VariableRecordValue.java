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
import io.camunda.zeebe.protocol.record.intent.VariableIntent;
import org.immutables.value.Value;

/**
 * Represents a variable related event.
 *
 * <p>See {@link VariableIntent} for intents.
 */
@Value.Immutable
@ImmutableProtocol(builder = ImmutableVariableRecordValue.Builder.class)
public interface VariableRecordValue
    extends RecordValue, ProcessInstanceRelated, AuditLogProcessInstanceRelated, TenantOwned {

  /**
   * @return the name of the variable.
   */
  String getName();

  /**
   * @return the value of the variable as JSON string.
   */
  String getValue();

  /**
   * @return the key of the scope the variable belongs to.
   */
  long getScopeKey();

  /**
   * @return the key of the process instance the variable belongs to
   */
  @Override
  long getProcessInstanceKey();

  /**
   * @return the key of the process the variable belongs to
   */
  @Override
  long getProcessDefinitionKey();

  /**
   * @return the element instance key of the scope the variable belongs to.
   */
  @Override
  default long getElementInstanceKey() {
    return getScopeKey();
  }

  /**
   * @return the BPMN process id this process instance belongs to.
   */
  @Override
  String getBpmnProcessId();

  /**
   * Returns the key of the root process instance in the hierarchy. For variables in top-level
   * process instances, this is equal to {@link #getProcessInstanceKey()}. For variables in child
   * process instances (created via call activities), this is the key of the topmost parent process
   * instance.
   *
   * @return the key of the root process instance, or {@code -1} if not set
   */
  long getRootProcessInstanceKey();
}
