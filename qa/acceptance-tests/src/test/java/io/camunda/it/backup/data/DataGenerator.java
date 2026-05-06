/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.backup.data;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.search.enums.ProcessInstanceState;
import io.camunda.client.api.search.enums.UserTaskState;
import io.camunda.client.api.search.response.ProcessInstance;
import io.camunda.client.api.search.response.SearchResponse;
import io.camunda.client.api.search.response.UserTask;
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

public class DataGenerator extends AbstractBackupDataGenerator {

  private static final int PROCESS_INSTANCE_NUMBER = 10;
  private static final String ASSIGNEE = "user";

  public final ConcurrentSkipListSet<Long> instanceKeys = new ConcurrentSkipListSet<>();

  private final String bpmnProcessId;
  private final Duration timeout;
  private final String[] taskTypes = {"task1", "userTask"};

  public DataGenerator(
      final CamundaClient camundaClient, final String bpmnProcessId, final Duration timeout) {
    super(camundaClient);
    this.bpmnProcessId = bpmnProcessId;
    this.timeout = timeout;
  }

  @Override
  public void createData() {
    deployProcess(bpmnProcessId, createModel());
    for (int i = 0; i < PROCESS_INSTANCE_NUMBER; i++) {
      final var evt =
          camundaClient
              .newCreateInstanceCommand()
              .bpmnProcessId(bpmnProcessId)
              .latestVersion()
              .variables(Map.of("var1", i))
              .send()
              .join();
      logger.debug("Process instance started with key {}", evt.getProcessInstanceKey());
      instanceKeys.add(evt.getProcessInstanceKey());
    }
    completeProcesses();
    verifyAllExported(ProcessInstanceState.COMPLETED);

    // generate uncompleted processes that will be restored to ACTIVE state after restore
    for (int i = 0; i < PROCESS_INSTANCE_NUMBER; i++) {
      final var evt =
          camundaClient
              .newCreateInstanceCommand()
              .bpmnProcessId(bpmnProcessId)
              .latestVersion()
              .variables(Map.of("var1", i))
              .send()
              .join();
      logger.debug("Process instance started with key {}", evt.getProcessInstanceKey());
      instanceKeys.add(evt.getProcessInstanceKey());
    }

    logger.info("Finished generating data");
  }

  @Override
  public void assertData() {
    verifyAllExported(ProcessInstanceState.ACTIVE);
    completeProcesses();
    verifyAllExported(ProcessInstanceState.COMPLETED);
  }

  @Override
  public void changeData() {
    // no state change between backup and restore for this generator
  }

  @Override
  public void assertDataAfterChange() {
    // no post-change verification needed
  }

  public void completeProcesses() {
    completeServiceTask(taskTypes[0], "var1");
    logger.debug("Service task completed");
    completeUserTasks();
    logger.debug("User task completed");
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

              response.get().items().stream()
                  .filter(inst -> inst.getState().equals(ProcessInstanceState.COMPLETED))
                  .map(ProcessInstance::getProcessInstanceKey)
                  .forEach(instanceKeys::remove);
            });
  }

  private void completeServiceTask(final String jobType, final String varName) {
    final var completedJobs = new ConcurrentHashMap<Long, Object>();
    logger.debug(
        "Starting to complete {} tasks of type {} with processInstanceKeys={}",
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
    logger.debug("Starting to complete {} user tasks", instanceKeys.size());
    final List<UserTask> items =
        Awaitility.await("all user tasks are ready")
            .atMost(timeout)
            .until(
                () -> {
                  try {
                    final var itemsFromQuery =
                        camundaClient
                            .newUserTaskSearchRequest()
                            .filter(f -> f.assignee(ASSIGNEE).state(UserTaskState.CREATED))
                            .send()
                            .join()
                            .items();
                    logger.debug("Found {} user tasks", itemsFromQuery.size());
                    return itemsFromQuery;
                  } catch (final RuntimeException e) {
                    logger.warn("Failed to complete user tasks, will retry", e);
                    return List.of();
                  }
                },
                l -> l.size() == instanceKeys.size());
    logger.debug("Fetched {} user tasks", items.size());

    items.forEach(
        item -> {
          logger.debug("Completing user task {}", item.getUserTaskKey());
          assertThat(
                  camundaClient
                      .newCompleteUserTaskCommand(item.getUserTaskKey())
                      .send()
                      .toCompletableFuture())
              .succeedsWithin(timeout);
        });
  }

  private BpmnModelInstance createModel() {
    return Bpmn.createExecutableProcess(bpmnProcessId)
        .startEvent("start")
        .serviceTask(taskTypes[0])
        .zeebeJobType(taskTypes[0])
        .zeebeInput("=var1", "varIn")
        .userTask(taskTypes[1])
        .zeebeUserTask()
        .zeebeInput("=var3", "varIn")
        .zeebeAssignee(ASSIGNEE)
        .endEvent()
        .done();
  }
}
