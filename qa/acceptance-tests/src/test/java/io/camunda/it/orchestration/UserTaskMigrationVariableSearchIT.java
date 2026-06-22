/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.orchestration;

import static io.camunda.it.util.TestHelper.activateAndCompleteJobs;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.MigrationPlan;
import io.camunda.client.api.search.enums.ElementInstanceState;
import io.camunda.client.api.search.enums.UserTaskState;
import io.camunda.exporter.CamundaExporter;
import io.camunda.qa.util.cluster.TestCamundaApplication;
import io.camunda.qa.util.multidb.MultiDbConfigurator;
import io.camunda.security.api.model.config.AuthenticationMethod;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneApplication;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.test.util.testcontainers.TestSearchContainers;
import java.time.Duration;
import java.util.Map;
import org.agrona.CloseHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Reproduces the gap described in <a
 * href="https://github.com/camunda/camunda/issues/49963">#49963</a>.
 *
 * <p>Scenario:
 *
 * <ol>
 *   <li>Deploy a process v1 that has <b>no user task</b> (just a service task that waits on a job).
 *   <li>Start an instance of v1 with a process-instance variable, and let it wait on the service
 *       task.
 *   <li>Deploy a process v2 that <b>does</b> have a Camunda user task after the service task.
 *   <li>Migrate the running instance from v1 to v2 (map the active service task to v2's service
 *       task).
 *   <li>Complete the service-task job so the instance proceeds into the user task.
 *   <li>Search user tasks by the {@code processInstanceVariables} filter.
 * </ol>
 *
 * <p>This test requires the exporter optimization {@code skipVariableWriteWithoutUserTasks=true}
 * (enabled below). With it on, variables of the no-user-task v1 instance are never written to the
 * {@code tasklist-task} index, and migration does not back-fill them. Therefore, after the user
 * task activates the {@code processInstanceVariables} filter does <b>not</b> find the task — the
 * pre-migration process variable was skipped and never back-filled (the #49963 gap).
 *
 * <p>The assertion is a <b>characterization</b> of current (buggy) behavior: it asserts the result
 * is empty. Once #49963 is fixed, flip that assertion to expect the task to be found.
 */
@Testcontainers
@ZeebeIntegration
public class UserTaskMigrationVariableSearchIT {

  private static final String PROCESS_V1_ID = "migration-no-user-task";
  private static final String PROCESS_V2_ID = "migration-with-user-task";
  private static final String WAIT_TASK_ID = "wait";
  private static final String USER_TASK_ID = "review";
  private static final String JOB_TYPE = "wait-job";
  private static final String INDEX_PREFIX = "usertaskmigrationvar";
  private static final String PROCESS_VARIABLE_NAME = "amount";
  private static final int PROCESS_VARIABLE_VALUE = 1000;
  private static final Duration TIMEOUT = Duration.ofSeconds(90);

  @TestZeebe(autoStart = false)
  private TestStandaloneApplication<?> app;

  private CamundaClient client;
  private GenericContainer<?> searchContainer;

  @BeforeEach
  void setup() {
    searchContainer =
        TestSearchContainers.createDefaultElasticsearchContainer()
            .withStartupTimeout(Duration.ofMinutes(5));
    searchContainer.start();
    final String esUrl =
        String.format(
            "http://%s:%d", searchContainer.getHost(), searchContainer.getMappedPort(9200));

    final TestCamundaApplication camundaApp =
        new TestCamundaApplication()
            .withAuthenticationMethod(AuthenticationMethod.BASIC)
            .withUnauthenticatedAccess();
    app = camundaApp;

    final var configurator = new MultiDbConfigurator(camundaApp);
    // secondary storage = Elasticsearch + the camunda-exporter writing into it
    configurator.configureElasticsearchSupport(esUrl, INDEX_PREFIX);

    // Enable the optimization this scenario depends on. The configurator above sets the
    // camunda-exporter args; we add the skip flag on top before the app is started.
    camundaApp.updateExporterArgs(
        CamundaExporter.class.getSimpleName().toLowerCase(),
        args -> args.put("skipVariableWriteWithoutUserTasks", true));

    app.start().awaitCompleteTopology();
    client = app.newClientBuilder().build();
  }

  @AfterEach
  void tearDown() {
    CloseHelper.quietCloseAll(client);
    if (app != null) {
      app.stop();
    }
    if (searchContainer != null) {
      searchContainer.stop();
    }
  }

  @Test
  void shouldNotFindTaskByProcessInstanceVariablesAfterMigration() {
    // given - v1 has NO user task: start -> service task (waits on a job) -> end
    final BpmnModelInstance v1 =
        Bpmn.createExecutableProcess(PROCESS_V1_ID)
            .startEvent()
            .serviceTask(WAIT_TASK_ID, t -> t.zeebeJobType(JOB_TYPE))
            .endEvent()
            .done();
    // v2 HAS a Camunda user task after the service task
    final BpmnModelInstance v2 =
        Bpmn.createExecutableProcess(PROCESS_V2_ID)
            .startEvent()
            .serviceTask(WAIT_TASK_ID, t -> t.zeebeJobType(JOB_TYPE))
            .userTask(USER_TASK_ID, u -> u.zeebeUserTask())
            .endEvent()
            .done();

    final var deployment =
        client
            .newDeployResourceCommand()
            .addProcessModel(v1, PROCESS_V1_ID + ".bpmn")
            .addProcessModel(v2, PROCESS_V2_ID + ".bpmn")
            .send()
            .join();
    final long targetProcessDefinitionKey =
        deployment.getProcesses().stream()
            .filter(p -> p.getBpmnProcessId().equals(PROCESS_V2_ID))
            .findFirst()
            .orElseThrow()
            .getProcessDefinitionKey();

    // start the v1 instance with a process-instance-scoped variable
    final long processInstanceKey =
        client
            .newCreateInstanceCommand()
            .bpmnProcessId(PROCESS_V1_ID)
            .latestVersion()
            .variables(Map.of(PROCESS_VARIABLE_NAME, PROCESS_VARIABLE_VALUE))
            .send()
            .join()
            .getProcessInstanceKey();

    // run up to the waiting service task - wait until it is active and exported before migrating
    await()
        .atMost(TIMEOUT)
        .untilAsserted(
            () ->
                assertThat(
                        client
                            .newElementInstanceSearchRequest()
                            .filter(
                                f ->
                                    f.processInstanceKey(processInstanceKey)
                                        .elementId(WAIT_TASK_ID)
                                        .state(ElementInstanceState.ACTIVE))
                            .send()
                            .join()
                            .items())
                    .hasSize(1));

    // when - migrate the active service task from v1 to v2's service task
    client
        .newMigrateProcessInstanceCommand(processInstanceKey)
        .migrationPlan(
            MigrationPlan.newBuilder()
                .withTargetProcessDefinitionKey(targetProcessDefinitionKey)
                .addMappingInstruction(WAIT_TASK_ID, WAIT_TASK_ID)
                .build())
        .send()
        .join();

    // complete the service-task job so the instance proceeds into the user task (v2)
    activateAndCompleteJobs(client, JOB_TYPE, "test-worker", 1);

    // wait until the migrated instance's user task has been created and exported
    await()
        .atMost(TIMEOUT)
        .untilAsserted(
            () ->
                assertThat(
                        client
                            .newUserTaskSearchRequest()
                            .filter(
                                f ->
                                    f.processInstanceKey(processInstanceKey)
                                        .state(UserTaskState.CREATED))
                            .send()
                            .join()
                            .items())
                    .hasSize(1));

    // then - the processInstanceVariables filter does NOT find the task today.
    // The pre-migration process variable was skipped from tasklist-task (no user task in v1) and
    // migration does not back-fill it. This is the #49963 gap.
    // CHARACTERIZATION: once #49963 is fixed, change this assertion to expect hasSize(1).
    final var byProcessVariable =
        client
            .newUserTaskSearchRequest()
            .filter(
                f ->
                    f.processInstanceKey(processInstanceKey)
                        .processInstanceVariables(
                            Map.of(PROCESS_VARIABLE_NAME, PROCESS_VARIABLE_VALUE)))
            .send()
            .join();
    assertThat(byProcessVariable.items())
        .describedAs(
            "process-instance variable filter cannot match a migrated instance's pre-migration "
                + "variables until #49963 is fixed")
        .isEmpty();
  }
}
