/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.bpmn.activity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.builder.AdHocSubProcessBuilder;
import io.camunda.zeebe.protocol.impl.record.value.job.JobResult;
import io.camunda.zeebe.protocol.impl.record.value.job.JobResultActivateElement;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.JobRecordValue.JobResultActivateElementValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

/**
 * Covers joining gateways inside an ad-hoc sub-process that merge the paths of different ad-hoc
 * activities (shared joins), in combination with other engine features.
 */
public final class AdHocSubProcessSharedJoinTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  private static final String AD_HOC_SUB_PROCESS_ELEMENT_ID = "ad-hoc";

  @Rule public final RecordingExporterTestWatcher watcher = new RecordingExporterTestWatcher();

  private final String processId = "process-" + UUID.randomUUID();
  private final String jobType = "job-" + UUID.randomUUID();

  private long deployAndCreate(final Consumer<AdHocSubProcessBuilder> modifier) {
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(processId)
            .startEvent()
            .adHocSubProcess(AD_HOC_SUB_PROCESS_ELEMENT_ID, modifier)
            .endEvent()
            .done();
    ENGINE.deployment().withXmlResource(process).deploy();
    return ENGINE.processInstance().ofBpmnProcessId(processId).create();
  }

  private void awaitTokenAtJoin(final long processInstanceKey) {
    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.SEQUENCE_FLOW_TAKEN)
        .withProcessInstanceKey(processInstanceKey)
        .filter(r -> r.getValue().getElementId().startsWith("toJoin"))
        .await();
  }

  private List<Record<ProcessInstanceRecordValue>> recordsUntilCompleted(
      final long processInstanceKey) {
    return RecordingExporter.processInstanceRecords()
        .withProcessInstanceKey(processInstanceKey)
        .limitToProcessInstanceCompleted()
        .toList();
  }

  @Test
  public void shouldTerminateWhenProcessInstanceIsCanceledWhileTokenWaitsAtJoin() {
    // given
    final long processInstanceKey =
        deployAndCreate(
            adHocSubProcess -> {
              adHocSubProcess.zeebeActiveElementsCollectionExpression("[\"task1\",\"task2\"]");
              adHocSubProcess
                  .manualTask("task1")
                  .sequenceFlowId("toJoin1")
                  .parallelGateway("join")
                  .manualTask("task3");
              adHocSubProcess.serviceTask("task2", t -> t.zeebeJobType(jobType)).connectTo("join");
            });
    awaitTokenAtJoin(processInstanceKey);

    // when
    ENGINE.processInstance().withInstanceKey(processInstanceKey).cancel();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_TERMINATED)
                .withProcessInstanceKey(processInstanceKey)
                .withElementType(BpmnElementType.PROCESS)
                .exists())
        .isTrue();
  }

  @Test
  public void shouldCancelWaitingTokenWhenCompletionConditionCancelsRemainingInstances() {
    // given
    final long processInstanceKey =
        deployAndCreate(
            adHocSubProcess -> {
              adHocSubProcess
                  .zeebeActiveElementsCollectionExpression("[\"task1\",\"task2\",\"task4\"]")
                  .completionCondition("=task4Done = true")
                  .cancelRemainingInstances(true);
              adHocSubProcess
                  .manualTask("task1")
                  .sequenceFlowId("toJoin1")
                  .parallelGateway("join")
                  .manualTask("task3");
              adHocSubProcess.serviceTask("task2", t -> t.zeebeJobType(jobType)).connectTo("join");
              adHocSubProcess
                  .serviceTask("task4", t -> t.zeebeJobType(jobType + "-4"))
                  .zeebeOutputExpression("true", "task4Done");
            });
    awaitTokenAtJoin(processInstanceKey);

    // when
    ENGINE.job().ofInstance(processInstanceKey).withType(jobType + "-4").complete();

    // then
    assertThat(recordsUntilCompleted(processInstanceKey))
        .extracting(r -> r.getValue().getElementId(), Record::getIntent)
        .contains(
            tuple("task2", ProcessInstanceIntent.ELEMENT_TERMINATED),
            tuple(AD_HOC_SUB_PROCESS_ELEMENT_ID, ProcessInstanceIntent.ELEMENT_COMPLETED))
        .doesNotContain(tuple("join", ProcessInstanceIntent.ELEMENT_ACTIVATED));
  }

  @Test
  public void shouldKeepWaitingTokenWhenCompletionConditionDoesNotCancelRemainingInstances() {
    // given
    final long processInstanceKey =
        deployAndCreate(
            adHocSubProcess -> {
              adHocSubProcess
                  .zeebeActiveElementsCollectionExpression("[\"task1\",\"task2\"]")
                  .completionCondition("=true")
                  .cancelRemainingInstances(false);
              adHocSubProcess
                  .manualTask("task1")
                  .sequenceFlowId("toJoin1")
                  .parallelGateway("join")
                  .manualTask("task3");
              adHocSubProcess.serviceTask("task2", t -> t.zeebeJobType(jobType)).connectTo("join");
            });
    awaitTokenAtJoin(processInstanceKey);

    // when
    ENGINE.job().ofInstance(processInstanceKey).withType(jobType).complete();

    // then
    assertThat(recordsUntilCompleted(processInstanceKey))
        .extracting(r -> r.getValue().getElementId(), Record::getIntent)
        .containsSubsequence(
            tuple("join", ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple("task3", ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(AD_HOC_SUB_PROCESS_ELEMENT_ID, ProcessInstanceIntent.ELEMENT_COMPLETED));
  }

  @Test
  public void shouldJoinPathThroughExclusiveGateway() {
    // given
    final long processInstanceKey =
        deployAndCreate(
            adHocSubProcess -> {
              adHocSubProcess.zeebeActiveElementsCollectionExpression("[\"task1\",\"task2\"]");
              adHocSubProcess
                  .manualTask("task1")
                  .exclusiveGateway("xor")
                  .defaultFlow()
                  .parallelGateway("join")
                  .manualTask("task3");
              adHocSubProcess.manualTask("task2").connectTo("join");
            });

    // then
    assertThat(recordsUntilCompleted(processInstanceKey))
        .extracting(r -> r.getValue().getElementId(), Record::getIntent)
        .containsSubsequence(
            tuple("join", ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple("task3", ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(processId, ProcessInstanceIntent.ELEMENT_COMPLETED));
  }

  @Test
  public void shouldJoinPathThroughBoundaryEvent() {
    // given
    final long processInstanceKey =
        deployAndCreate(
            adHocSubProcess -> {
              adHocSubProcess.zeebeActiveElementsCollectionExpression("[\"task1\",\"task2\"]");
              final var task1 = adHocSubProcess.serviceTask("task1", t -> t.zeebeJobType(jobType));
              task1.manualTask("afterTask1");
              task1
                  .boundaryEvent("error", b -> b.error("err"))
                  .parallelGateway("join")
                  .manualTask("task3");
              adHocSubProcess.manualTask("task2").connectTo("join");
            });

    // when
    ENGINE.job().ofInstance(processInstanceKey).withType(jobType).withErrorCode("err").throwError();

    // then
    assertThat(recordsUntilCompleted(processInstanceKey))
        .extracting(r -> r.getValue().getElementId(), Record::getIntent)
        .containsSubsequence(
            tuple("error", ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple("join", ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple("task3", ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(processId, ProcessInstanceIntent.ELEMENT_COMPLETED));
  }

  @Test
  public void shouldChainSharedJoins() {
    // given
    final long processInstanceKey =
        deployAndCreate(
            adHocSubProcess -> {
              adHocSubProcess.zeebeActiveElementsCollectionExpression(
                  "[\"task1\",\"task2\",\"task3\"]");
              adHocSubProcess
                  .manualTask("task1")
                  .parallelGateway("join1")
                  .parallelGateway("join2")
                  .manualTask("task4");
              adHocSubProcess.manualTask("task2").connectTo("join1");
              adHocSubProcess.manualTask("task3").connectTo("join2");
            });

    // then
    final var records = recordsUntilCompleted(processInstanceKey);
    assertThat(records)
        .extracting(r -> r.getValue().getElementId(), Record::getIntent)
        .contains(
            tuple("join1", ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple("join2", ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple("task4", ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(processId, ProcessInstanceIntent.ELEMENT_COMPLETED));
  }

  @Test
  public void shouldContinueEachOutgoingFlowOfSharedJoinInItsOwnInnerInstance() {
    // given
    final long processInstanceKey =
        deployAndCreate(
            adHocSubProcess -> {
              adHocSubProcess.zeebeActiveElementsCollectionExpression("[\"task1\",\"task2\"]");
              adHocSubProcess
                  .manualTask("task1")
                  .parallelGateway("join")
                  .manualTask("a")
                  .moveToNode("join")
                  .manualTask("b");
              adHocSubProcess.manualTask("task2").connectTo("join");
            });

    // then
    final var records = recordsUntilCompleted(processInstanceKey);
    final var flowScopeKeys =
        records.stream()
            .filter(r -> r.getIntent() == ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .filter(r -> List.of("a", "b").contains(r.getValue().getElementId()))
            .map(r -> r.getValue().getFlowScopeKey())
            .distinct()
            .toList();
    assertThat(flowScopeKeys)
        .describedAs("Expected each path after the join to run in its own inner instance")
        .hasSize(2);
    assertThat(records)
        .extracting(r -> r.getValue().getElementId(), Record::getIntent)
        .contains(tuple(processId, ProcessInstanceIntent.ELEMENT_COMPLETED));
  }

  @Test
  public void shouldTriggerEventSubProcessWhileTokenWaitsAtJoin() {
    // given
    final var correlationKey = UUID.randomUUID().toString();
    final long processInstanceKey =
        deployAndCreate(
            adHocSubProcess -> {
              adHocSubProcess.zeebeActiveElementsCollectionExpression("[\"task1\",\"task2\"]");
              adHocSubProcess
                  .manualTask("task1")
                  .sequenceFlowId("toJoin1")
                  .parallelGateway("join")
                  .manualTask("task3");
              adHocSubProcess.serviceTask("task2", t -> t.zeebeJobType(jobType)).connectTo("join");
              adHocSubProcess
                  .embeddedSubProcess()
                  .eventSubProcess("event_sub_process")
                  .startEvent("event_sub_start")
                  .message(
                      m ->
                          m.name("msg")
                              .zeebeCorrelationKeyExpression("=\"%s\"".formatted(correlationKey)))
                  .interrupting(false)
                  .endEvent("event_sub_end");
            });
    awaitTokenAtJoin(processInstanceKey);

    // when
    ENGINE.message().withName("msg").withCorrelationKey(correlationKey).publish();
    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
        .withProcessInstanceKey(processInstanceKey)
        .withElementId("event_sub_process")
        .await();
    ENGINE.job().ofInstance(processInstanceKey).withType(jobType).complete();

    // then
    assertThat(recordsUntilCompleted(processInstanceKey))
        .extracting(r -> r.getValue().getElementId(), Record::getIntent)
        .containsSubsequence(
            tuple("event_sub_process", ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple("join", ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple("task3", ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(processId, ProcessInstanceIntent.ELEMENT_COMPLETED));
  }

  @Test
  public void shouldCompensateActivityThatLedIntoSharedJoinAfterAdHocSubProcess() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(processId)
            .startEvent()
            .adHocSubProcess(
                AD_HOC_SUB_PROCESS_ELEMENT_ID,
                adHocSubProcess -> {
                  adHocSubProcess.zeebeActiveElementsCollectionExpression("[\"task1\",\"task2\"]");
                  final var task1 = adHocSubProcess.manualTask("task1");
                  task1.boundaryEvent().compensation(c -> c.manualTask("undo1"));
                  task1.parallelGateway("join").manualTask("task3");
                  adHocSubProcess.manualTask("task2").connectTo("join");
                })
            .intermediateThrowEvent("throw")
            .compensateEventDefinition()
            .compensateEventDefinitionDone()
            .endEvent()
            .done();
    ENGINE.deployment().withXmlResource(process).deploy();

    // when
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId).create();

    // then
    assertThat(recordsUntilCompleted(processInstanceKey))
        .extracting(r -> r.getValue().getElementId(), Record::getIntent)
        .contains(
            tuple("undo1", ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(processId, ProcessInstanceIntent.ELEMENT_COMPLETED));
  }

  @Test
  public void shouldCompensateActivityOfAdHocSubProcessWithoutJoin() {
    // given - baseline: compensation after the ad-hoc sub-process without a shared join
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(processId)
            .startEvent()
            .adHocSubProcess(
                AD_HOC_SUB_PROCESS_ELEMENT_ID,
                adHocSubProcess -> {
                  adHocSubProcess.zeebeActiveElementsCollectionExpression("[\"task1\",\"task2\"]");
                  final var task1 = adHocSubProcess.manualTask("task1");
                  task1.boundaryEvent().compensation(c -> c.manualTask("undo1"));
                  adHocSubProcess.manualTask("task2");
                })
            .intermediateThrowEvent("throw")
            .compensateEventDefinition()
            .compensateEventDefinitionDone()
            .endEvent()
            .done();
    ENGINE.deployment().withXmlResource(process).deploy();

    // when
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId).create();

    // then
    assertThat(recordsUntilCompleted(processInstanceKey))
        .extracting(r -> r.getValue().getElementId(), Record::getIntent)
        .contains(
            tuple("undo1", ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(processId, ProcessInstanceIntent.ELEMENT_COMPLETED));
  }

  @Test
  public void shouldMigrateTokenWaitingAtSharedJoin() {
    // given
    final String targetProcessId = processId + "-target";
    final Consumer<AdHocSubProcessBuilder> adHocSubProcessModel =
        adHocSubProcess -> {
          adHocSubProcess.zeebeActiveElementsCollectionExpression("[\"task1\",\"task2\"]");
          adHocSubProcess
              .manualTask("task1")
              .sequenceFlowId("toJoin1")
              .parallelGateway("join")
              .manualTask("task3");
          adHocSubProcess
              .serviceTask("task2", t -> t.zeebeJobType(jobType))
              .sequenceFlowId("toJoin2")
              .connectTo("join");
        };
    final var deployment =
        ENGINE
            .deployment()
            .withXmlResource(
                "source.bpmn",
                Bpmn.createExecutableProcess(processId)
                    .startEvent()
                    .adHocSubProcess(AD_HOC_SUB_PROCESS_ELEMENT_ID, adHocSubProcessModel)
                    .endEvent()
                    .done())
            .withXmlResource(
                "target.bpmn",
                Bpmn.createExecutableProcess(targetProcessId)
                    .startEvent()
                    .adHocSubProcess(AD_HOC_SUB_PROCESS_ELEMENT_ID, adHocSubProcessModel)
                    .endEvent()
                    .done())
            .deploy();
    final long targetProcessDefinitionKey =
        deployment.getValue().getProcessesMetadata().stream()
            .filter(p -> p.getBpmnProcessId().equals(targetProcessId))
            .findFirst()
            .orElseThrow()
            .getProcessDefinitionKey();
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId).create();
    awaitTokenAtJoin(processInstanceKey);

    // when
    ENGINE
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .migration()
        .withTargetProcessDefinitionKey(targetProcessDefinitionKey)
        .addMappingInstruction(AD_HOC_SUB_PROCESS_ELEMENT_ID, AD_HOC_SUB_PROCESS_ELEMENT_ID)
        .addMappingInstruction("task2", "task2")
        .addMappingInstruction("join", "join")
        .addMappingInstruction("toJoin1", "toJoin1")
        .migrate();
    ENGINE.job().ofInstance(processInstanceKey).withType(jobType).complete();

    // then
    assertThat(recordsUntilCompleted(processInstanceKey))
        .extracting(r -> r.getValue().getElementId(), Record::getIntent)
        .containsSubsequence(
            tuple("join", ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple("task3", ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(targetProcessId, ProcessInstanceIntent.ELEMENT_COMPLETED));
  }

  @Test
  public void shouldCompleteSharedJoinWithExecutionListener() {
    // given
    final long processInstanceKey =
        deployAndCreate(
            adHocSubProcess -> {
              adHocSubProcess.zeebeActiveElementsCollectionExpression("[\"task1\",\"task2\"]");
              adHocSubProcess
                  .manualTask("task1")
                  .parallelGateway("join")
                  .zeebeStartExecutionListener(jobType + "-start")
                  .manualTask("task3");
              adHocSubProcess.manualTask("task2").connectTo("join");
            });

    // when
    ENGINE.job().ofInstance(processInstanceKey).withType(jobType + "-start").complete();

    // then
    assertThat(recordsUntilCompleted(processInstanceKey))
        .extracting(r -> r.getValue().getElementId(), Record::getIntent)
        .containsSubsequence(
            tuple("join", ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple("task3", ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(processId, ProcessInstanceIntent.ELEMENT_COMPLETED));
  }

  @Test
  public void shouldResolveIncidentOnSharedInclusiveJoin() {
    // given
    final long processInstanceKey =
        deployAndCreate(
            adHocSubProcess -> {
              adHocSubProcess.zeebeActiveElementsCollectionExpression("[\"task1\",\"task2\"]");
              adHocSubProcess
                  .manualTask("task1")
                  .inclusiveGateway("join")
                  .conditionExpression("x > 0")
                  .manualTask("task3");
              adHocSubProcess.manualTask("task2").connectTo("join");
            });
    final var incident =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();
    assertThat(incident.getValue().getElementId()).isEqualTo("join");

    // when
    ENGINE.variables().ofScope(processInstanceKey).withDocument(Map.of("x", 1)).update();
    ENGINE.incident().ofInstance(processInstanceKey).withKey(incident.getKey()).resolve();

    // then
    assertThat(recordsUntilCompleted(processInstanceKey))
        .extracting(r -> r.getValue().getElementId(), Record::getIntent)
        .containsSubsequence(
            tuple("join", ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple("task3", ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(processId, ProcessInstanceIntent.ELEMENT_COMPLETED));
  }

  @Test
  public void shouldJoinPerMultiInstanceIteration() {
    // given
    final long processInstanceKey =
        deployAndCreate(
            adHocSubProcess -> {
              adHocSubProcess.multiInstance().parallel().zeebeInputCollectionExpression("[1,2]");
              adHocSubProcess.zeebeActiveElementsCollectionExpression("[\"task1\",\"task2\"]");
              adHocSubProcess.manualTask("task1").parallelGateway("join").manualTask("task3");
              adHocSubProcess.manualTask("task2").connectTo("join");
            });

    // then
    final var records = recordsUntilCompleted(processInstanceKey);
    assertThat(records)
        .filteredOn(r -> r.getIntent() == ProcessInstanceIntent.ELEMENT_COMPLETED)
        .filteredOn(r -> r.getValue().getElementId().equals("task3"))
        .hasSize(2);
    assertThat(records)
        .filteredOn(r -> r.getIntent() == ProcessInstanceIntent.ELEMENT_ACTIVATED)
        .filteredOn(r -> r.getValue().getElementId().equals("join"))
        .extracting(r -> r.getValue().getFlowScopeKey())
        .describedAs("Expected each iteration to join in its own ad-hoc sub-process instance")
        .doesNotHaveDuplicates()
        .hasSize(2);
  }

  @Test
  public void shouldJoinElementsActivatedByJobWorker() {
    // given
    final long processInstanceKey =
        deployAndCreate(
            adHocSubProcess -> {
              adHocSubProcess.zeebeJobType(jobType);
              adHocSubProcess.manualTask("task1").parallelGateway("join").manualTask("task3");
              adHocSubProcess.manualTask("task2").connectTo("join");
            });

    // when
    completeAdHocSubProcessJob(false, "task1", "task2");

    // then - the worker is called again once the path after the join completes
    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
        .withProcessInstanceKey(processInstanceKey)
        .withElementId("task3")
        .await();
    completeAdHocSubProcessJob(true);

    assertThat(recordsUntilCompleted(processInstanceKey))
        .extracting(r -> r.getValue().getElementId(), Record::getIntent)
        .containsSubsequence(
            tuple("join", ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple("task3", ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(AD_HOC_SUB_PROCESS_ELEMENT_ID, ProcessInstanceIntent.ELEMENT_COMPLETED));
  }

  private void completeAdHocSubProcessJob(
      final boolean completionConditionFulfilled, final String... elementIds) {
    final var jobKey =
        ENGINE.jobs().withType(jobType).activate().getValue().getJobKeys().getFirst();
    final var jobResult =
        new JobResult()
            .setActivateElements(
                Arrays.stream(elementIds)
                    .<JobResultActivateElementValue>map(
                        id -> new JobResultActivateElement().setElementId(id))
                    .toList())
            .setCompletionConditionFulfilled(completionConditionFulfilled);
    ENGINE.job().withKey(jobKey).withResult(jobResult).complete();
  }

  @Test
  public void shouldInterruptAdHocSubProcessWhileTokenWaitsAtJoin() {
    // given
    final var correlationKey = UUID.randomUUID().toString();
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(processId)
            .startEvent()
            .adHocSubProcess(
                AD_HOC_SUB_PROCESS_ELEMENT_ID,
                adHocSubProcess -> {
                  adHocSubProcess.zeebeActiveElementsCollectionExpression("[\"task1\",\"task2\"]");
                  adHocSubProcess
                      .manualTask("task1")
                      .sequenceFlowId("toJoin1")
                      .parallelGateway("join")
                      .manualTask("task3");
                  adHocSubProcess
                      .serviceTask("task2", t -> t.zeebeJobType(jobType))
                      .connectTo("join");
                })
            .boundaryEvent(
                "interrupt",
                b ->
                    b.message(
                        m ->
                            m.name("msg")
                                .zeebeCorrelationKeyExpression(
                                    "=\"%s\"".formatted(correlationKey))))
            .endEvent("interrupted")
            .moveToActivity(AD_HOC_SUB_PROCESS_ELEMENT_ID)
            .endEvent()
            .done();
    ENGINE.deployment().withXmlResource(process).deploy();
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId).create();
    awaitTokenAtJoin(processInstanceKey);

    // when
    ENGINE.message().withName("msg").withCorrelationKey(correlationKey).publish();

    // then
    assertThat(recordsUntilCompleted(processInstanceKey))
        .extracting(r -> r.getValue().getElementId(), Record::getIntent)
        .contains(
            tuple("task2", ProcessInstanceIntent.ELEMENT_TERMINATED),
            tuple(AD_HOC_SUB_PROCESS_ELEMENT_ID, ProcessInstanceIntent.ELEMENT_TERMINATED),
            tuple("interrupted", ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(processId, ProcessInstanceIntent.ELEMENT_COMPLETED))
        .doesNotContain(tuple("join", ProcessInstanceIntent.ELEMENT_ACTIVATED));
  }

  @Test
  public void shouldActivateSharedJoinByModification() {
    // given
    final long processInstanceKey =
        deployAndCreate(
            adHocSubProcess -> {
              adHocSubProcess.zeebeActiveElementsCollectionExpression("[\"task2\"]");
              adHocSubProcess.manualTask("task1").parallelGateway("join").manualTask("task3");
              adHocSubProcess.serviceTask("task2", t -> t.zeebeJobType(jobType)).connectTo("join");
            });
    final long adHocSubProcessKey =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementId(AD_HOC_SUB_PROCESS_ELEMENT_ID)
            .getFirst()
            .getKey();
    RecordingExporter.jobRecords(JobIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .withType(jobType)
        .await();

    // when
    ENGINE
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .modification()
        .activateElement("join")
        .modify();
    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
        .withProcessInstanceKey(processInstanceKey)
        .withElementId("task3")
        .await();
    ENGINE.job().ofInstance(processInstanceKey).withType(jobType).complete();
    ENGINE
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .modification()
        .terminateElement(adHocSubProcessKey)
        .modify();

    // then
    final var records =
        RecordingExporter.processInstanceRecords()
            .withProcessInstanceKey(processInstanceKey)
            .limit(
                r ->
                    r.getKey() == adHocSubProcessKey
                        && r.getIntent() == ProcessInstanceIntent.ELEMENT_TERMINATED)
            .toList();
    assertThat(records)
        .filteredOn(
            r ->
                r.getValue().getBpmnElementType()
                    == BpmnElementType.AD_HOC_SUB_PROCESS_INNER_INSTANCE)
        .extracting(r -> r.getValue().getFlowScopeKey())
        .describedAs("Expected every inner instance to be a direct child of the ad-hoc sub-process")
        .containsOnly(adHocSubProcessKey);
    assertThat(records)
        .extracting(r -> r.getValue().getElementId(), Record::getIntent)
        .contains(tuple("task3", ProcessInstanceIntent.ELEMENT_COMPLETED));
  }
}
