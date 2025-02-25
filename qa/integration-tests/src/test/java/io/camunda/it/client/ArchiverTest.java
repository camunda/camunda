/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.client;

import static org.assertj.core.api.Assertions.assertThat;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.cat.indices.IndicesRecord;
import co.elastic.clients.elasticsearch.ilm.MoveToStepRequest;
import io.camunda.application.commons.search.SearchClientDatabaseConfiguration.SearchClientProperties;
import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.it.utils.CamundaMultiDBExtension;
import io.camunda.search.connect.es.ElasticsearchConnector;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneApplication;
import io.camunda.zeebe.test.util.Strings;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ArchiverTest {
  private static final Logger LOGGER = LoggerFactory.getLogger(ArchiverTest.class);
  private static final String SERVICE_TASK = "taskA";
  private static final BpmnModelInstance PROCESS =
      Bpmn.createExecutableProcess("process")
          .startEvent()
          .serviceTask(SERVICE_TASK, b -> b.zeebeJobType(SERVICE_TASK))
          .endEvent()
          .done();

  private static TestStandaloneApplication<?> camunda;

  @RegisterExtension
  private static final CamundaMultiDBExtension CAMUNDA_MULTI_DB_EXTENSION =
      new CamundaMultiDBExtension(
          application -> {
            final var exporterCfg =
                application.brokerConfig().getExporters().get("CamundaExporter");
            final var args = new HashMap<>(exporterCfg.getArgs());
            final var archiver =
                Map.of(
                    "waitPeriodBeforeArchiving",
                    "1h",
                    "rolloverBatchSize",
                    1,
                    "delayBetweenRuns",
                    100,
                    "rolloverInterval",
                    "1d",
                    "elsRolloverDateFormat",
                    "yyyy-MM-dd",
                    "retention",
                    Map.of("enabled", true, "minimumAge", "2h", "policyName", "test-policy"));

            args.put("archiver", archiver);
            application.withExporter("CamundaExporter", exporter -> exporter.setArgs(args));
            camunda = application;
          });

  // No need to close it, it's closed by the extension
  private static CamundaClient client;
  private static final Duration TIMEOUT = Duration.ofSeconds(30);
  private static final String PROCESS_ID = Strings.newRandomValidBpmnId();
  private static final String TASK_TYPE = Strings.newRandomValidBpmnId();

  @BeforeAll
  static void beforeAll() {
    client = CAMUNDA_MULTI_DB_EXTENSION.createCamundaClient();
  }

  @Test
  public void shouldArchiveOnlyCompletedProcesses() throws InterruptedException {
    // given
    final var twoHoursAgo = Instant.now().minus(2, ChronoUnit.HOURS);
    pinClock(twoHoursAgo);
    final var processKey = deployOneTaskProcess();
    final var pis =
        List.of(
            createProcessInstance(processKey),
            createProcessInstance(processKey),
            createProcessInstance(processKey),
            createProcessInstance(processKey));
    Awaitility.await("Until all process instances are visible")
        .untilAsserted(() -> assertProcessInstancesAreVisible(pis));
    final List<Long> archivedPis = new ArrayList<>();
    final List<Long> nonArchivedPis = new ArrayList<>();

    // when
    activateJobs(1)
        .forEach(
            job -> {
              archivedPis.add(job.getProcessInstanceKey());
              client.newCompleteCommand(job.getKey()).send().join();
            });
    LOGGER.debug("Process instance {} completed", archivedPis);

    pinClock(twoHoursAgo.plus(30, ChronoUnit.MINUTES));
    activateJobs(2)
        .forEach(
            job -> {
              nonArchivedPis.add(job.getProcessInstanceKey());
              client.newCompleteCommand(job.getKey()).send().join();
            });
    for (final var pi : pis) {
      if (!archivedPis.contains(pi) & !nonArchivedPis.contains(pi)) {
        nonArchivedPis.add(pi);
      }
    }
    LOGGER.debug("Process instance {} completed", nonArchivedPis);

    // the archiver has archived the instance
    // then the completed instance is not returned from queries
    Awaitility.await("Until the completed instance is archived")
        .atMost(TIMEOUT)
        // give some time to the archiver to add the indices to ILM policies
        .pollInterval(Duration.ofMillis(200))
        .pollDelay(Duration.ofSeconds(1))
        .untilAsserted(() -> assertProcessInstancesArchived(twoHoursAgo, archivedPis));
    // and the active instances have not been archived
    Awaitility.await("Active instances are not archived")
        .atMost(TIMEOUT)
        .untilAsserted(
            () -> {
              final var processInstanceList =
                  client.newProcessInstanceQuery().send().join().items();
              assertThat(processInstanceList).hasSize(nonArchivedPis.size());
            });
  }

  private void pinClock(final Instant instant) {
    client.newClockPinCommand().time(instant).send().join();
  }

  private void assertProcessInstancesArchived(final Instant endTime, final List<Long> archivePiKeys)
      throws IOException {
    forceDeletionOfArchiverIndices(endTime);

    final var archivedPis =
        client
            .newProcessInstanceQuery()
            .filter(f -> f.processInstanceKey(p -> p.in(archivePiKeys)))
            .send()
            .join();

    assertThat(archivedPis.items())
        .as("finished PIs are not visible anymore after archiving")
        .isEmpty();
  }

  private void forceDeletionOfArchiverIndices(final Instant endTime) throws IOException {
    final var properties = camunda.bean(SearchClientProperties.class);
    final var connector = new ElasticsearchConnector(properties);
    final var esClient = connector.createClient();
    final var date = new SimpleDateFormat("yyyy-MM-dd").format(Date.from(endTime));
    final var indices =
        esClient.cat().indices().valueBody().stream()
            .map(IndicesRecord::index)
            .filter(Objects::nonNull)
            .filter(name -> name.endsWith(date))
            .toList();

    for (final var index : indices) {
      forceDeletionOfIndexViaLifecycle(esClient, index);
    }

    // force refresh to make changes visible
    esClient.indices().refresh();
  }

  private void forceDeletionOfIndexViaLifecycle(
      final ElasticsearchClient esClient, final String index) throws IOException {
    final var request =
        new MoveToStepRequest.Builder()
            .index(index)
            .currentStep(s -> s.phase("new").action("complete").name("complete"))
            .nextStep(s -> s.name("delete").action("delete").phase("delete"))
            .build();
    try {
      esClient.ilm().moveToStep(request);
    } catch (final Exception e) {
      // it's possible this fails if we pick up the index right after creation, but before it
      // was updated with a lifecycle policy; that's OK, it'll eventually work, just log the
      // exception
      LOGGER.warn("Failed to force deletion of archived index", e);
    }
  }

  private void assertProcessInstancesAreVisible(final List<Long> keys) {
    final var result =
        client
            .newProcessInstanceQuery()
            .filter(f -> f.processInstanceKey(k -> k.in(keys)))
            .send()
            .join();
    assertThat(result.items()).hasSameSizeAs(keys);
  }

  private List<ActivatedJob> activateJobs(final int count) {
    return Awaitility.await("jobs are activated")
        .atMost(TIMEOUT)
        .until(
            () ->
                client
                    .newActivateJobsCommand()
                    .jobType(TASK_TYPE)
                    .maxJobsToActivate(count)
                    .send()
                    .join()
                    .getJobs(),
            list -> list.size() == count);
  }

  private long createProcessInstance(final long processKey) {
    return client
        .newCreateInstanceCommand()
        .processDefinitionKey(processKey)
        .send()
        .join()
        .getProcessInstanceKey();
  }

  private long deployOneTaskProcess() {
    final var process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .serviceTask(TASK_TYPE, b -> b.zeebeJobType(TASK_TYPE))
            .endEvent()
            .done();
    return client
        .newDeployResourceCommand()
        .addProcessModel(process, "process.bpmn")
        .send()
        .join()
        .getProcesses()
        .getFirst()
        .getProcessDefinitionKey();
  }
}
