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
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.search.response.SearchResponse;
import io.camunda.client.api.search.response.UserTask;
import io.camunda.process.test.api.CamundaProcessTestContext;
import io.camunda.process.test.api.assertions.UserTaskSelector;
import io.camunda.process.test.api.assertions.UserTaskSelectors;
import io.camunda.process.test.api.mock.JobWorkerMock;
import io.camunda.process.test.impl.client.CamundaManagementClient;
import io.camunda.process.test.impl.containers.CamundaContainer;
import io.camunda.process.test.impl.containers.ConnectorsContainer;
import io.camunda.process.test.impl.mock.JobWorkerMockImpl;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.ZeebeClientBuilder;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.assertj.core.api.Assertions;
import org.awaitility.Awaitility;

public class CamundaProcessTestContextImpl implements CamundaProcessTestContext {

  private final CamundaContainer camundaContainer;
  private final ConnectorsContainer connectorsContainer;
  private final Consumer<AutoCloseable> clientCreationCallback;
  private final CamundaManagementClient camundaManagementClient;
  private final int TIMEOUT = 40;

  public CamundaProcessTestContextImpl(
      final CamundaContainer camundaContainer,
      final ConnectorsContainer connectorsContainer,
      final Consumer<AutoCloseable> clientCreationCallback,
      final CamundaManagementClient camundaManagementClient) {
    this.camundaContainer = camundaContainer;
    this.connectorsContainer = connectorsContainer;
    this.clientCreationCallback = clientCreationCallback;
    this.camundaManagementClient = camundaManagementClient;
  }

  @Override
  public CamundaClient createClient() {
    return createClient(builder -> {});
  }

  @Override
  public CamundaClient createClient(final Consumer<CamundaClientBuilder> modifier) {
    final CamundaClientBuilder builder =
        CamundaClient.newClientBuilder()
            .usePlaintext()
            .grpcAddress(getCamundaGrpcAddress())
            .restAddress(getCamundaRestAddress());

    modifier.accept(builder);

    final CamundaClient client = builder.build();
    clientCreationCallback.accept(client);

    return client;
  }

  @Override
  public ZeebeClient createZeebeClient() {
    return createZeebeClient(builder -> {});
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
    return camundaContainer.getGrpcApiAddress();
  }

  @Override
  public URI getCamundaRestAddress() {
    return camundaContainer.getRestApiAddress();
  }

  @Override
  public URI getConnectorsAddress() {
    return connectorsContainer.getRestApiAddress();
  }

  @Override
  public Instant getCurrentTime() {
    return camundaManagementClient.getCurrentTime();
  }

  @Override
  public void increaseTime(final Duration timeToAdd) {
    camundaManagementClient.increaseTime(timeToAdd);
  }

  @Override
  public JobWorkerMock mockJobWorker(final String jobType) {
    final CamundaClient client = createClient();
    return new JobWorkerMockImpl(jobType, client);
  }

  @Override
  public void completeJob(final String jobType) {
    completeJob(jobType, new HashMap<>());
  }

  @Override
  public void completeJob(final String jobType, final Map<String, Object> variables) {
    final CamundaClient client = createClient();
    final ActivatedJob job = getActivatedJob(jobType);
    client.newCompleteCommand(job).variables(variables).send().join();
  }

  @Override
  public void throwBpmnErrorFromJob(final String jobType, final String errorCode) {
    throwBpmnErrorFromJob(jobType, errorCode, new HashMap<>());
  }

  @Override
  public void throwBpmnErrorFromJob(
      final String jobType, final String errorCode, final Map<String, Object> variables) {
    final CamundaClient client = createClient();
    final ActivatedJob job = getActivatedJob(jobType);
    client.newThrowErrorCommand(job).errorCode(errorCode).variables(variables).send().join();
  }

  private ActivatedJob getActivatedJob(final String jobType) {
    final CamundaClient client = createClient();
    final AtomicReference<ActivatedJob> activatedJob = new AtomicReference<>();
    Awaitility.await()
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(
            () -> {
              final List<ActivatedJob> jobs =
                  client
                      .newActivateJobsCommand()
                      .jobType(jobType)
                      .maxJobsToActivate(1)
                      .send()
                      .join()
                      .getJobs();
              assertThat(jobs).isNotEmpty();
              activatedJob.set(jobs.get(0));
            });
    return activatedJob.get();
  }

  @Override
  public void completeUserTask(final String taskName) {
    completeUserTask(UserTaskSelectors.byTaskName(taskName), new HashMap<>());
  }

  @Override
  public void completeUserTask(final String taskName, final Map<String, Object> variables) {
    completeUserTask(UserTaskSelectors.byTaskName(taskName), variables);
  }

  @Override
  public void completeUserTask(final UserTaskSelector userTaskSelector) {
    completeUserTask(userTaskSelector, new HashMap<>());
  }

  @Override
  public void completeUserTask(
      final UserTaskSelector userTaskSelector, final Map<String, Object> variables) {
    final CamundaClient client = createClient();
    final SearchResponse<UserTask> result = client.newUserTaskSearchRequest().send().join();
    final AtomicReference<Long> userTaskKey = new AtomicReference<>();
    Awaitility.await("until user task is active")
        .ignoreExceptions()
        .atMost(Duration.ofSeconds(2 * TIMEOUT))
        .untilAsserted(
            () -> {
              final Future<SearchResponse<UserTask>> userTaskFuture =
                  client.newUserTaskSearchRequest().send();
              Assertions.assertThat(userTaskFuture)
                  .succeedsWithin(Duration.ofSeconds(TIMEOUT))
                  .extracting(SearchResponse::items)
                  .satisfies(
                      items -> {
                        final List<UserTask> tasks =
                            items.stream()
                                .filter(userTaskSelector::test)
                                .collect(Collectors.toList());
                        Assertions.assertThat(tasks).isNotEmpty();
                        userTaskKey.set(items.get(0).getUserTaskKey());
                      });
            });

    client.newUserTaskCompleteCommand(userTaskKey.get()).variables(variables).send().join();
  }
}
