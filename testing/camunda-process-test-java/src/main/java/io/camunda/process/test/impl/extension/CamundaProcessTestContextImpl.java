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
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.search.enums.UserTaskState;
import io.camunda.client.api.search.filter.UserTaskFilter;
import io.camunda.client.api.search.response.UserTask;
import io.camunda.process.test.api.CamundaAssertAwaitBehavior;
import io.camunda.process.test.api.CamundaClientBuilderFactory;
import io.camunda.process.test.api.CamundaProcessTestContext;
import io.camunda.process.test.api.assertions.UserTaskSelector;
import io.camunda.process.test.api.assertions.UserTaskSelectors;
import io.camunda.process.test.api.mock.JobWorkerMockBuilder;
import io.camunda.process.test.impl.client.CamundaManagementClient;
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
    builder.preferRestOverGrpc(false);

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
    return new JobWorkerMockBuilderImpl(jobType, client);
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
    completeJob(jobType, Collections.emptyMap());
  }

  @Override
  public void completeJob(final String jobType, final Map<String, Object> variables) {
    final CamundaClient client = createClient();
    final ActivatedJob job = getActivatedJob(jobType, client);

    LOGGER.debug(
        "Complete job with variables {} [job-type: '{}', job-key: '{}']",
        variables,
        jobType,
        job.getKey());
    client.newCompleteCommand(job).variables(variables).send().join();
  }

  @Override
  public void throwBpmnErrorFromJob(final String jobType, final String errorCode) {
    throwBpmnErrorFromJob(jobType, errorCode, Collections.emptyMap());
  }

  @Override
  public void throwBpmnErrorFromJob(
      final String jobType, final String errorCode, final Map<String, Object> variables) {
    final CamundaClient client = createClient();
    final ActivatedJob job = getActivatedJob(jobType, client);

    LOGGER.debug(
        "Throw BPMN error with error code {} and variables {} [job-type: '{}', job-key: '{}']",
        errorCode,
        variables,
        jobType,
        job.getKey());
    client.newThrowErrorCommand(job).errorCode(errorCode).variables(variables).send().join();
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

    LOGGER.debug("Mock: Deploy a DMN '{}' with decision output {}", decisionId, decisionOutput);

    final String resourceName = decisionId + ".dmn";
    client
        .newDeployResourceCommand()
        .addResourceStream(
            new ByteArrayInputStream(Dmn.convertToString(modelInstance).getBytes()), resourceName)
        .send()
        .join();
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
}
