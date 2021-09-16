/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.exporter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.camunda.zeebe.client.api.worker.JobWorker;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.intent.JobBatchIntent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.util.Map;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class ElasticsearchExporterJobRecordIT
    extends AbstractElasticsearchExporterIntegrationTestCase {

  private JobWorker jobWorker;

  @Before
  public void init() {
    elastic.start();

    configuration = getDefaultConfiguration();
    esClient = createElasticsearchClient(configuration);

    exporterBrokerRule.configure("es", ElasticsearchExporter.class, configuration);
    exporterBrokerRule.start();
  }

  @After
  public void cleanUp() {
    if (jobWorker != null) {
      jobWorker.close();
    }
  }

  @Test
  public void shouldExportJobRecordWithCustomHeaders() {
    // when
    exporterBrokerRule.deployProcess(
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .serviceTask(
                "task",
                t -> t.zeebeJobType("test").zeebeTaskHeader("x", "1").zeebeTaskHeader("y", "2"))
            .endEvent()
            .done(),
        "process.bpmn");

    final var processInstanceKey = exporterBrokerRule.createProcessInstance("process", Map.of());

    // then
    await("index templates need to be created").untilAsserted(this::assertIndexSettings);
    final var jobCreated =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    assertRecordExported(jobCreated);
  }

  @Test
  public void shouldExportJobRecordWithOverlappingCustomHeaders() {
    // when
    exporterBrokerRule.deployProcess(
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .serviceTask(
                "task",
                t -> t.zeebeJobType("test").zeebeTaskHeader("x", "1").zeebeTaskHeader("x.y", "2"))
            .endEvent()
            .done(),
        "process.bpmn");

    final var processInstanceKey = exporterBrokerRule.createProcessInstance("process", Map.of());

    // then
    await("index templates need to be created").untilAsserted(this::assertIndexSettings);
    final var jobCreated =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    assertRecordExported(jobCreated);
  }

  @Test
  public void shouldExportJobBatchRecordWithOverlappingCustomHeaders() {
    // when
    exporterBrokerRule.deployProcess(
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .serviceTask(
                "task",
                t -> t.zeebeJobType("test").zeebeTaskHeader("x", "1").zeebeTaskHeader("x.y", "2"))
            .endEvent()
            .done(),
        "process.bpmn");

    final var processInstanceKey = exporterBrokerRule.createProcessInstance("process", Map.of());

    await("index templates need to be created").untilAsserted(this::assertIndexSettings);
    final var jobCreated =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    jobWorker =
        exporterBrokerRule.createJobWorker(
            "test", ((client, job) -> client.newCompleteCommand(job.getKey()).send()));

    // then
    final var jobBatchActivated =
        RecordingExporter.jobBatchRecords(JobBatchIntent.ACTIVATED).withType("test").getFirst();

    assertThat(jobBatchActivated.getValue().getJobKeys()).contains(jobCreated.getKey());
    assertRecordExported(jobBatchActivated);
  }
}
