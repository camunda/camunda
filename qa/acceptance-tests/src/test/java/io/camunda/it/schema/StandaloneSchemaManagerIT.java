/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.schema;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.it.schema.strategy.ElasticsearchBackendStrategy;
import io.camunda.it.schema.strategy.OpenSearchBackendStrategy;
import io.camunda.it.schema.strategy.SearchBackendStrategy;
import io.camunda.qa.util.cluster.TestCamundaApplication;
import io.camunda.qa.util.cluster.TestStandaloneSchemaManager;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import java.time.Duration;
import java.util.Objects;
import java.util.stream.Stream;
import org.awaitility.Awaitility;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

@ZeebeIntegration
final class StandaloneSchemaManagerIT {

  @TestZeebe(autoStart = false)
  final TestStandaloneSchemaManager schemaManager = new TestStandaloneSchemaManager();

  @TestZeebe(autoStart = false)
  final TestCamundaApplication camunda = new TestCamundaApplication();

  static Stream<SearchBackendStrategy> strategies() {
    return Stream.of(new ElasticsearchBackendStrategy(), new OpenSearchBackendStrategy());
  }

  @ParameterizedTest
  @MethodSource("strategies")
  void canUseCamunda(final SearchBackendStrategy strategy) throws Exception {
    try (strategy) {
      initialize(strategy);

      final long processInstanceKey;
      try (final var client = camunda.newClientBuilder().build()) {
        client
            .newDeployResourceCommand()
            .addProcessModel(
                Bpmn.createExecutableProcess("process-with-user-task")
                    .startEvent()
                    .userTask("user-task")
                    .zeebeUserTask()
                    .endEvent()
                    .done(),
                "process-with-user-task.bpmn")
            .send()
            .join();

        processInstanceKey =
            client
                .newCreateInstanceCommand()
                .bpmnProcessId("process-with-user-task")
                .latestVersion()
                .send()
                .join()
                .getProcessInstanceKey();

        final var userTaskKey =
            Awaitility.await("user task created")
                .atMost(Duration.ofSeconds(60))
                .ignoreExceptions()
                .until(
                    () ->
                        client
                            .newUserTaskSearchRequest()
                            .send()
                            .join()
                            .items()
                            .getFirst()
                            .getUserTaskKey(),
                    Objects::nonNull);

        client.newAssignUserTaskCommand(userTaskKey).assignee("demo").send().join();
        client.newCompleteUserTaskCommand(userTaskKey).send().join();

        Awaitility.await("process instance completed")
            .atMost(Duration.ofSeconds(60))
            .ignoreExceptions()
            .untilAsserted(
                () -> {
                  final var result =
                      client.newProcessInstanceGetRequest(processInstanceKey).send().join();
                  assertThat(result.getEndDate()).isNotNull();
                });
      }
      Awaitility.await("Zeebe records templates exist and records are exported to Elasticsearch")
          .atMost(Duration.ofSeconds(5))
          .ignoreExceptions()
          .untilAsserted(
              () -> {
                assertThat(strategy.countTemplates("zeebe-record*")).isGreaterThan(0);
                assertThat(strategy.countDocuments("zeebe-record*")).isGreaterThan(0);
              });
    }
  }

  @ParameterizedTest
  @MethodSource("strategies")
  void canArchiveProcessInstances(final SearchBackendStrategy strategy) throws Exception {
    try (strategy) {
      initialize(strategy);
      final long processInstanceKey;
      try (final var client = camunda.newClientBuilder().build()) {
        client
            .newDeployResourceCommand()
            .addProcessModel(
                Bpmn.createExecutableProcess("simple-process").startEvent().endEvent().done(),
                "simple-process.bpmn")
            .send()
            .join();

        processInstanceKey =
            client
                .newCreateInstanceCommand()
                .bpmnProcessId("simple-process")
                .latestVersion()
                .send()
                .join()
                .getProcessInstanceKey();
      }
      // then - process instance is archived
      Awaitility.await("process instance should be archived")
          .atMost(Duration.ofSeconds(60))
          .pollInterval(Duration.ofSeconds(1))
          .ignoreExceptions()
          .untilAsserted(
              () -> {
                // Verify that instance is in archived index
                assertThat(
                        strategy.searchByKey("operate-list-view-8.3.0_*-*-*", processInstanceKey))
                    .isEqualTo(1);

                // Verify it's not in the live index
                assertThat(strategy.searchByKey("operate-list-view-*_", processInstanceKey))
                    .isEqualTo(0);
              });
    }
  }

  private void initialize(final SearchBackendStrategy strategy) throws Exception {
    strategy.startContainer();
    strategy.createAdminClient();
    strategy.createSchema();
    strategy.configureStandaloneSchemaManager(schemaManager);
    strategy.configureCamundaApplication(camunda);
    schemaManager.start();
    camunda.start();
  }
}
