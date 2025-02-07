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
import io.camunda.client.api.search.response.ProcessInstanceState;
import io.camunda.client.api.search.response.SearchQueryResponse;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.awaitility.Awaitility;
import org.hamcrest.Matchers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DataGenerator implements AutoCloseable {
  private static final Logger LOGGER = LoggerFactory.getLogger(DataGenerator.class);
  // process id -> process instance key
  public final ConcurrentSkipListSet<String> instancekeys = new ConcurrentSkipListSet<>();
  final String assignee = "user";
  private CamundaClient camundaClient;
  private final OneTaskProcess process;

  public DataGenerator(final CamundaClient camundaClient, final String processId) {
    this.camundaClient = camundaClient;
    process = new OneTaskProcess(processId);
  }

  public void generate(final int processNumber) {
    process.deploy();
    for (int i = 0; i < processNumber; i++) {
      process.startProcessInstance(processNumber);
    }
    // complete tasks
    completeTask(process.tasks[0], "var1", processNumber);
    LOGGER.debug("Service task completed");
    completeUserTasks(processNumber);
    LOGGER.debug("User task completed");

    verifyAllExported();

    // wait for everything to be exported
    LOGGER.info("Finished generating data");
  }

  public void setCamundaClient(final CamundaClient camundaClient) {
    this.camundaClient = camundaClient;
  }

  public void verifyAllExported() {
    verifyAllExported(120);
  }

  public void verifyAllExported(final int timeoutSeconds) {
    Awaitility.await("until all processes have been exported")
        .atMost(Duration.ofSeconds(timeoutSeconds))
        .untilAsserted(
            () -> {
              final Future<SearchQueryResponse<ProcessInstance>> response =
                  camundaClient
                      .newProcessInstanceQuery()
                      .filter(
                          b ->
                              b.processInstanceKey(p -> p.in(instancekeys.stream().toList()))
                                  .state(ProcessInstanceState.COMPLETED))
                      .page(b -> b.limit(instancekeys.size()).from(0))
                      .send();
              assertThat(response)
                  .succeedsWithin(Duration.ofSeconds(timeoutSeconds))
                  .extracting(SearchQueryResponse::items)
                  .asInstanceOf(InstanceOfAssertFactories.LIST)
                  .hasSameSizeAs(instancekeys);
            });
  }

  private void completeTask(final String jobType, final String varName, final int number) {
    final var completedJobs = new AtomicInteger(0);
    final var worker =
        camundaClient
            .newWorker()
            .jobType(jobType)
            .handler(
                (jobClient, job) -> {
                  jobClient
                      .newCompleteCommand(job.getKey())
                      .variable(varName, job.getKey())
                      .send()
                      .thenRun(completedJobs::incrementAndGet);
                })
            .timeout(5000)
            .name("completeTask");
    try (final var jobWorker = worker.open()) {
      Awaitility.await("until all jobs have been completed")
          .atMost(Duration.ofSeconds(30))
          .untilAtomic(completedJobs, Matchers.equalTo(number));
    }
  }

  private void completeUserTasks(final int number) {
    final var items =
        Awaitility.await("all user tasks are ready")
            .atMost(Duration.ofSeconds(30))
            .until(
                () ->
                    camundaClient
                        .newUserTaskQuery()
                        .filter(f -> f.assignee(assignee))
                        .send()
                        .join()
                        .items(),
                l -> l.size() == number);

    items.forEach(
        item -> camundaClient.newUserTaskCompleteCommand(item.getUserTaskKey()).send().join());
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
      instancekeys.add(String.valueOf(evt.getProcessInstanceKey()));
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
