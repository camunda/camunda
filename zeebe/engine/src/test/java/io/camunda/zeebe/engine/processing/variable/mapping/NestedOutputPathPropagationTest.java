/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.variable.mapping;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;
import io.camunda.zeebe.protocol.record.value.VariableRecordValue;
import io.camunda.zeebe.test.util.JsonUtil;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.List;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

/**
 * Runtime coverage for https://github.com/camunda/camunda/issues/35251: an output mapping targeting
 * a nested path propagates only the mapped paths, merged into what the parent scope already holds.
 */
public final class NestedOutputPathPropagationTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Test
  public void shouldKeepParentSiblingAndDropLocalSibling() {
    // given: the parent holds a={c:2}; the sub-process creates its own a={p:0} locally
    final var processId = "nested-output-discriminator";
    final var process =
        Bpmn.createExecutableProcess(processId)
            .startEvent()
            .subProcess(
                "sp",
                sp ->
                    sp.zeebeInputExpression("={p:0}", "a")
                        .zeebeOutputExpression("1", "a.b")
                        .embeddedSubProcess()
                        .startEvent()
                        .endEvent())
            .endEvent()
            .done();
    ENGINE.deployment().withXmlResource(process).deploy();

    // when
    final long processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(processId)
            .withVariables("{\"a\":{\"c\":2}}")
            .create();

    // then: 'c' belongs to the parent and survives; 'p' was only local and does not propagate
    JsonUtil.assertEquality(lastRootValueOf(processInstanceKey, "a"), "{\"c\":2,\"b\":1}");
  }

  @Test
  public void shouldPropagateOnlyMappedBranchesOfAUserTaskResult() {
    // given: the issue's shape - a form writes three branches, only two are mapped
    final var processId = "nested-output-user-task";
    final var process =
        Bpmn.createExecutableProcess(processId)
            .startEvent()
            .userTask("task")
            .zeebeUserTask()
            .zeebeOutputExpression("processData.humanTask.outcome", "processData.humanTask.outcome")
            .zeebeOutputExpression("processData.fault.errors", "processData.fault.errors")
            .endEvent()
            .done();
    ENGINE.deployment().withXmlResource(process).deploy();
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId).create();
    final long userTaskKey =
        RecordingExporter.userTaskRecords(UserTaskIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst()
            .getKey();

    // when
    ENGINE
        .userTask()
        .withKey(userTaskKey)
        .withVariables(
            "{\"processData\":{\"humanTask\":{\"outcome\":\"approved\"},"
                + "\"fault\":{\"errors\":[\"e1\"]},\"test\":{\"value\":\"junk\"}}}")
        .complete();

    // then: both mapped branches propagate, the unmapped 'test' branch does not
    JsonUtil.assertEquality(
        lastRootValueOf(processInstanceKey, "processData"),
        "{\"humanTask\":{\"outcome\":\"approved\"},\"fault\":{\"errors\":[\"e1\"]}}");
  }

  @Test
  public void shouldMergeAtEveryLevelOfADeepTarget() {
    // given: the parent holds siblings at two levels; the sub-process adds a local-only branch
    final var processId = "nested-output-deep";
    final var process =
        Bpmn.createExecutableProcess(processId)
            .startEvent()
            .subProcess(
                "sp",
                sp ->
                    sp.zeebeInputExpression("={junk:0}", "a")
                        .zeebeOutputExpression("1", "a.b.c")
                        .embeddedSubProcess()
                        .startEvent()
                        .endEvent())
            .endEvent()
            .done();
    ENGINE.deployment().withXmlResource(process).deploy();

    // when
    final long processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(processId)
            .withVariables("{\"a\":{\"b\":{\"keep\":1},\"top\":2}}")
            .create();

    // then: 'keep' and 'top' survive, the local 'junk' never enters
    JsonUtil.assertEquality(
        lastRootValueOf(processInstanceKey, "a"), "{\"b\":{\"keep\":1,\"c\":1},\"top\":2}");
  }

  @Test
  public void shouldNotChangeMultiInstanceOutputMappingPropagation() {
    // given: an inner multi-instance activity merges into its OWN scope, so seed and merge target
    // were already the same - this pins that the fix leaves that path alone
    final var processId = "nested-output-multi-instance";
    final var jobType = "nestedOutputMiJob";
    final var process =
        Bpmn.createExecutableProcess(processId)
            .startEvent()
            .serviceTask(
                "task",
                t ->
                    t.zeebeJobType(jobType)
                        .zeebeInputExpression("={p:0}", "a")
                        .zeebeOutputExpression("1", "a.b")
                        .multiInstance(
                            mi ->
                                mi.zeebeInputCollectionExpression("=[1]")
                                    .zeebeInputElement("item")))
            .endEvent()
            .done();
    ENGINE.deployment().withXmlResource(process).deploy();
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId).create();

    // when
    ENGINE.jobs().withType(jobType).activate();
    ENGINE.job().ofInstance(processInstanceKey).withType(jobType).complete();

    // then: 'a' stays on the inner instance with its local sibling, and never reaches the root
    final var rootValues =
        RecordingExporter.records()
            .limitToProcessInstance(processInstanceKey)
            .variableRecords()
            .withScopeKey(processInstanceKey)
            .withName("a")
            .asList();
    assertThat(rootValues).isEmpty();

    final var innerValues =
        RecordingExporter.records()
            .limitToProcessInstance(processInstanceKey)
            .variableRecords()
            .withName("a")
            .asList();
    assertThat(innerValues).isNotEmpty();
    JsonUtil.assertEquality(innerValues.getLast().getValue().getValue(), "{\"p\":0,\"b\":1}");
  }

  @Test
  public void shouldPropagateOnlyMappedBranchesFromACallActivity() {
    // given: the child produces a two-branch 'data'; the call activity maps only one branch
    final var childId = "nested-output-call-child";
    final var parentId = "nested-output-call-parent";
    final var child =
        Bpmn.createExecutableProcess(childId)
            .startEvent()
            .intermediateThrowEvent(
                "produce", e -> e.zeebeOutputExpression("={wanted:1,unwanted:2}", "data"))
            .endEvent()
            .done();
    final var parent =
        Bpmn.createExecutableProcess(parentId)
            .startEvent()
            .callActivity(
                "call",
                c -> c.zeebeProcessId(childId).zeebeOutputExpression("data.wanted", "data.wanted"))
            .endEvent()
            .done();
    ENGINE.deployment().withXmlResource(child).withXmlResource(parent).deploy();

    // when
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(parentId).create();

    // then: the child's unmapped branch does not reach the parent process
    JsonUtil.assertEquality(lastRootValueOf(processInstanceKey, "data"), "{\"wanted\":1}");
  }

  private static String lastRootValueOf(final long processInstanceKey, final String name) {
    final List<Record<VariableRecordValue>> records =
        RecordingExporter.records()
            .limitToProcessInstance(processInstanceKey)
            .variableRecords()
            .withScopeKey(processInstanceKey)
            .withName(name)
            .asList();
    assertThat(records)
        .describedAs("expected a '%s' variable at the root scope", name)
        .isNotEmpty();
    return records.getLast().getValue().getValue();
  }
}
