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
package io.camunda.process.test.api;

import io.camunda.client.CamundaClient;
import io.camunda.client.CamundaClientBuilder;
import io.camunda.client.api.command.CompleteAdHocSubProcessResultStep1;
import io.camunda.client.api.command.CompleteUserTaskJobResultStep1;
import io.camunda.process.test.api.assertions.ElementSelector;
import io.camunda.process.test.api.assertions.IncidentSelector;
import io.camunda.process.test.api.assertions.JobSelector;
import io.camunda.process.test.api.assertions.ProcessInstanceSelector;
import io.camunda.process.test.api.assertions.UserTaskSelector;
import io.camunda.process.test.api.mock.JobWorkerMockBuilder;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.ZeebeClientBuilder;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.function.Consumer;

/** The injected context for a process test. */
public interface CamundaProcessTestContext {

  /**
   * Creates a new preconfigured Camunda client that is managed by the runtime.
   *
   * @return a new Camunda client
   */
  CamundaClient createClient();

  /**
   * Creates a new preconfigured Camunda client that is managed by the runtime. The given modifier
   * can customize the client.
   *
   * @param modifier to customize the Camunda client
   * @return a new Camunda client
   */
  CamundaClient createClient(final Consumer<CamundaClientBuilder> modifier);

  /**
   * Creates a new preconfigured Zeebe client that is managed by the runtime.
   *
   * @return a new Zeebe client
   * @deprecated used to keep compatibility with the Zeebe client injection
   */
  @Deprecated
  ZeebeClient createZeebeClient();

  /**
   * Creates a new preconfigured Zeebe client that is managed by the runtime. The given modifier can
   * customize the client.
   *
   * @param modifier to customize the Zeebe client
   * @return a new Zeebe client
   * @deprecated used to keep compatibility with the Zeebe client injection
   */
  @Deprecated
  ZeebeClient createZeebeClient(final Consumer<ZeebeClientBuilder> modifier);

  /**
   * @return the URI of Camunda's gRPC API address
   */
  URI getCamundaGrpcAddress();

  /**
   * @return the URI of Camunda's REST API address
   */
  URI getCamundaRestAddress();

  /**
   * @return the URI of the connectors REST API address
   */
  URI getConnectorsAddress();

  /**
   * The current time may differ from the system time if the time was modified using {@link
   * #increaseTime(Duration)}.
   *
   * @return the current time for the process tests
   */
  Instant getCurrentTime();

  /**
   * Modifies the current time for the process tests. It can be used to jump to the future to avoid
   * waiting until a due date is reached, for example, of a BPMN timer event.
   *
   * @param timeToAdd the duration to add to the current time
   * @throws ArithmeticException if numeric overflow occurs
   */
  void increaseTime(final Duration timeToAdd);

  /**
   * Sets the current time and date for the process tests. It can be used to jump to a specific date
   * to avoid waiting until a due date is reached, for example, or start the process from a specific
   * date.
   *
   * @param timeToSet the time to set
   * @throws ArithmeticException if numeric overflow occurs
   */
  void setTime(final Instant timeToSet);

  /**
   * Creates a mock job worker for the specified job type.
   *
   * <p>This mock allows simulating job processing behavior, such as completing jobs, throwing BPMN
   * errors, or handling jobs with custom logic.
   *
   * @param jobType the job type to mock, matching the `zeebeJobType` in the BPMN model.
   * @return a {@see JobWorkerMock} instance for configuring the mock behavior.
   */
  JobWorkerMockBuilder mockJobWorker(final String jobType);

  /**
   * Mocks a child process with the specified ID.
   *
   * @param childProcessId the ID of the child process to mock
   */
  void mockChildProcess(final String childProcessId);

  /**
   * Mocks a child process with the specified ID and sets the provided variables.
   *
   * @param childProcessId the ID of the child process to mock
   * @param variables a map of variables to set for the mocked child process
   */
  void mockChildProcess(final String childProcessId, final Map<String, Object> variables);

