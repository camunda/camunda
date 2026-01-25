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
package io.camunda.process.test.impl.extension;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.CamundaClientBuilder;
import io.camunda.client.api.JsonMapper;
import io.camunda.client.api.command.CompleteAdHocSubProcessResultStep1;
import io.camunda.client.api.command.CompleteUserTaskJobResultStep1;
import io.camunda.client.api.command.ThrowErrorCommandStep1;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.search.enums.ElementInstanceState;
import io.camunda.client.api.search.enums.IncidentState;
import io.camunda.client.api.search.enums.JobKind;
import io.camunda.client.api.search.enums.JobState;
import io.camunda.client.api.search.enums.UserTaskState;
import io.camunda.client.api.search.filter.ElementInstanceFilter;
import io.camunda.client.api.search.filter.IncidentFilter;
import io.camunda.client.api.search.filter.JobFilter;
import io.camunda.client.api.search.filter.UserTaskFilter;
import io.camunda.client.api.search.response.ElementInstance;
import io.camunda.client.api.search.response.Incident;
import io.camunda.client.api.search.response.Job;
import io.camunda.client.api.search.response.ProcessInstance;
import io.camunda.client.api.search.response.UserTask;
import io.camunda.process.test.api.CamundaAssertAwaitBehavior;
import io.camunda.process.test.api.CamundaClientBuilderFactory;
import io.camunda.process.test.api.CamundaProcessTestContext;
import io.camunda.process.test.api.assertions.ElementSelector;
import io.camunda.process.test.api.assertions.IncidentSelector;
import io.camunda.process.test.api.assertions.JobSelector;
import io.camunda.process.test.api.assertions.JobSelectors;
import io.camunda.process.test.api.assertions.ProcessInstanceSelector;
import io.camunda.process.test.api.assertions.UserTaskSelector;
import io.camunda.process.test.api.assertions.UserTaskSelectors;
import io.camunda.process.test.api.mock.JobWorkerMockBuilder;
import io.camunda.process.test.impl.client.CamundaManagementClient;
import io.camunda.process.test.impl.mock.BpmnExampleDataReader;
import io.camunda.process.test.impl.mock.BpmnExampleDataReader.BpmnExampleDataReaderException;
import io.camunda.process.test.impl.mock.JobWorkerMockBuilderImpl;
import io.camunda.process.test.impl.runtime.CamundaProcessTestRuntime;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.ZeebeClientBuilder;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import org.camunda.bpm.model.dmn.Dmn;
import org.camunda.bpm.model.dmn.DmnModelInstance;
import org.camunda.bpm.model.dmn.instance.Decision;
import org.camunda.bpm.model.dmn.instance.Definitions;
import org.camunda.bpm.model.dmn.instance.LiteralExpression;
import org.camunda.bpm.model.dmn.instance.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CamundaProcessTestContextImpl implements CamundaProcessTestContext {

  private static final Logger LOGGER = LoggerFactory.getLogger(CamundaProcessTestContextImpl.class);

  // We can complete only created user tasks. Ignore other states.
  private static final Consumer<UserTaskFilter> DEFAULT_USER_TASK_COMPLETION_FILTER =
      filter -> filter.state(UserTaskState.CREATED);

  // We can complete only jobs that are in CREATED, FAILED, RETRIES_UPDATED, or TIMED_OUT states
  // and have retries >= 1
  private static final Consumer<JobFilter> DEFAULT_JOB_COMPLETION_FILTER =
      filter ->
          filter
              .state(
                  state ->
                      state.in(
                          JobState.CREATED,
                          JobState.FAILED,
                          JobState.RETRIES_UPDATED,
                          JobState.TIMED_OUT))
              .retries(retries -> retries.gte(1));

  // We can update variables only for active element instances
  private static final Consumer<ElementInstanceFilter> DEFAULT_ELEMENT_INSTANCE_FILTER =
      filter -> filter.state(ElementInstanceState.ACTIVE);

  // We can resolve only active incidents
  private static final Consumer<IncidentFilter> DEFAULT_INCIDENT_RESOLUTION_FILTER =
      filter -> filter.state(IncidentState.ACTIVE);

  private final URI camundaRestApiAddress;
  private final URI camundaGrpcApiAddress;
  private final URI connectorsRestApiAddress;
  private final CamundaClientBuilderFactory camundaClientBuilderFactory;
  private final Consumer<AutoCloseable> clientCreationCallback;
  private final CamundaManagementClient camundaManagementClient;

  private final JsonMapper jsonMapper;
  private final io.camunda.zeebe.client.api.JsonMapper zeebeJsonMapper;
  private final CamundaAssertAwaitBehavior awaitBehavior;

  public CamundaProcessTestContextImpl(
      final CamundaProcessTestRuntime camundaRuntime,
      final Consumer<AutoCloseable> clientCreationCallback,
      final CamundaManagementClient camundaManagementClient,
      final CamundaAssertAwaitBehavior awaitBehavior,
      final JsonMapper jsonMapper,
      final io.camunda.zeebe.client.api.JsonMapper zeebeJsonMapper) {

    camundaClientBuilderFactory = camundaRuntime.getCamundaClientBuilderFactory();
    camundaRestApiAddress = camundaRuntime.getCamundaRestApiAddress();
    camundaGrpcApiAddress = camundaRuntime.getCamundaGrpcApiAddress();
    connectorsRestApiAddress = camundaRuntime.getConnectorsRestApiAddress();
    this.clientCreationCallback = clientCreationCallback;
    this.camundaManagementClient = camundaManagementClient;
    this.awaitBehavior = awaitBehavior;
    this.jsonMapper = jsonMapper;
    this.zeebeJsonMapper = zeebeJsonMapper;
  }

  @Override
  public CamundaClient createClient() {
    return createClient(
        builder -> {
          if (jsonMapper != null) {
            builder.withJsonMapper(jsonMapper);
          }
        });
  }

  @Override
  public CamundaClient createClient(final Consumer<CamundaClientBuilder> modifier) {
    final CamundaClientBuilder builder = camundaClientBuilderFactory.get();

    modifier.accept(builder);

    final CamundaClient client = builder.build();
    clientCreationCallback.accept(client);

    return client;
  }

  @Override
  public ZeebeClient createZeebeClient() {
    return createZeebeClient(
        builder -> {
          if (zeebeJsonMapper != null) {
            builder.withJsonMapper(zeebeJsonMapper);
          }
        });
  }

  @Override
  public ZeebeClient createZeebeClient(final Consumer<ZeebeClientBuilder> modifier) {
    final ZeebeClientBuilder builder =
        ZeebeClient.newClientBuilder()
            .usePlaintext()
            .grpcAddress(getCamundaGrpcAddress())
            .restAddress(getCamundaRestAddress());

    modifier.accept(builder);

    final ZeebeClient client = builder.build();
    clientCreationCallback.accept(client);

    return client;
  }

  @Override
  public URI getCamundaGrpcAddress() {
    return camundaGrpcApiAddress;
  }

  @Override
  public URI getCamundaRestAddress() {
    return camundaRestApiAddress;
  }

  @Override
  public URI getConnectorsAddress() {
    return connectorsRestApiAddress;
  }

  @Override
  public Instant getCurrentTime() {
    return camundaManagementClient.getCurrentTime();
  }

  @Override
  public void increaseTime(final Duration timeToAdd) {
    LOGGER.debug("Increase the time by {}", timeToAdd);
    camundaManagementClient.increaseTime(timeToAdd);
  }

  @Override
  public void setTime(final Instant timeToSet) {
    LOGGER.debug("Setting the time to {}", timeToSet);
    camundaManagementClient.setTime(timeToSet);
  }

  @Override
  public JobWorkerMockBuilder mockJobWorker(final String jobType) {
    final CamundaClient client = createClient();
    final BpmnExampleDataReader exampleDataReader =
        new BpmnExampleDataReader(client, awaitBehavior);

    return new JobWorkerMockBuilderImpl(jobType, client, exampleDataReader);
  }

  @Override
  public void mockChildProcess(final String childProcessId) {
    mockChildProcess(childProcessId, Collections.emptyMap());
  }

  @Override
  public void mockChildProcess(final String childProcessId, final Map<String, Object> variables) {
    final CamundaClient client = createClient();
    final BpmnModelInstance processModel =
        Bpmn.createExecutableProcess(childProcessId)
            .startEvent()
            .endEvent(
                "child-end",
                e ->
                    variables.forEach(
                        (k, v) ->
                            e.zeebeOutput(
                                "=" + client.getConfiguration().getJsonMapper().toJson(v), k)))
            .done();

    LOGGER.debug(
        "Mock: Deploy a child process '{}' with result variables {}", childProcessId, variables);

    final String resourceName = childProcessId + ".bpmn";
    client.newDeployResourceCommand().addProcessModel(processModel, resourceName).send().join();
  }

  @Override
  public void completeJob(final String jobType) {
    completeJob(JobSelectors.byJobType(jobType), Collections.emptyMap());
  }

  @Override
  public void completeJobWithExampleData(final String jobType) {
    completeJobWithExampleData(JobSelectors.byJobType(jobType));
  }

  @Override
  public void completeJob(final String jobType, final Map<String, Object> variables) {
    completeJob(JobSelectors.byJobType(jobType), variables);
  }

  @Override
  public void completeJob(final JobSelector jobSelector) {
    completeJob(jobSelector, Collections.emptyMap());
  }

  @Override
  public void completeJob(final JobSelector jobSelector, final Map<String, Object> variables) {
    final CamundaClient client = createClient();

    // completing the job inside the await block to handle the eventual consistency of the API
    awaitJob(
        jobSelector,
        client,
        job -> {
          LOGGER.debug(
              "Mock: Complete job [{}, jobKey: '{}'] with variables {}",
              jobSelector.describe(),
              job.getJobKey(),
              variables);

          client.newCompleteCommand(job.getJobKey()).variables(variables).send().join();
        });
  }

  @Override
  public void completeJobWithExampleData(final JobSelector jobSelector) {
    final CamundaClient client = createClient();
    final BpmnExampleDataReader exampleDataReader =
        new BpmnExampleDataReader(client, awaitBehavior);

    // completing the job inside the await block to handle the eventual consistency of the API
    awaitJob(
        jobSelector,
        client,
        job -> {
          final String logPrefix =
              String.format(
                  "Mock: Complete job [%s, jobKey: '%s']", jobSelector.describe(), job.getJobKey());

          try {
            final String exampleDataVariables =
                exampleDataReader.readExampleData(
                    job.getProcessDefinitionKey(),
                    job.getProcessDefinitionId(),
                    job.getElementId());

            LOGGER.debug("{} with example data {}", logPrefix, exampleDataVariables);
            client
                .newCompleteCommand(job.getJobKey())
                .variables(exampleDataVariables)
                .send()
                .join();
          } catch (final BpmnExampleDataReaderException e) {

            LOGGER.warn("{} without example data due to errors. {}", logPrefix, e.getMessage());
            client.newCompleteCommand(job.getJobKey()).send().join();
          }
        });
  }

  @Override
  public void throwBpmnErrorFromJob(final String jobType, final String errorCode) {
    throwBpmnErrorFromJob(JobSelectors.byJobType(jobType), errorCode);
  }

  @Override
  public void throwBpmnErrorFromJob(
      final String jobType, final String errorCode, final Map<String, Object> variables) {
    throwBpmnErrorFromJob(JobSelectors.byJobType(jobType), errorCode, variables);
  }

  @Override
  public void throwBpmnErrorFromJob(
      final String jobType,
      final String errorCode,
      final String errorMessage,
      final Map<String, Object> variables) {
    throwBpmnErrorFromJob(JobSelectors.byJobType(jobType), errorCode, errorMessage, variables);
  }

  @Override
  public void throwBpmnErrorFromJob(final JobSelector jobSelector, final String errorCode) {
    throwBpmnErrorFromJob(jobSelector, errorCode, Collections.emptyMap());
  }

  @Override
  public void throwBpmnErrorFromJob(
      final JobSelector jobSelector, final String errorCode, final Map<String, Object> variables) {
    throwBpmnErrorFromJob(jobSelector, errorCode, null, variables);
  }

  @Override
  public void throwBpmnErrorFromJob(
      final JobSelector jobSelector,
      final String errorCode,
      final String errorMessage,
      final Map<String, Object> variables) {
    final CamundaClient client = createClient();

    awaitJob(
        jobSelector,
        client,
        job -> {
          LOGGER.debug(
              "Mock: Throw BPMN error [{}, jobKey: '{}'] with error code {} and variables {}",
              jobSelector.describe(),
              job.getJobKey(),
              errorCode,
              variables);

          final ThrowErrorCommandStep1.ThrowErrorCommandStep2 command =
              client
                  .newThrowErrorCommand(job.getJobKey())
                  .errorCode(errorCode)
                  .variables(variables);

          if (errorMessage != null) {
            command.errorMessage(errorMessage);
          }

          command.send().join();
        });
  }

  @Override
  public void completeUserTask(final String elementId) {
    completeUserTask(UserTaskSelectors.byElementId(elementId), Collections.emptyMap());
  }

  @Override
  public void completeUserTask(final String elementId, final Map<String, Object> variables) {
    completeUserTask(UserTaskSelectors.byElementId(elementId), variables);
  }

  @Override
  public void completeUserTask(final UserTaskSelector userTaskSelector) {
    completeUserTask(userTaskSelector, Collections.emptyMap());
  }

  @Override
  public void completeUserTask(
      final UserTaskSelector userTaskSelector, final Map<String, Object> variables) {
    final CamundaClient client = createClient();

    // completing the user task inside the await block to handle the eventual consistency of the API
    awaitUserTask(
        userTaskSelector,
        client,
        userTask -> {
          LOGGER.debug(
              "Mock: Complete user task [{}, userTaskKey: '{}'] with variables {}",
              userTaskSelector.describe(),
              userTask.getUserTaskKey(),
              variables);

          client
              .newCompleteUserTaskCommand(userTask.getUserTaskKey())
              .variables(variables)
              .send()
              .join();
        });
  }

  @Override
  public void completeUserTaskWithExampleData(final String elementId) {
    completeUserTaskWithExampleData(UserTaskSelectors.byElementId(elementId));
  }

  @Override
  public void completeUserTaskWithExampleData(final UserTaskSelector userTaskSelector) {
    final CamundaClient client = createClient();
    final BpmnExampleDataReader exampleDataReader =
        new BpmnExampleDataReader(client, awaitBehavior);

    // completing the user task inside the await block to handle the eventual consistency of the API
    awaitUserTask(
        userTaskSelector,
        client,
        userTask -> {
          final String logPrefix =
              String.format(
                  "Mock: Complete user task [%s, userTaskKey: '%s']",
                  userTaskSelector.describe(), userTask.getUserTaskKey());

          try {
            final String exampleData =
                exampleDataReader.readExampleData(
                    userTask.getProcessDefinitionKey(),
                    userTask.getBpmnProcessId(),
                    userTask.getElementId());

            LOGGER.debug("{} with example data {}", logPrefix, exampleData);
            client
                .newCompleteUserTaskCommand(userTask.getUserTaskKey())
                .variables(exampleData)
                .send()
                .join();
          } catch (final BpmnExampleDataReaderException e) {

            LOGGER.warn("{} without example data due to errors. {}", logPrefix, e.getMessage());
            client.newCompleteUserTaskCommand(userTask.getUserTaskKey()).send().join();
          }
        });
  }

  @Override
  public void mockDmnDecision(final String decisionId, final Object decisionOutput) {
    final CamundaClient client = createClient();
    final String jsonVariables = client.getConfiguration().getJsonMapper().toJson(decisionOutput);

    // Create an empty DMN model
    final DmnModelInstance modelInstance = Dmn.createEmptyModel();

    // Create and configure the definitions element
    final Definitions definitions = modelInstance.newInstance(Definitions.class);
    definitions.setName(decisionId + "-name");
    definitions.setNamespace("http://camunda.org/schema/1.0/dmn");
    modelInstance.setDefinitions(definitions);

    // Create the decision element
    final Decision decision = modelInstance.newInstance(Decision.class);
    decision.setId(decisionId);
    decision.setName(decisionId + "-decision-name");
    definitions.addChildElement(decision);

    final LiteralExpression literalExpression = modelInstance.newInstance(LiteralExpression.class);
    final Text text = modelInstance.newInstance(Text.class);
    text.setTextContent(jsonVariables);
    literalExpression.setText(text);
    decision.addChildElement(literalExpression);

    LOGGER.debug(
        "Mock: Deploy a DMN [decisionId: '{}'] with decision output {}",
        decisionId,
        decisionOutput);

    final String resourceName = decisionId + ".dmn";
    client
        .newDeployResourceCommand()
        .addResourceStream(
            new ByteArrayInputStream(Dmn.convertToString(modelInstance).getBytes()), resourceName)
        .send()
        .join();
  }

  @Override
  public void resolveIncident(final IncidentSelector incidentSelector) {
    final CamundaClient client = createClient();

    awaitIncident(
        incidentSelector,
        client,
        incident -> {
          final long incidentKey = incident.getIncidentKey();

          // If the incident has a job key, update the job retries to 1 before resolving
          // This allows the job to be retried once, enabling the process instance to continue
          if (incident.getJobKey() != null) {
            final long jobKey = incident.getJobKey();
            LOGGER.debug("Updating job retries for job key: {}", jobKey);
            client.newUpdateRetriesCommand(jobKey).retries(1).send().join();
          }

          LOGGER.debug("Resolving incident [{}]", incidentSelector.describe());
          client.newResolveIncidentCommand(incidentKey).send().join();
        });
  }

  @Override
  public void completeJobOfAdHocSubProcess(
      final JobSelector jobSelector, final Consumer<CompleteAdHocSubProcessResultStep1> jobResult) {
    completeJobOfAdHocSubProcess(jobSelector, Collections.emptyMap(), jobResult);
  }

  @Override
  public void completeJobOfAdHocSubProcess(
      final JobSelector jobSelector,
      final Map<String, Object> variables,
      final Consumer<CompleteAdHocSubProcessResultStep1> jobResult) {
    final CamundaClient client = createClient();

    // completing the job inside the await block to handle the eventual consistency of the API
    awaitJob(
        JobSelectors.byJobKind(JobKind.AD_HOC_SUB_PROCESS).and(jobSelector),
        client,
        job -> {
          LOGGER.debug(
              "Mock: Complete ad-hoc sub-process job [{}, jobKey: '{}'] with variables {}",
              jobSelector.describe(),
              job.getJobKey(),
              variables);

          client
              .newCompleteCommand(job.getJobKey())
              .variables(variables)
              .withResult(
                  result -> {
                    final CompleteAdHocSubProcessResultStep1 adHocResult =
                        result.forAdHocSubProcess();
                    jobResult.accept(adHocResult);
                    return adHocResult;
                  })
              .send()
              .join();
        });
  }

  @Override
  public void completeJobOfUserTaskListener(
      final JobSelector jobSelector, final Consumer<CompleteUserTaskJobResultStep1> jobResult) {
    final CamundaClient client = createClient();

    // completing the job inside the await block to handle the eventual consistency of the API
    awaitJob(
        JobSelectors.byJobKind(JobKind.TASK_LISTENER).and(jobSelector),
        client,
        job -> {
          LOGGER.debug(
              "Mock: Complete user task listener job [{}, jobKey: '{}']",
              jobSelector.describe(),
              job.getJobKey());

          client
              .newCompleteCommand(job.getJobKey())
              .withResult(
                  result -> {
                    final CompleteUserTaskJobResultStep1 userTaskResult = result.forUserTask();
                    jobResult.accept(userTaskResult);
                    return userTaskResult;
                  })
              .send()
              .join();
        });
  }

  private void awaitUserTask(
      final UserTaskSelector userTaskSelector,
      final CamundaClient client,
      final Consumer<UserTask> userTaskConsumer) {

    awaitBehavior.untilAsserted(
        () -> findUserTask(userTaskSelector, client),
        userTask -> {
          assertThat(userTask)
              .withFailMessage(
                  "Expected to complete user task [%s] but no user task is available.",
                  userTaskSelector.describe())
              .isPresent();

          userTask.ifPresent(userTaskConsumer);
        });
  }

  private Optional<UserTask> findUserTask(
      final UserTaskSelector userTaskSelector, final CamundaClient client) {
    return client
        .newUserTaskSearchRequest()
        .filter(
            filter ->
                DEFAULT_USER_TASK_COMPLETION_FILTER
                    .andThen(userTaskSelector::applyFilter)
                    .accept(filter))
        .send()
        .join()
        .items()
        .stream()
        .filter(userTaskSelector::test)
        .findFirst();
  }

  private void awaitJob(
      final JobSelector jobSelector, final CamundaClient client, final Consumer<Job> jobConsumer) {

    awaitBehavior.untilAsserted(
        () -> findJob(jobSelector, client),
        job -> {
          assertThat(job)
              .withFailMessage(
                  "Expected to complete job [%s] but no job is available.", jobSelector.describe())
              .isPresent();

          job.ifPresent(jobConsumer);
        });
  }

  private Optional<Job> findJob(final JobSelector jobSelector, final CamundaClient client) {
    return client
        .newJobSearchRequest()
        .filter(
            filter ->
                DEFAULT_JOB_COMPLETION_FILTER.andThen(jobSelector::applyFilter).accept(filter))
        .send()
        .join()
        .items()
        .stream()
        .filter(jobSelector::test)
        .findFirst();
  }

  private ActivatedJob getActivatedJob(final String jobType, final CamundaClient client) {
    return awaitBehavior.until(
        () ->
            client
                .newActivateJobsCommand()
                .jobType(jobType)
                .maxJobsToActivate(1)
                .requestTimeout(Duration.ofSeconds(1)) // avoid long blocking call
                .send()
                .join()
                .getJobs()
                .stream()
                .findFirst()
                .orElse(null),
        job ->
            assertThat(job)
                .withFailMessage(
                    "Expected to complete a job with the type '%s' but no job is available.",
                    jobType)
                .isNotNull());
  }

  private void awaitIncident(
      final IncidentSelector incidentSelector,
      final CamundaClient client,
      final Consumer<Incident> incidentConsumer) {

    awaitBehavior.untilAsserted(
        () -> findIncident(incidentSelector, client),
        incident -> {
          assertThat(incident)
              .withFailMessage(
                  "Expected to resolve an incident [%s] but no incident was found.",
                  incidentSelector.describe())
              .isPresent();

          incident.ifPresent(incidentConsumer);
        });
  }

  private Optional<Incident> findIncident(
      final IncidentSelector incidentSelector, final CamundaClient client) {
    return client
        .newIncidentSearchRequest()
        .filter(
            filter -> {
              DEFAULT_INCIDENT_RESOLUTION_FILTER
                  .andThen(incidentSelector::applyFilter)
                  .accept(filter);
            })
        .send()
        .join()
        .items()
        .stream()
        .filter(incidentSelector::test)
        .findFirst();
  }

  @Override
  public void updateVariables(
      final ProcessInstanceSelector processInstanceSelector, final Map<String, Object> variables) {
    final CamundaClient client = createClient();

    awaitProcessInstance(
        processInstanceSelector,
        client,
        pi -> {
          LOGGER.debug(
              "Update variables for process instance [{}, processInstanceKey: '{}'] with variables {}",
              processInstanceSelector.describe(),
              pi.getProcessInstanceKey(),
              variables);

          client
              .newSetVariablesCommand(pi.getProcessInstanceKey())
              .variables(variables)
              .send()
              .join();
        });
  }

  @Override
  public void updateLocalVariables(
      final ProcessInstanceSelector processInstanceSelector,
      final ElementSelector elementSelector,
      final Map<String, Object> variables) {
    final CamundaClient client = createClient();

    awaitProcessInstance(
        processInstanceSelector,
        client,
        pi ->
            awaitElementInstance(
                pi.getProcessInstanceKey(),
                elementSelector,
                client,
                ei -> {
                  LOGGER.debug(
                      "Update local variables for element [{}, elementInstanceKey: '{}'] in process instance [processInstanceKey: '{}'] with variables {}",
                      elementSelector.describe(),
                      ei.getElementInstanceKey(),
                      pi.getProcessInstanceKey(),
                      variables);

                  client
                      .newSetVariablesCommand(ei.getElementInstanceKey())
                      .variables(variables)
                      .send()
                      .join();
                }));
  }

  private Optional<ProcessInstance> findProcessInstance(
      final ProcessInstanceSelector processInstanceSelector, final CamundaClient client) {
    return client
        .newProcessInstanceSearchRequest()
        .filter(processInstanceSelector::applyFilter)
        .send()
        .join()
        .items()
        .stream()
        .filter(processInstanceSelector::test)
        .findFirst();
  }

  private Optional<ElementInstance> findElementInstance(
      final long processInstanceKey,
      final ElementSelector elementSelector,
      final CamundaClient client) {
    return client
        .newElementInstanceSearchRequest()
        .filter(
            filter ->
                DEFAULT_ELEMENT_INSTANCE_FILTER
                    .andThen(elementSelector::applyFilter)
                    .accept(filter.processInstanceKey(processInstanceKey)))
        .send()
        .join()
        .items()
        .stream()
        .filter(elementSelector::test)
        .findFirst();
  }

  private void awaitProcessInstance(
      final ProcessInstanceSelector processInstanceSelector,
      final CamundaClient client,
      final Consumer<ProcessInstance> processInstanceConsumer) {

    awaitBehavior.untilAsserted(
        () -> findProcessInstance(processInstanceSelector, client),
        processInstance -> {
          assertThat(processInstance)
              .withFailMessage(
                  "Expected to update variables for process instance [%s] but no process instance is available.",
                  processInstanceSelector.describe())
              .isPresent();

          processInstance.ifPresent(processInstanceConsumer);
        });
  }

  private void awaitElementInstance(
      final long processInstanceKey,
      final ElementSelector elementSelector,
      final CamundaClient client,
      final Consumer<ElementInstance> elementInstanceConsumer) {

    awaitBehavior.untilAsserted(
        () -> findElementInstance(processInstanceKey, elementSelector, client),
        elementInstance -> {
          assertThat(elementInstance)
              .withFailMessage(
                  "Expected to update local variables for element [%s] in process instance [processInstanceKey: %s] but no element is available.",
                  elementSelector.describe(), processInstanceKey)
              .isPresent();

          elementInstance.ifPresent(elementInstanceConsumer);
        });
  }
}
