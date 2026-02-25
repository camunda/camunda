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
import io.camunda.zeebe.protocol.record.RecordValueWithVariables;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceResultIntent;
import java.util.Set;
import org.immutables.value.Value;

/**
 * Represents a process instance related command or event.
 *
 * <p>See {@link ProcessInstanceResultIntent} for intents.
 */
@Value.Immutable
@ImmutableProtocol(builder = ImmutableProcessInstanceResultRecordValue.Builder.class)
public interface ProcessInstanceResultRecordValue
    extends RecordValueWithVariables, ProcessInstanceRelated, TenantOwned {
  /**
   * @return the BPMN process id this process instance belongs to.
   */
  String getBpmnProcessId();

  /**
   * @return the version of the deployed process this instance belongs to.
   */
  int getVersion();

  /**
   * @return the key of the process instance
   */
  @Override
  long getProcessInstanceKey();

  /**
   * @return the key of the deployed process this instance belongs to.
   */
  @Override
  long getProcessDefinitionKey();

  /**
   * @return the set of tags of the process instance
   */
  Set<String> getTags();

  /**
   * Returns the business id for the process instance. The business id is an immutable, user-defined
   * string identifier that identifies a process instance within the scope of a process definition.
   *
   * @return the business id, or an empty string if not set
   * @since 8.9
   */
  String getBusinessId();
}
