/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.exporter;

import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.client.api.worker.JobWorker;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.protocol.record.intent.JobBatchIntent;
import io.zeebe.protocol.record.intent.JobIntent;
import io.zeebe.test.util.record.RecordingExporter;
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
    exporterBrokerRule.deployWorkflow(
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .serviceTask(
                "task",
                t -> t.zeebeJobType("test").zeebeTaskHeader("x", "1").zeebeTaskHeader("y", "2"))
            .endEvent()
            .done(),
        "process.bpmn");

    final var workflowInstanceKey = exporterBrokerRule.createWorkflowInstance("process", Map.of());

    // then
    final var jobCreated =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withWorkflowInstanceKey(workflowInstanceKey)
            .getFirst();

    assertRecordExported(jobCreated);
  }

  @Test
  public void shouldExportJobRecordWithOverlappingCustomHeaders() {
    // when
    exporterBrokerRule.deployWorkflow(
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .serviceTask(
                "task",
                t -> t.zeebeJobType("test").zeebeTaskHeader("x", "1").zeebeTaskHeader("x.y", "2"))
            .endEvent()
            .done(),
        "process.bpmn");

    final var workflowInstanceKey = exporterBrokerRule.createWorkflowInstance("process", Map.of());

    // then
    final var jobCreated =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withWorkflowInstanceKey(workflowInstanceKey)
            .getFirst();

    assertRecordExported(jobCreated);
  }

  @Test
  public void shouldExportJobBatchRecordWithOverlappingCustomHeaders() {
    // when
    exporterBrokerRule.deployWorkflow(
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .serviceTask(
                "task",
                t -> t.zeebeJobType("test").zeebeTaskHeader("x", "1").zeebeTaskHeader("x.y", "2"))
            .endEvent()
            .done(),
        "process.bpmn");

    final var workflowInstanceKey = exporterBrokerRule.createWorkflowInstance("process", Map.of());

    final var jobCreated =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withWorkflowInstanceKey(workflowInstanceKey)
            .getFirst();

    jobWorker =
        exporterBrokerRule.createJobWorker(
            "test", ((client, job) -> client.newCompleteCommand(job).send()));

    // then
    final var jobBatchActivated =
        RecordingExporter.jobBatchRecords(JobBatchIntent.ACTIVATED).withType("test").getFirst();

    assertThat(jobBatchActivated.getValue().getJobKeys()).contains(jobCreated.getKey());
    assertRecordExported(jobBatchActivated);
  }
}
