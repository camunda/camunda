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
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.immutables.value.Value;

/**
 * Represents a job related event or command.
 *
 * <p>See {@link JobIntent} for intents.
 */
@Value.Immutable
@ImmutableProtocol(builder = ImmutableJobRecordValue.Builder.class)
public interface JobRecordValue
    extends RecordValueWithVariables, ProcessInstanceRelated, TenantOwned {

  /**
   * @return the type of the job
   */
  String getType();

  /**
   * @return user-defined headers associated with this job
   */
  Map<String, String> getCustomHeaders();

  /**
   * @return the assigned worker to complete the job
   */
  String getWorker();

  /**
   * @return remaining retries
   */
  int getRetries();

  /**
   * @return the time of backoff in milliseconds. If backoff is disabled this method returns 0
   *     (default value).
   */
  long getRetryBackoff();

  /**
   * @return the timestamp when this job record should be recurred. This method is used by backoff
   *     to determine the date when the job is needed to be recurred after backoff.
   */
  long getRecurringTime();

  /**
   * @return the unix timestamp until when the job is exclusively assigned to this worker (time unit
   *     is milliseconds since unix epoch). If the deadline is exceeded, it can happen that the job
   *     is handed to another worker and the work is performed twice. If this property is not set it
   *     will return '-1'.
   */
  long getDeadline();

  /**
   * @return the duration used to update the deadline. If this property is not set it will return
   *     '-1'.
   */
  long getTimeout();

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

  /**
   * @return the element id of the corresponding service task
   */
  String getElementId();

  /**
   * @return the element instance key of the corresponding service task
   */
  long getElementInstanceKey();

  /**
   * @return the bpmn process id of the corresponding process definition
   */
  String getBpmnProcessId();

  /**
   * @return the version of the corresponding process definition
   */
  int getProcessDefinitionVersion();

  /**
   * @return the process key of the corresponding process definition
   */
  long getProcessDefinitionKey();

  /**
   * @return the job kind indicating the specific category of the job
   */
  JobKind getJobKind();

  /**
   * @return the listener event type associated with this job. This type is applicable mainly for
   *     jobs of kind {@link JobKind#EXECUTION_LISTENER} or {@link JobKind#TASK_LISTENER}
   */
  JobListenerEventType getJobListenerEventType();

  Set<String> getChangedAttributes();

  JobResultValue getResult();

  /**
   * @return the tags that were set for this job.
   */
  Set<String> getTags();

  /**
   * @return true if the job is part of a user task migration
   */
  boolean isJobToUserTaskMigration();

  @Value.Immutable
  @ImmutableProtocol(builder = ImmutableJobResultValue.Builder.class)
  interface JobResultValue {

    /**
     * @return the type of the job result, e.g. "userTask" for user task jobs or "adHocSubprocess"
     *     for ad-hoc subprocess jobs. Depending on the type different properties are set.
     */
    JobResultType getType();

    /**
     * @return true if the operation was rejected by Task Listener
     */
    boolean isDenied();

    /**
     * @return a reason provided by Task Listener for denying the work
     */
    String getDeniedReason();

    /**
     * May only contain the attribute names of {@link JobResultCorrectionsValue} as entries. Those
     * attributes that are contained in this list are the ones that were corrected by the worker.
     * Others are considered not corrected.
     *
     * @return the list of attributes that the worker corrected when handling the job
     * @apiNote only attributes in this list should be considered when accessing {@link
     *     JobResultCorrectionsValue} as unset fields receive default values
     */
    List<String> getCorrectedAttributes();

    /**
     * @return the corrections the worker made as a result of completing the job
     * @apiNote contains defaults for fields that were not set, use {@link
     *     JobResultValue#getCorrectedAttributes()} to determine which fields are set
     */
    JobResultCorrectionsValue getCorrections();

    /**
     * @return a list of elements that need to be activated as a result of completing the job, as
     *     well as variables that need to be set on the scope of each of these elements.
     */
    List<JobResultActivateElementValue> getActivateElements();

    /**
     * @return whether the completion condition is fulfilled. This is used to determine if the
     *     ad-hoc sub-process should be completed.
     */
    boolean isCompletionConditionFulfilled();

    /**
     * @return whether the remaining instances of the ad-hoc sub-process should be canceled.
     */
    boolean isCancelRemainingInstances();
  }

  /**
   * Represents the corrections that can be part of a {@link JobResultValue}.
   *
   * @apiNote the fields are all optional, but receive a default value if not set. Use {@link
   *     JobResultValue#getCorrectedAttributes()} to determine which fields are set.
   */
  @Value.Immutable
  @ImmutableProtocol(builder = ImmutableJobResultCorrectionsValue.Builder.class)
  interface JobResultCorrectionsValue {

    /**
     * @return the corrected assignee value
     */
    String getAssignee();

    /**
     * @return the corrected due date value
     */
    String getDueDate();

    /**
     * @return the corrected follow-up date value
     */
    String getFollowUpDate();

    /**
     * @return the corrected candidate users
     */
    List<String> getCandidateGroupsList();

    /**
     * @return the corrected candidate groups
     */
    List<String> getCandidateUsersList();

    /**
     * @return the corrected priority
     */
    int getPriority();
  }

  /** Represents the activate elements that can be opart of a {@link JobResultValue} */
  @Value.Immutable
  @ImmutableProtocol(builder = ImmutableJobResultActivateElementValue.Builder.class)
  interface JobResultActivateElementValue {

    /**
     * @return the id of the element that needs to be activated
     */
    String getElementId();

    /**
     * @return the variables that need to be set on the element when it is activated
     */
    Map<String, Object> getVariables();
  }
}
