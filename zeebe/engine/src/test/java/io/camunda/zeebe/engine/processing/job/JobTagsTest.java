/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.job;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.value.JobRecordValue;
import io.camunda.zeebe.test.util.Strings;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public final class JobTagsTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  private static BpmnModelInstance processWithServiceTask(final String processId) {
    return Bpmn.createExecutableProcess(processId)
        .startEvent()
        .serviceTask("task", t -> t.zeebeJobType(Strings.newRandomValidBpmnId()))
        .endEvent()
        .done();
  }

  @Test
  public void shouldInheritTagsFromOwningProcessInstance() {
    // given
    final String processId = Strings.newRandomValidBpmnId();
    ENGINE.deployment().withXmlResource(processWithServiceTask(processId)).deploy();

    // when
    final long processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(processId)
            .withTags("businessKey:1234", "priority:high")
            .create();

    // then
    final Record<JobRecordValue> jobCreated =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    Assertions.assertThat(jobCreated.getValue()).hasOnlyTags("businessKey:1234", "priority:high");
  }

  @Test
  public void shouldHaveNoTagsWhenProcessInstanceHasNone() {
    // given - the engine reuses one job record for every job it creates, so a job with tags is
    // created first to catch tags that leak into the next job
    final String processId = Strings.newRandomValidBpmnId();
    ENGINE.deployment().withXmlResource(processWithServiceTask(processId)).deploy();
    final long taggedProcessInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(processId).withTags("priority:high").create();
    RecordingExporter.jobRecords(JobIntent.CREATED)
        .withProcessInstanceKey(taggedProcessInstanceKey)
        .getFirst();

    // when
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId).create();

    // then
    final Record<JobRecordValue> jobCreated =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    Assertions.assertThat(jobCreated.getValue()).hasNoTags();
  }
}
