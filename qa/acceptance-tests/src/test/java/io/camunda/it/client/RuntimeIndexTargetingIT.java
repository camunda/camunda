/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.client;

import static io.camunda.qa.util.multidb.CamundaMultiDBExtension.TIMEOUT_DATA_AVAILABILITY;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.search.enums.ProcessInstanceState;
import io.camunda.client.api.search.enums.UserTaskState;
import io.camunda.client.api.statistics.response.ProcessElementStatistics;
import io.camunda.configuration.beans.SearchEngineConnectProperties;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.qa.util.multidb.MultiDbTestApplication;
import io.camunda.search.connect.configuration.ConnectConfiguration;
import io.camunda.search.connect.es.ElasticsearchConnector;
import io.camunda.search.connect.os.OpensearchConnector;
import io.camunda.webapps.schema.descriptors.IndexDescriptors;
import io.camunda.webapps.schema.descriptors.IndexTemplateDescriptor;
import io.camunda.webapps.schema.descriptors.template.ListViewTemplate;
import io.camunda.webapps.schema.descriptors.template.TaskTemplate;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import java.io.IOException;
import java.util.UUID;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.opensearch.client.opensearch._types.FieldValue;

/**
 * Verifies that searches which can only match running process instances read the runtime index
 * only, and that all other searches still read the archived indices.
 *
 * <p>The test copies the documents of one running process instance into a dated archive index. The
 * archiver leaves the same duplicate behind for a short time while it moves an instance. A search
 * that reads the archive index counts the instance twice, a search that reads only the runtime
 * index counts it once.
 */
@MultiDbTest
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "rdbms.*$")
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "AWS_OS")
class RuntimeIndexTargetingIT {

  @MultiDbTestApplication static final TestStandaloneBroker BROKER = new TestStandaloneBroker();

  private static final String ARCHIVE_DATE_SUFFIX = "2020-01-01";
  private static final String USER_TASK_ID = "task";

  private static CamundaClient camundaClient;

  private static long processDefinitionKey;
  private static long processInstanceKey;

  @BeforeAll
  static void setup() throws IOException {
    final var processId = "process-" + UUID.randomUUID();
    processDefinitionKey =
        camundaClient
            .newDeployResourceCommand()
            .addProcessModel(
                Bpmn.createExecutableProcess(processId)
                    .startEvent()
                    .userTask(USER_TASK_ID)
                    .zeebeUserTask()
                    .endEvent()
                    .done(),
                processId + ".bpmn")
            .send()
            .join()
            .getProcesses()
            .getFirst()
            .getProcessDefinitionKey();
    processInstanceKey =
        camundaClient
            .newCreateInstanceCommand()
            .processDefinitionKey(processDefinitionKey)
            .send()
            .join()
            .getProcessInstanceKey();

    Awaitility.await("until the process instance and its user task are exported")
        .atMost(TIMEOUT_DATA_AVAILABILITY)
        .ignoreExceptions()
        .untilAsserted(
            () -> {
              assertThat(countProcessInstances(null)).isOne();
              assertThat(countUserTasks(null)).isOne();
            });

    copyToArchiveIndex(ListViewTemplate.class, ListViewTemplate.PROCESS_INSTANCE_KEY);
    copyToArchiveIndex(TaskTemplate.class, TaskTemplate.PROCESS_INSTANCE_ID);
  }

  @Test
  void shouldSearchArchivedIndicesWithoutStateFilter() {
    // when
    final var processInstances = countProcessInstances(null);
    final var userTasks = countUserTasks(null);
    final var activeElementInstances = activeUserTaskElementInstances(null);

    // then
    assertThat(processInstances).isEqualTo(2);
    assertThat(userTasks).isEqualTo(2);
    assertThat(activeElementInstances).isEqualTo(2);
  }

  @Test
  void shouldSearchOnlyRuntimeIndexForActiveProcessInstances() {
    // when
    final var processInstances = countProcessInstances(ProcessInstanceState.ACTIVE);

    // then
    assertThat(processInstances).isOne();
  }

  @Test
  void shouldSearchOnlyRuntimeIndexForElementStatisticsOfActiveProcessInstances() {
    // when
    final var activeElementInstances = activeUserTaskElementInstances(ProcessInstanceState.ACTIVE);

    // then
    assertThat(activeElementInstances).isOne();
  }

  @Test
  void shouldSearchOnlyRuntimeIndexForCreatedUserTasks() {
    // when
    final var userTasks = countUserTasks(UserTaskState.CREATED);

    // then
    assertThat(userTasks).isOne();
  }

  private static long countProcessInstances(final ProcessInstanceState state) {
    return camundaClient
        .newProcessInstanceSearchRequest()
        .filter(
            f -> {
              f.processInstanceKey(processInstanceKey);
              if (state != null) {
                f.state(state);
              }
            })
        .send()
        .join()
        .page()
        .totalItems();
  }

  private static long countUserTasks(final UserTaskState state) {
    return camundaClient
        .newUserTaskSearchRequest()
        .filter(
            f -> {
              f.processInstanceKey(processInstanceKey);
              if (state != null) {
                f.state(state);
              }
            })
        .send()
        .join()
        .page()
        .totalItems();
  }

  private static long activeUserTaskElementInstances(final ProcessInstanceState state) {
    return camundaClient
        .newProcessDefinitionElementStatisticsRequest(processDefinitionKey)
        .filter(
            f -> {
              if (state != null) {
                f.state(state);
              }
            })
        .send()
        .join()
        .stream()
        .filter(statistics -> USER_TASK_ID.equals(statistics.getElementId()))
        .mapToLong(ProcessElementStatistics::getActive)
        .sum();
  }

  /**
   * Copies the documents of the process instance into a dated archive index. The index template
   * gives the new index the mappings and the alias of the runtime index.
   */
  private static void copyToArchiveIndex(
      final Class<? extends IndexTemplateDescriptor> templateClass, final String keyField)
      throws IOException {
    final ConnectConfiguration connectConfiguration =
        BROKER.bean(SearchEngineConnectProperties.class);
    final var isElasticsearch = connectConfiguration.getTypeEnum().isElasticSearch();
    final var runtimeIndex =
        new IndexDescriptors(connectConfiguration.getIndexPrefix(), isElasticsearch)
            .get(templateClass)
            .getFullQualifiedName();
    final var archiveIndex = runtimeIndex + ARCHIVE_DATE_SUFFIX;
    final var key = String.valueOf(processInstanceKey);

    final long copied;
    if (isElasticsearch) {
      final var client = new ElasticsearchConnector(connectConfiguration).createClient();
      try {
        copied =
            client
                .reindex(
                    r ->
                        r.source(
                                s ->
                                    s.index(runtimeIndex)
                                        .query(q -> q.term(t -> t.field(keyField).value(key))))
                            .dest(d -> d.index(archiveIndex))
                            .refresh(true))
                .total();
      } finally {
        client._transport().close();
      }
    } else {
      final var client = new OpensearchConnector(connectConfiguration).createClient();
      try {
        copied =
            client
                .reindex(
                    r ->
                        r.source(
                                s ->
                                    s.index(runtimeIndex)
                                        .query(
                                            q ->
                                                q.term(
                                                    t ->
                                                        t.field(keyField)
                                                            .value(FieldValue.of(key)))))
                            .dest(d -> d.index(archiveIndex))
                            .refresh(true))
                .total();
      } finally {
        client._transport().close();
      }
    }
    assertThat(copied).as("documents copied to %s", archiveIndex).isPositive();
  }
}
