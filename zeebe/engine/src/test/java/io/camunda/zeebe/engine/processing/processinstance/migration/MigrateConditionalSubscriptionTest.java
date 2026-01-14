/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.processinstance.migration;

import static io.camunda.zeebe.engine.processing.processinstance.migration.MigrationTestUtil.extractProcessDefinitionKeyByProcessId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.ConditionalSubscriptionIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ConditionalSubscriptionRecordValue;
import io.camunda.zeebe.test.util.BrokerClassRuleHelper;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.Map;
import org.assertj.core.groups.Tuple;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;

public class MigrateConditionalSubscriptionTest {

  @Rule public final EngineRule engine = EngineRule.singlePartition();

  @Rule public final TestWatcher watcher = new RecordingExporterTestWatcher();
  @Rule public final BrokerClassRuleHelper helper = new BrokerClassRuleHelper();

  @Test
  public void shouldWriteConditionalMigratedEvent() {
    // given
    final String processId = helper.getBpmnProcessId();
    final String targetProcessId = helper.getBpmnProcessId() + "2";

    final var deployment =
        engine
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .startEvent()
                    .userTask("A")
                    .boundaryEvent("boundary1")
                    .cancelActivity(true)
                    .condition(
                        c ->
                            c.condition("=x > 10")
                                .zeebeVariableNames("x")
                                .zeebeVariableEvents("create"))
                    .endEvent()
                    .moveToActivity("A")
                    .endEvent()
                    .done())
            .withXmlResource(
                Bpmn.createExecutableProcess(targetProcessId)
                    .startEvent()
                    .userTask("B")
                    .boundaryEvent("boundary2")
                    .cancelActivity(true)
                    .condition(
                        c ->
                            c.condition("=y > 10")
                                .zeebeVariableNames("y")
                                .zeebeVariableEvents("update"))
                    .endEvent("target_process_conditional_end")
                    .moveToActivity("B")
                    .endEvent()
                    .done())
            .deploy();

    final long targetProcessDefinitionKey =
        extractProcessDefinitionKeyByProcessId(deployment, targetProcessId);

    final long processInstanceKey = engine.processInstance().ofBpmnProcessId(processId).create();

