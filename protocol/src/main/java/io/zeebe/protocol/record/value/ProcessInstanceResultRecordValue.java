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
package io.zeebe.protocol.record.value;

import io.zeebe.protocol.record.RecordValueWithVariables;
import io.zeebe.protocol.record.intent.ProcessInstanceResultIntent;

/**
 * Represents a process instance related command or event.
 *
 * <p>See {@link ProcessInstanceResultIntent} for intents.
 */
public interface ProcessInstanceResultRecordValue
    extends RecordValueWithVariables, ProcessInstanceRelated {
  /** @return the BPMN process id this process instance belongs to. */
  String getBpmnProcessId();

  /** @return the version of the deployed process this instance belongs to. */
  int getVersion();

  /** @return the key of the deployed process this instance belongs to. */
  long getProcessDefinitionKey();

  /** @return the key of the process instance */
  long getProcessInstanceKey();
}
