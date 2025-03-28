/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.backup;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.search.response.ProcessInstance;
import io.camunda.client.api.search.response.SearchResponse;
import io.camunda.client.api.search.response.UserTask;
import io.camunda.client.wrappers.ProcessInstanceState;
import io.camunda.client.wrappers.UserTaskFilter;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.Future;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.awaitility.Awaitility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DataGenerator implements AutoCloseable {

  private static final Logger LOGGER = LoggerFactory.getLogger(DataGenerator.class);
  // process id -> process instance key
  public final ConcurrentSkipListSet<Long> instanceKeys = new ConcurrentSkipListSet<>();
  final String assignee = "user";
  private CamundaClient camundaClient;
  private final OneTaskProcess process;
  private final Duration timeout;

  public DataGenerator(
      final CamundaClient camundaClient, final String processId, final Duration timeout) {
    this.camundaClient = camundaClient;
    process = new OneTaskProcess(processId);
    this.timeout = timeout;
  }

  public void generateCompletedProcesses(final int processNumber) {
    generateUncompletedProcesses(processNumber);
    completeProcesses();
    verifyAllExported(ProcessInstanceState.COMPLETED);

    // wait for everything to be exported
    LOGGER.info("Finished generating data");
  }

  public void generateUncompletedProcesses(final int processNumber) {
    process.deploy();
    for (int i = 0; i < processNumber; i++) {
      process.startProcessInstance(i);
    }
  }

  /**
   * Completes all process instances that are present in {@link DataGenerator#instanceKeys}. Once
   * completed, the processes will be removed that map.
   */
  public void completeProcesses() {
    completeTask(process.tasks[0], "var1");
    LOGGER.debug("Service task completed");
    completeUserTasks();
    LOGGER.debug("User task completed");
  }

  public void verifyAllExported(final ProcessInstanceState state) {
    Awaitility.await("until all processes have been exported")
        .atMost(timeout)
        .untilAsserted(
            () -> {
              final Future<SearchResponse<ProcessInstance>> response =
                  camundaClient
                      .newProcessInstanceSearchRequest()
                      .filter(
                          b ->
                              b.processInstanceKey(p -> p.in(instanceKeys.stream().toList()))
                                  .state(state))
                      .page(b -> b.limit(instanceKeys.size()).from(0))
                      .send();
              assertThat(response)
                  .succeedsWithin(timeout)
                  .extracting(SearchResponse::items)
                  .asInstanceOf(InstanceOfAssertFactories.LIST)
                  .hasSameSizeAs(instanceKeys);

              // remove all completed instances
              response.get().items().stream()
                  .filter(inst -> inst.getState().equals(ProcessInstanceState.COMPLETED))
                  .map(ProcessInstance::getProcessInstanceKey)
                  .forEach(instanceKeys::remove);
            });
  }

  private void completeTask(final String jobType, final String varName) {
    final var completedJobs = new ConcurrentHashMap<Long, Object>();
    LOGGER.debug(
        "Starting to {} complete tasks of type {} with processInstanceKeys={}",
        instanceKeys.size(),
        jobType,
        instanceKeys);
    final var activateJobsResponse =
        camundaClient
            .newActivateJobsCommand()
            .jobType(jobType)
            .maxJobsToActivate(instanceKeys.size())
            .send()
            .join();
    activateJobsResponse
        .getJobs()
        .forEach(
            job ->
                camundaClient
                    .newCompleteCommand(job.getKey())
                    .variable(varName, job.getKey())
                    .send()
                    .join());
  }

  private void completeUserTasks() {
    LOGGER.debug("Starting to {} complete user tasks", instanceKeys.size());
    final List<UserTask> items =
        Awaitility.await("all user tasks are ready")
            .atMost(timeout)
            .until(
                () -> {
                  try {
                    final var itemsFromQuery =
                        camundaClient
                            .newUserTaskSearchRequest()
                            .filter(f -> f.assignee(assignee).state(UserTaskFilter.State.CREATED))
                            .send()
                            .join()
                            .items();
                    LOGGER.debug("Found {} user tasks", itemsFromQuery.size());
                    return itemsFromQuery;
                  } catch (final RuntimeException e) {
                    LOGGER.warn("Failed to complete user tasks, will retry", e);
                    return List.of();
                  }
                },
                l -> l.size() == instanceKeys.size());
    LOGGER.debug("Fetched {} user tasks", items.size());

    items.forEach(
        item -> {
          LOGGER.debug("Completing user task {}", item.getUserTaskKey());
          assertThat(
                  camundaClient
                      .newUserTaskCompleteCommand(item.getUserTaskKey())
                      .send()
                      .toCompletableFuture())
              .succeedsWithin(timeout);
        });
  }

  @Override
  public void close() throws Exception {
    camundaClient = null;
  }

  class OneTaskProcess {

    final String bpmnProcessId;

    private final String[] tasks = {"task1", "userTask"};

    OneTaskProcess(final String bpmnProcessId) {
      this.bpmnProcessId = bpmnProcessId;
    }

    private BpmnModelInstance createModel() {
      return Bpmn.createExecutableProcess(bpmnProcessId)
          .startEvent("start")
          .serviceTask(tasks[0])
          .zeebeJobType(tasks[0])
          .zeebeInput("=var1", "varIn")
          .userTask(tasks[1])
          .zeebeUserTask()
          .zeebeInput("=var3", "varIn")
          .zeebeAssignee(assignee)
          .endEvent()
          .done();
    }

    private void startProcessInstance(final Object payload) {
      final var evt =
          camundaClient
              .newCreateInstanceCommand()
              .bpmnProcessId(bpmnProcessId)
              .latestVersion()
              .variables(Map.of("var1", payload))
              .send()
              .join();
      LOGGER.debug("Process instance started with key {}", evt.getProcessInstanceKey());
      instanceKeys.add(evt.getProcessInstanceKey());
    }

    private void deploy() {
      final var deployResourceCmd =
          camundaClient
              .newDeployResourceCommand()
              .addProcessModel(process.createModel(), bpmnProcessId + ".bpmn");
      final var deploymentEvent = deployResourceCmd.send().join();
      LOGGER.debug("Deployed process {} with key {}", bpmnProcessId, deploymentEvent.getKey());
    }
  }
}
