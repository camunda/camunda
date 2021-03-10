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
import io.zeebe.protocol.record.intent.JobIntent;
import java.util.Map;

/**
 * Represents a job related event or command.
 *
 * <p>See {@link JobIntent} for intents.
 */
public interface JobRecordValue extends RecordValueWithVariables, ProcessInstanceRelated {

  /** @return the type of the job */
  String getType();

  /** @return user-defined headers associated with this job */
  Map<String, String> getCustomHeaders();

  /** @return the assigned worker to complete the job */
  String getWorker();

  /** @return remaining retries */
  int getRetries();

  /**
   * @return the unix timestamp until when the job is exclusively assigned to this worker (time unit
   *     is milliseconds since unix epoch). If the deadline is exceeded, it can happen that the job
   *     is handed to another worker and the work is performed twice. If this property is not set it
   *     will return '-1'.
   */
  long getDeadline();

  /**
   * @return the message that contains additional context of the failure/error. It is set by the job
   *     worker then the processing fails because of a technical failure or a non-technical error.
   */
  String getErrorMessage();

  /**
   * @return the error code to identify the business error. It is set by the job worker then the
   *     processing fails because of a non-technical error that should be handled by the process.
   */
  String getErrorCode();

  /** @return the element id of the corresponding service task */
  String getElementId();

  /** @return the element instance key of the corresponding service task */
  long getElementInstanceKey();

  /** @return the bpmn process id of the corresponding process definition */
  String getBpmnProcessId();

  /** @return the version of the corresponding process definition */
  int getProcessDefinitionVersion();

  /** @return the process key of the corresponding process definition */
  long getProcessDefinitionKey();
}