  /**
   * Completes a job of the specified type.
   *
   * @param jobType the type of the job to complete, matching the `zeebeJobType` in the BPMN model
   */
  void completeJob(final String jobType);

  /**
   * Completes a job of the specified type with the variables from the example data property of the
   * related BPMN element. If no property is defined, it completes the job without variables.
   *
   * @param jobType the type of the job to complete, matching the `zeebeJobType` in the BPMN model
   */
  void completeJobWithExampleData(final String jobType);

  /**
   * Completes a job of the specified type and sets the provided variables.
   *
   * @param jobType the type of the job to complete, matching the `zeebeJobType` in the BPMN model
   * @param variables a map of variables to set when completing the job
   */
  void completeJob(final String jobType, final Map<String, Object> variables);

  /**
   * Completes a job matching the specified selector.
   *
   * @param jobSelector the selector to identify the job to complete
   */
  void completeJob(final JobSelector jobSelector);

  /**
   * Completes a job matching the specified selector and sets the provided variables.
   *
   * @param jobSelector the selector to identify the job to complete
   * @param variables a map of variables to set when completing the job
   */
  void completeJob(final JobSelector jobSelector, final Map<String, Object> variables);

  /**
   * Completes a job matching the specified selector with the variables from the example data
   * property of the related BPMN element. If no property is defined, it completes the job without
   * variables.
   *
   * @param jobSelector the selector to identify the job to complete
   */
  void completeJobWithExampleData(final JobSelector jobSelector);

  /**
   * Throws a BPMN error from a job of the specified type.
   *
   * @param jobType the type of the job to throw the error from, matching the `zeebeJobType` in the
   *     BPMN model
   * @param errorCode the error code to throw
   */
  void throwBpmnErrorFromJob(final String jobType, final String errorCode);

  /**
   * Throws a BPMN error from a job of the specified type and sets the provided variables.
   *
   * @param jobType the type of the job to throw the error from, matching the `zeebeJobType` in the
   *     BPMN model
   * @param errorCode the error code to throw
   * @param variables a map of variables to set when throwing the error
   */
  void throwBpmnErrorFromJob(
      final String jobType, final String errorCode, final Map<String, Object> variables);

  /**
   * Throws a BPMN error from a job of the specified type with an error message and sets the
   * provided variables.
   *
   * @param jobType the type of the job to throw the error from, matching the `zeebeJobType` in the
   *     BPMN model
   * @param errorCode the error code to throw
   * @param errorMessage the error message to throw
   * @param variables a map of variables to set when throwing the error
   */
  void throwBpmnErrorFromJob(
      final String jobType,
      final String errorCode,
      final String errorMessage,
      final Map<String, Object> variables);

  /**
   * Throws a BPMN error from a job that matches the specified selector.
   *
   * @param jobSelector the selector to identify the job to throw the error from
   * @param errorCode the error code to throw
   */
  void throwBpmnErrorFromJob(final JobSelector jobSelector, final String errorCode);

  /**
   * Throws a BPMN error from a job that matches the specified selector and sets the provided
   * variables.
   *
   * @param jobSelector the selector to identify the job to throw the error from
   * @param errorCode the error code to throw
   * @param variables a map of variables to set when throwing the error
   */
  void throwBpmnErrorFromJob(
      final JobSelector jobSelector, final String errorCode, final Map<String, Object> variables);

  /**
   * Throws a BPMN error from a job that matches the specified selector with an error message and
   * sets the provided variables.
   *
   * @param jobSelector the selector to identify the job to throw the error from
   * @param errorCode the error code to throw
   * @param errorMessage the error message to throw
   * @param variables a map of variables to set when throwing the error
   */
  void throwBpmnErrorFromJob(
      final JobSelector jobSelector,
      final String errorCode,
      final String errorMessage,
      final Map<String, Object> variables);