    RecordingExporter.conditionalSubscriptionRecords(ConditionalSubscriptionIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .withCatchEventId("boundary1")
        .await();

    // when
    engine
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .migration()
        .withTargetProcessDefinitionKey(targetProcessDefinitionKey)
        .addMappingInstruction("A", "B")
        .addMappingInstruction("boundary1", "boundary2")
        .migrate();

    // then
    Assertions.assertThat(
            RecordingExporter.conditionalSubscriptionRecords(ConditionalSubscriptionIntent.MIGRATED)
                .withProcessInstanceKey(processInstanceKey)
                .withCatchEventId("boundary2")
                .getFirst()
                .getValue())
        .describedAs("Expect that the process definition is updated")
        .hasProcessDefinitionKey(targetProcessDefinitionKey)
        .describedAs("Expect that the catch event id is updated")
        .hasCatchEventId("boundary2")
        .describedAs("Expect that the migrated subscription keeps its source condition")
        .hasCondition("=x > 10")
        .describedAs("Expect that the migrated subscription keeps the source variable names")
        .hasVariableNames("x")
        .describedAs("Expect that the migrated subscription keeps the source variable events")
        .hasVariableEvents("create");

    // and when: trigger via variable update (validates the migrated subscription remains usable)
    engine.variables().ofScope(processInstanceKey).withDocument(Map.of("x", 11)).update();

    Assertions.assertThat(
            RecordingExporter.conditionalSubscriptionRecords(
                    ConditionalSubscriptionIntent.TRIGGERED)
                .withProcessInstanceKey(processInstanceKey)
                .withCatchEventId("boundary2")
                .getFirst()
                .getValue())
        .hasProcessDefinitionKey(targetProcessDefinitionKey)
        .hasCatchEventId("boundary2");

    Assertions.assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
                .withProcessInstanceKey(processInstanceKey)
                .withElementType(BpmnElementType.END_EVENT)
                .withElementId("target_process_conditional_end")
                .getFirst()
                .getValue())
        .describedAs("Expect that the process instance is continued in the target process")
        .isNotNull();
  }

  @Test
  public void shouldMigrateMultipleConditionalBoundaryEvents() {
    // given
    final String processId = helper.getBpmnProcessId();
    final String targetProcessId = helper.getBpmnProcessId() + "2";

    final var deployment =
        engine
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .startEvent()
                    .userTask("A")
                    .boundaryEvent("boundary1")
                    .cancelActivity(true)
                    .condition(c -> c.condition("=x > 10").zeebeVariableNames("x"))
                    .endEvent()
                    .moveToActivity("A")
                    .boundaryEvent("boundary2")
                    .cancelActivity(true)
                    .condition(c -> c.condition("=y > 10").zeebeVariableNames("y"))
                    .endEvent()
                    .moveToActivity("A")
                    .endEvent()
                    .done())
            .withXmlResource(
                Bpmn.createExecutableProcess(targetProcessId)
                    .startEvent()
                    .userTask("B")
                    .boundaryEvent("boundary3")
                    .cancelActivity(true)
                    .condition(c -> c.condition("=x > 999").zeebeVariableNames("x"))
                    .endEvent("target_process_conditional_end1")
                    .moveToActivity("B")
                    .boundaryEvent("boundary4")
                    .cancelActivity(true)
                    .condition(c -> c.condition("=y > 999").zeebeVariableNames("y"))
                    .endEvent("target_process_conditional_end2")
                    .moveToActivity("B")
                    .endEvent()
                    .done())
            .deploy();

    final long targetProcessDefinitionKey =
        extractProcessDefinitionKeyByProcessId(deployment, targetProcessId);

    final long processInstanceKey = engine.processInstance().ofBpmnProcessId(processId).create();

    RecordingExporter.conditionalSubscriptionRecords(ConditionalSubscriptionIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .withCatchEventId("boundary1")
        .await();
    RecordingExporter.conditionalSubscriptionRecords(ConditionalSubscriptionIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .withCatchEventId("boundary2")
        .await();

    // when
    engine
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .migration()
        .withTargetProcessDefinitionKey(targetProcessDefinitionKey)
        .addMappingInstruction("A", "B")
        .addMappingInstruction("boundary1", "boundary3")
        .addMappingInstruction("boundary2", "boundary4")
        .migrate();

    // then
    assertThat(
            RecordingExporter.conditionalSubscriptionRecords(ConditionalSubscriptionIntent.MIGRATED)
                .withProcessDefinitionKey(targetProcessDefinitionKey)
                .limit(2))
        .extracting(Record::getValue)
        .extracting(
            ConditionalSubscriptionRecordValue::getCatchEventId,
            ConditionalSubscriptionRecordValue::getCondition)
        .describedAs("Expect that the conditional boundary events are migrated")
        .containsExactlyInAnyOrder(
            Tuple.tuple("boundary3", "=x > 10"), Tuple.tuple("boundary4", "=y > 10"));
  }

  @Test
  public void shouldMigrateOneOfMultipleConditionalBoundaryEventsAndDelete() {
    // given
    final String processId = helper.getBpmnProcessId();
    final String targetProcessId = helper.getBpmnProcessId() + "2";

    final var deployment =
        engine
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .startEvent()
                    .userTask("A")
                    .boundaryEvent("boundary1")
                    .cancelActivity(true)
                    .condition(c -> c.condition("=x > 10").zeebeVariableNames("x"))
                    .endEvent()
                    .moveToActivity("A")
                    .boundaryEvent("boundary2")
                    .cancelActivity(true)
                    .condition(c -> c.condition("=y > 10").zeebeVariableNames("y"))
                    .endEvent()
                    .moveToActivity("A")
                    .endEvent()
                    .done())
            .withXmlResource(
                Bpmn.createExecutableProcess(targetProcessId)
                    .startEvent()
                    .userTask("B")
                    .boundaryEvent("boundary3")
                    .cancelActivity(true)
                    .condition(c -> c.condition("=x > 999").zeebeVariableNames("x"))
                    .endEvent("target_process_conditional_end")
                    .moveToActivity("B")
                    .endEvent()
                    .done())
            .deploy();

    final long targetProcessDefinitionKey =
        extractProcessDefinitionKeyByProcessId(deployment, targetProcessId);

    final long processInstanceKey = engine.processInstance().ofBpmnProcessId(processId).create();

    RecordingExporter.conditionalSubscriptionRecords(ConditionalSubscriptionIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .withCatchEventId("boundary1")
        .await();
    RecordingExporter.conditionalSubscriptionRecords(ConditionalSubscriptionIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .withCatchEventId("boundary2")
        .await();

    // when
    engine
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .migration()
        .withTargetProcessDefinitionKey(targetProcessDefinitionKey)
        .addMappingInstruction("A", "B")
        .addMappingInstruction("boundary1", "boundary3")
        .migrate();

    // then
    Assertions.assertThat(
            RecordingExporter.conditionalSubscriptionRecords(ConditionalSubscriptionIntent.MIGRATED)
                .withProcessInstanceKey(processInstanceKey)
                .withCatchEventId("boundary3")
                .getFirst()
                .getValue())
        .hasProcessDefinitionKey(targetProcessDefinitionKey)
        .hasCatchEventId("boundary3")
        .hasCondition("=x > 10");

    Assertions.assertThat(
            RecordingExporter.conditionalSubscriptionRecords(ConditionalSubscriptionIntent.DELETED)
                .withCatchEventId("boundary2")
                .getFirst())
        .describedAs("Expect that the non-mapped conditional boundary event is deleted")
        .isNotNull();

    // validate migrated subscription is usable
    engine.variables().ofScope(processInstanceKey).withDocument(Map.of("x", 1000)).update();

    Assertions.assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
                .withProcessInstanceKey(processInstanceKey)
                .withElementType(BpmnElementType.END_EVENT)
                .withElementId("target_process_conditional_end")
                .getFirst()
                .getValue())
        .isNotNull();
  }

  @Test
  public void shouldMigrateOneOfMultipleConditionalBoundaryEventsAndCreate() {
    // given
    final String processId = helper.getBpmnProcessId();
    final String targetProcessId = helper.getBpmnProcessId() + "2";

    final var deployment =
        engine
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .startEvent()
                    .userTask("A")
                    .boundaryEvent("boundary1")
                    .cancelActivity(true)
                    .condition(c -> c.condition("=x > 10").zeebeVariableNames("x"))
                    .endEvent()
                    .moveToActivity("A")
                    .endEvent()
                    .done())
            .withXmlResource(
                Bpmn.createExecutableProcess(targetProcessId)
                    .startEvent()
                    .userTask("B")
                    .boundaryEvent("boundary2")
                    .cancelActivity(true)
                    .condition(c -> c.condition("=x > 999").zeebeVariableNames("x"))
                    .endEvent()
                    .moveToActivity("B")
                    .boundaryEvent("boundary3")
                    .cancelActivity(true)
                    .condition(c -> c.condition("=z > 10").zeebeVariableNames("z"))
                    .endEvent("target_process_conditional_end")
                    .moveToActivity("B")
                    .endEvent()
                    .done())
            .deploy();

    final long targetProcessDefinitionKey =
        extractProcessDefinitionKeyByProcessId(deployment, targetProcessId);

    final long processInstanceKey = engine.processInstance().ofBpmnProcessId(processId).create();

    RecordingExporter.conditionalSubscriptionRecords(ConditionalSubscriptionIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .withCatchEventId("boundary1")
        .await();

    // when
    engine
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .migration()
        .withTargetProcessDefinitionKey(targetProcessDefinitionKey)
        .addMappingInstruction("A", "B")
        .addMappingInstruction("boundary1", "boundary2")
        .migrate();

    // then
    Assertions.assertThat(
            RecordingExporter.conditionalSubscriptionRecords(ConditionalSubscriptionIntent.MIGRATED)
                .withProcessInstanceKey(processInstanceKey)
                .withCatchEventId("boundary2")
                .getFirst()
                .getValue())
        .hasProcessDefinitionKey(targetProcessDefinitionKey)
        .hasCatchEventId("boundary2")
        .hasCondition("=x > 10");

    // the additional conditional boundary in the target should be created
    Assertions.assertThat(
            RecordingExporter.conditionalSubscriptionRecords(ConditionalSubscriptionIntent.CREATED)
                .withProcessDefinitionKey(targetProcessDefinitionKey)
                .withCatchEventId("boundary3")
                .getFirst()
                .getValue())
        .hasCatchEventId("boundary3")
        .hasCondition("=z > 10");
  }

  @Test
  public void shouldResubscribeToConditionalEvent() {
    // given
    final String processId = helper.getBpmnProcessId();
    final String targetProcessId = helper.getBpmnProcessId() + "2";

    final var deployment =
        engine
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .startEvent()
                    .userTask("A")
                    .boundaryEvent("boundary1")
                    .cancelActivity(true)
                    .condition(c -> c.condition("=x > 10").zeebeVariableNames("x"))
                    .endEvent()
                    .moveToActivity("A")
                    .endEvent()
                    .done())
            .withXmlResource(
                Bpmn.createExecutableProcess(targetProcessId)
                    .startEvent()
                    .userTask("B")
                    .boundaryEvent("boundary2")
                    .cancelActivity(true)
                    .condition(c -> c.condition("=y > 10").zeebeVariableNames("y"))
                    .endEvent("target_process_conditional_end")
                    .moveToActivity("B")
                    .endEvent()
                    .done())
            .deploy();

    final long targetProcessDefinitionKey =
        extractProcessDefinitionKeyByProcessId(deployment, targetProcessId);

    final long processInstanceKey = engine.processInstance().ofBpmnProcessId(processId).create();

    RecordingExporter.conditionalSubscriptionRecords(ConditionalSubscriptionIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .withCatchEventId("boundary1")
        .await();

    // when: migrate without mapping boundary1 -> boundary2 (resubscribe semantics)
    engine
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .migration()
        .withTargetProcessDefinitionKey(targetProcessDefinitionKey)
        .addMappingInstruction("A", "B")
        .migrate();

    // then: old subscription is deleted
    Assertions.assertThat(
            RecordingExporter.conditionalSubscriptionRecords(ConditionalSubscriptionIntent.DELETED)
                .withCatchEventId("boundary1")
                .getFirst())
        .describedAs("Expect that the conditional boundary event in the source is deleted")
        .isNotNull();

    // and new subscription is created in target
    Assertions.assertThat(
            RecordingExporter.conditionalSubscriptionRecords(ConditionalSubscriptionIntent.CREATED)
                .withProcessDefinitionKey(targetProcessDefinitionKey)
                .withCatchEventId("boundary2")
                .getFirst()
                .getValue())
        .describedAs("Expect that the conditional boundary event in the target is created")
        .hasProcessDefinitionKey(targetProcessDefinitionKey)
        .hasCatchEventId("boundary2")
        .hasCondition("=y > 10");

    // validate new subscription is usable
    engine.variables().ofScope(processInstanceKey).withDocument(Map.of("y", 11)).update();

    Assertions.assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
                .withProcessInstanceKey(processInstanceKey)
                .withElementType(BpmnElementType.END_EVENT)
                .withElementId("target_process_conditional_end")
                .getFirst()
                .getValue())
        .isNotNull();
  }

  @Test
  public void shouldResubscribeToConditionalEventWithTheSameCondition() {
    // given
    final String processId = helper.getBpmnProcessId();
    final String targetProcessId = helper.getBpmnProcessId() + "2";

    final var deployment =
        engine
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .startEvent()
                    .userTask("A")
                    .boundaryEvent("boundary1")
                    .cancelActivity(true)
                    .condition(c -> c.condition("=x > 10").zeebeVariableNames("x"))
                    .endEvent()
                    .moveToActivity("A")
                    .endEvent()
                    .done())
            .withXmlResource(
                Bpmn.createExecutableProcess(targetProcessId)
                    .startEvent()
                    .userTask("B")
                    .boundaryEvent("boundary2")
                    .cancelActivity(true)
                    .condition(c -> c.condition("=x > 10").zeebeVariableNames("x"))
                    .endEvent("target_process_conditional_end")
                    .moveToActivity("B")
                    .endEvent()
                    .done())
            .deploy();

    final long targetProcessDefinitionKey =
        extractProcessDefinitionKeyByProcessId(deployment, targetProcessId);

    final long processInstanceKey = engine.processInstance().ofBpmnProcessId(processId).create();

    RecordingExporter.conditionalSubscriptionRecords(ConditionalSubscriptionIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .withCatchEventId("boundary1")
        .await();

    // when: migrate without mapping boundary1 -> boundary2 (resubscribe semantics, even though
    // condition is the same)
    engine
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .migration()
        .withTargetProcessDefinitionKey(targetProcessDefinitionKey)
        .addMappingInstruction("A", "B")
        .migrate();

    // then: old deleted
    Assertions.assertThat(
            RecordingExporter.conditionalSubscriptionRecords(ConditionalSubscriptionIntent.DELETED)
                .withCatchEventId("boundary1")
                .getFirst())
        .isNotNull();

    // new created (same condition)
    Assertions.assertThat(
            RecordingExporter.conditionalSubscriptionRecords(ConditionalSubscriptionIntent.CREATED)
                .withProcessDefinitionKey(targetProcessDefinitionKey)
                .withCatchEventId("boundary2")
                .getFirst()
                .getValue())
        .hasProcessDefinitionKey(targetProcessDefinitionKey)
        .hasCatchEventId("boundary2")
        .hasCondition("=x > 10");

    engine.variables().ofScope(processInstanceKey).withDocument(Map.of("x", 11)).update();

    Assertions.assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
                .withProcessInstanceKey(processInstanceKey)
                .withElementType(BpmnElementType.END_EVENT)
                .withElementId("target_process_conditional_end")
                .getFirst()
                .getValue())
        .isNotNull();
  }

  @Test
  public void shouldKeepScopeKeyWhenMigratingConditionalBoundarySubscription() {
    // given
    final String processId = helper.getBpmnProcessId();
    final String targetProcessId = helper.getBpmnProcessId() + "2";

    final var deployment =
        engine
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .startEvent()
                    .userTask("A")
                    .boundaryEvent("boundary1")
                    .cancelActivity(true)
                    .condition(c -> c.condition("=x > 10").zeebeVariableNames("x"))
                    .endEvent()
                    .moveToActivity("A")
                    .endEvent()
                    .done())
            .withXmlResource(
                Bpmn.createExecutableProcess(targetProcessId)
                    .startEvent()
                    .userTask("B")
                    .boundaryEvent("boundary2")
                    .cancelActivity(true)
                    .condition(c -> c.condition("=x > 999").zeebeVariableNames("x"))
                    .endEvent("target_process_conditional_end")
                    .moveToActivity("B")
                    .endEvent()
                    .done())
            .deploy();

    final long targetProcessDefinitionKey =
        extractProcessDefinitionKeyByProcessId(deployment, targetProcessId);

    final long processInstanceKey = engine.processInstance().ofBpmnProcessId(processId).create();

    final var created =
        RecordingExporter.conditionalSubscriptionRecords(ConditionalSubscriptionIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .withCatchEventId("boundary1")
            .getFirst();

    final long originalScopeKey = created.getValue().getScopeKey();

    // when
    engine
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .migration()
        .withTargetProcessDefinitionKey(targetProcessDefinitionKey)
        .addMappingInstruction("A", "B")
        .addMappingInstruction("boundary1", "boundary2")
        .migrate();

    // then
    assertThat(
            RecordingExporter.conditionalSubscriptionRecords(ConditionalSubscriptionIntent.MIGRATED)
                .withProcessInstanceKey(processInstanceKey)
                .withCatchEventId("boundary2")
                .withCondition("=x > 10")
                .getFirst()
                .getValue()
                .getScopeKey())
        .describedAs("Expect that the scope key remains stable across migration")
        .isEqualTo(originalScopeKey);
  }

  @Test
  public void shouldCreateConditionalSubscriptionForNewlyIntroducedConditionalCatchInTarget() {
    // given
    final String processId = helper.getBpmnProcessId();
    final String targetProcessId = helper.getBpmnProcessId() + "2";

    final var deployment =
        engine
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .startEvent()
                    .userTask("A")
                    .endEvent()
                    .done())
            .withXmlResource(
                Bpmn.createExecutableProcess(targetProcessId)
                    .startEvent()
                    .userTask("B")
                    .boundaryEvent("boundary1")
                    .cancelActivity(false)
                    .condition(c -> c.condition("=x > 10").zeebeVariableNames("x"))
                    .endEvent("target_process_conditional_end")
                    .moveToActivity("B")
                    .endEvent()
                    .done())
            .deploy();

    final long targetProcessDefinitionKey =
        extractProcessDefinitionKeyByProcessId(deployment, targetProcessId);

    final long processInstanceKey = engine.processInstance().ofBpmnProcessId(processId).create();

    // when
    engine
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .migration()
        .withTargetProcessDefinitionKey(targetProcessDefinitionKey)
        .addMappingInstruction("A", "B")
        .migrate();

    // then: the new conditional catch subscription should be created
    Assertions.assertThat(
            RecordingExporter.conditionalSubscriptionRecords(ConditionalSubscriptionIntent.CREATED)
                .withProcessDefinitionKey(targetProcessDefinitionKey)
                .getFirst()
                .getValue())
        .hasProcessDefinitionKey(targetProcessDefinitionKey)
        .hasCatchEventId("boundary1")
        .hasCondition("=x > 10");

    // validate it can be triggered
    engine.variables().ofScope(processInstanceKey).withDocument(Map.of("x", 11)).update();

    Assertions.assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
                .withProcessInstanceKey(processInstanceKey)
                .withElementId("target_process_conditional_end")
                .getFirst()
                .getValue())
        .isNotNull();
  }

  @Test
  public void shouldMigrateTwoNonInterruptingConditionalBoundariesAndPreserveBothSubscriptions() {
    // given
    final String processId = helper.getBpmnProcessId();
    final String targetProcessId = helper.getBpmnProcessId() + "2";

    final var deployment =
        engine
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .startEvent()
                    .userTask("A")
                    .boundaryEvent("boundary1")
                    .cancelActivity(false)
                    .condition(c -> c.condition("=x > 10").zeebeVariableNames("x"))
                    .endEvent()
                    .moveToActivity("A")
                    .boundaryEvent("boundary2")
                    .cancelActivity(false)
                    .condition(c -> c.condition("=x > 5").zeebeVariableNames("x"))
                    .endEvent()
                    .moveToActivity("A")
                    .endEvent()
                    .done())
            .withXmlResource(
                Bpmn.createExecutableProcess(targetProcessId)
                    .startEvent()
                    .userTask("B")
                    .boundaryEvent("boundary3")
                    .cancelActivity(false)
                    .condition(c -> c.condition("=x > 99").zeebeVariableNames("x"))
                    .endEvent()
                    .moveToActivity("B")
                    .boundaryEvent("boundary4")
                    .cancelActivity(false)
                    .condition(c -> c.condition("=x > 999").zeebeVariableNames("x"))
                    .endEvent()
                    .moveToActivity("B")
                    .endEvent()
                    .done())
            .deploy();

    final long targetProcessDefinitionKey =
        extractProcessDefinitionKeyByProcessId(deployment, targetProcessId);

    final long processInstanceKey = engine.processInstance().ofBpmnProcessId(processId).create();

    RecordingExporter.conditionalSubscriptionRecords(ConditionalSubscriptionIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .withCatchEventId("boundary1")
        .await();
    RecordingExporter.conditionalSubscriptionRecords(ConditionalSubscriptionIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .withCatchEventId("boundary2")
        .await();

    // when
    engine
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .migration()
        .withTargetProcessDefinitionKey(targetProcessDefinitionKey)
        .addMappingInstruction("A", "B")
        .addMappingInstruction("boundary1", "boundary3")
        .addMappingInstruction("boundary2", "boundary4")
        .migrate();

    // then
    assertThat(
            RecordingExporter.conditionalSubscriptionRecords(ConditionalSubscriptionIntent.MIGRATED)
                .withProcessInstanceKey(processInstanceKey)
                .withVariableNames("x")
                .limit(2))
        .extracting(Record::getValue)
        .extracting(
            ConditionalSubscriptionRecordValue::getCatchEventId,
            ConditionalSubscriptionRecordValue::getCondition)
        .containsExactlyInAnyOrder(tuple("boundary3", "=x > 10"), tuple("boundary4", "=x > 5"))
        .hasSize(2);
  }

  @Test
  public void shouldMigrateConditionalIntermediateCatchSubscription() {
    // given
    final String processId = helper.getBpmnProcessId();
    final String targetProcessId = helper.getBpmnProcessId() + "2";

    final var deployment =
        engine
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .startEvent()
                    .intermediateCatchEvent("catch1")
                    .condition(c -> c.condition("=x > 10").zeebeVariableNames("x"))
                    .endEvent()
                    .done())
            .withXmlResource(
                Bpmn.createExecutableProcess(targetProcessId)
                    .startEvent()
                    .intermediateCatchEvent("catch2")
                    .condition(c -> c.condition("=x > 999").zeebeVariableNames("x"))
                    .endEvent("target_process_conditional_end")
                    .done())
            .deploy();

    final long targetProcessDefinitionKey =
        extractProcessDefinitionKeyByProcessId(deployment, targetProcessId);

    final long processInstanceKey = engine.processInstance().ofBpmnProcessId(processId).create();

    // ensure instance reached the catch and the subscription exists
    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
        .withProcessInstanceKey(processInstanceKey)
        .withElementId("catch1")
        .await();

    RecordingExporter.conditionalSubscriptionRecords(ConditionalSubscriptionIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .withCatchEventId("catch1")
        .await();

    // when
    engine
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .migration()
        .withTargetProcessDefinitionKey(targetProcessDefinitionKey)
        .addMappingInstruction("catch1", "catch2")
        .migrate();

    // then
    Assertions.assertThat(
            RecordingExporter.conditionalSubscriptionRecords(ConditionalSubscriptionIntent.MIGRATED)
                .withProcessInstanceKey(processInstanceKey)
                .withCatchEventId("catch2")
                .getFirst()
                .getValue())
        .hasProcessDefinitionKey(targetProcessDefinitionKey)
        .hasCatchEventId("catch2")
        .describedAs("Expect that the migrated subscription keeps the source condition")
        .hasCondition("=x > 10");

    // validate migrated catch continues
    engine.variables().ofScope(processInstanceKey).withDocument(Map.of("x", 1000)).update();

    Assertions.assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
                .withProcessInstanceKey(processInstanceKey)
                .withElementType(BpmnElementType.END_EVENT)
                .withElementId("target_process_conditional_end")
                .getFirst()
                .getValue())
        .isNotNull();
  }
}
