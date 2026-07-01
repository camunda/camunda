/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.variable.mapping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.VariableIntent;
import io.camunda.zeebe.test.util.MsgPackUtil;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.UUID;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

/** Reproduces https://github.com/camunda/camunda/issues/19441. */
public final class JobCompletionVariableScopeTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  private static final String PROCESS_ID = "process";
  private static final String SUBPROCESS_ID = "subprocess";
  private static final String TASK_ID = "task";

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  private String jobType;

  @Before
  public void init() {
    jobType = UUID.randomUUID().toString();
  }

  @Test
  public void shouldNotCreateGlobalVariableWhenCompletingJobWithLocalScopedVariable() {
    // given – a subprocess whose input mapping creates a local variable x=1, with a nested task
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .subProcess(
                    SUBPROCESS_ID,
                    s ->
                        s.zeebeInputExpression("1", "x")
                            .embeddedSubProcess()
                            .startEvent()
                            .serviceTask(TASK_ID, t -> t.zeebeJobType(jobType))
                            .endEvent())
                .endEvent()
                .done())
        .deploy();

    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    final long subprocessScopeKey =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementId(SUBPROCESS_ID)
            .getFirst()
            .getKey();

    // when – the job is completed with the same variable name and value that already exists locally
    ENGINE
        .job()
        .ofInstance(processInstanceKey)
        .withType(jobType)
        .withVariables(MsgPackUtil.asMsgPack("{'x': 1}"))
        .complete();

    // then – `x` appears exactly once: a single CREATED at the subprocess scope.
    // No CREATED at the process-instance (root) scope, and no spurious UPDATED.
    // `limitToProcessInstance` terminates the stream once the instance completes, so the
    // assertion sees every variable record the instance produced.
    assertThat(
            RecordingExporter.records()
                .limitToProcessInstance(processInstanceKey)
                .variableRecords()
                .withName("x"))
        .extracting(Record::getIntent, r -> r.getValue().getScopeKey())
        .containsExactly(tuple(VariableIntent.CREATED, subprocessScopeKey));
  }
}