  /**
   * Completes a user task with the given BPMN element ID.
   *
   * @param elementId the BPMN element ID of the user task to complete
   */
  void completeUserTask(final String elementId);

  /**
   * Completes a user task with the given BPMN element ID and sets the provided variables.
   *
   * @param elementId the BPMN element ID of the user task to complete
   * @param variables a map of variables to set when completing the user task
   */
  void completeUserTask(final String elementId, final Map<String, Object> variables);

  /**
   * Completes a user task that matches the specified selector.
   *
   * @param userTaskSelector the selector to identify the user task to complete
   */
  void completeUserTask(final UserTaskSelector userTaskSelector);

  /**
   * Completes a user task that matches the specified selector and sets the provided variables.
   *
   * @param userTaskSelector the selector to identify the user task to complete
   * @param variables a map of variables to set when completing the user task
   */
  void completeUserTask(
      final UserTaskSelector userTaskSelector, final Map<String, Object> variables);

  /**
   * Completes a user task with the given element ID with the variables from the example data
   * property of the related BPMN element. If no property is defined, it completes the user task
   * without variables.
   *
   * @param elementId the elementId of the user task and its associated example data
   */
  void completeUserTaskWithExampleData(final String elementId);

  /**
   * Completes a user task with the given element selector with the variables from the example data
   * property of the related BPMN element. If no property is defined, it completes the user task
   * without variables.
   *
   * @param userTaskSelector the selector to identify the user task to complete
   */
  void completeUserTaskWithExampleData(final UserTaskSelector userTaskSelector);

  /**
   * Mocks a DMN decision with the specified decision ID and sets the provided variables.
   *
   * @param decisionId the ID of the DMN decision to mock
   * @param decisionOutput the decision's output which may be a value, list or map.
   */
  void mockDmnDecision(final String decisionId, final Object decisionOutput);

  /**
   * Resolves an incident matching the specified selector. If the incident is related to a job,
   * increases the job retries by 1 before resolving.
   *
   * @param incidentSelector the selector to identify the incident to resolve
   */
  void resolveIncident(final IncidentSelector incidentSelector);

  /**
   * Completes a job of an ad-hoc sub-process matching the specified selector.
   *
   * @param jobSelector the selector to identify the job to complete
   * @param jobResult the consumer to configure the ad-hoc sub-process result
   */
  void completeJobOfAdHocSubProcess(
      final JobSelector jobSelector, final Consumer<CompleteAdHocSubProcessResultStep1> jobResult);

  /**
   * Completes a job of an ad-hoc sub-process matching the specified selector and sets the provided
   * variables.
   *
   * @param jobSelector the selector to identify the job to complete
   * @param variables a map of variables to set when completing the job
   * @param jobResult the consumer to configure the ad-hoc sub-process result
   */
  void completeJobOfAdHocSubProcess(
      final JobSelector jobSelector,
      final Map<String, Object> variables,
      final Consumer<CompleteAdHocSubProcessResultStep1> jobResult);

  /**
   * Updates variables for a process instance.
   *
   * @param processInstanceSelector the selector to identify the process instance
   * @param variables a map of variables to update
   */
  void updateVariables(
      final ProcessInstanceSelector processInstanceSelector, final Map<String, Object> variables);

  /**
   * Updates local variables for a specific element within a process instance.
   *
   * @param processInstanceSelector the selector to identify the process instance
   * @param elementSelector the selector to identify the element
   * @param variables a map of variables to update
   */
  void updateLocalVariables(
      final ProcessInstanceSelector processInstanceSelector,
      final ElementSelector elementSelector,
      final Map<String, Object> variables);

  /**
   * Completes a job of a user task listener matching the specified selector.
   *
   * @param jobSelector the selector to identify the job to complete
   * @param jobResult the consumer to configure the user task job result
   */
  void completeJobOfUserTaskListener(
      final JobSelector jobSelector, final Consumer<CompleteUserTaskJobResultStep1> jobResult);
}
