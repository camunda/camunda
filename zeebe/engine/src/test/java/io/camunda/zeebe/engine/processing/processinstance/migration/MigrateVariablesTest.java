/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.processinstance.migration;

import static io.camunda.zeebe.engine.processing.processinstance.migration.MigrationTestUtil.extractProcessDefinitionKeyByProcessId;
import static io.camunda.zeebe.protocol.record.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.VariableIntent;
import io.camunda.zeebe.test.util.BrokerClassRuleHelper;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.Map;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;

public class MigrateVariablesTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  @Rule public final TestWatcher watcher = new RecordingExporterTestWatcher();
  @Rule public final BrokerClassRuleHelper helper = new BrokerClassRuleHelper();

  @Test
  public void shouldWriteMigratedEventForGlobalVariable() {
    // given
    final String processId = helper.getBpmnProcessId();
    final String targetProcessId = helper.getBpmnProcessId() + "2";

    final var deployment =
        ENGINE
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .startEvent()
                    .serviceTask("A", a -> a.zeebeJobType("A"))
                    .endEvent()
                    .done())
            .withXmlResource(
                Bpmn.createExecutableProcess(targetProcessId)
                    .startEvent()
                    .serviceTask("A", a -> a.zeebeJobType("A"))
                    .endEvent()
                    .done())
            .deploy();
    final long targetProcessDefinitionKey =
        extractProcessDefinitionKeyByProcessId(deployment, targetProcessId);

    final var processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(processId)
            .withVariables(
                Map.of(
                    "variable_to_migrate",
                    "This is just a string",
                    "another_variable_to_migrate",
                    Map.of("this", "is", "a", "context")))
            .create();

    final var variable =
        RecordingExporter.variableRecords(VariableIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .withName("variable_to_migrate")
            .getFirst()
            .getValue();
    final var variable2 =
        RecordingExporter.variableRecords(VariableIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .withName("another_variable_to_migrate")
            .getFirst()
            .getValue();

    RecordingExporter.jobRecords(JobIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .await();

    // when
    ENGINE
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .migration()
        .withTargetProcessDefinitionKey(targetProcessDefinitionKey)
        .addMappingInstruction("A", "A")
        .migrate();

    // then
    assertThat(
            RecordingExporter.variableRecords(VariableIntent.MIGRATED)
                .withProcessInstanceKey(processInstanceKey)
                .withName("variable_to_migrate")
                .getFirst()
                .getValue())
        .describedAs("Expect that process definition is updated")
        .hasProcessDefinitionKey(targetProcessDefinitionKey)
        .hasBpmnProcessId(targetProcessId)
        .describedAs("Expect that the value is unset to avoid exceeding the max record size")
        .hasValue("null")
        .describedAs("Expect that the other variable data did not change")
        .hasName(variable.getName())
        .hasProcessInstanceKey(variable.getProcessInstanceKey())
        .hasScopeKey(variable.getScopeKey())
        .hasTenantId(variable.getTenantId());
    assertThat(
            RecordingExporter.variableRecords(VariableIntent.MIGRATED)
                .withProcessInstanceKey(processInstanceKey)
                .withName("another_variable_to_migrate")
                .getFirst()
                .getValue())
        .describedAs("Expect that process definition is updated")
        .hasProcessDefinitionKey(targetProcessDefinitionKey)
        .hasBpmnProcessId(targetProcessId)
        .describedAs("Expect that the value is unset to avoid exceeding the max record size")
        .hasValue("null")
        .describedAs("Expect that the other variable data did not change")
        .hasName(variable2.getName())
        .hasProcessInstanceKey(variable2.getProcessInstanceKey())
        .hasScopeKey(variable2.getScopeKey())
        .hasTenantId(variable2.getTenantId());
  }

  @Test
  public void shouldWriteMigratedEventForLocalVariable() {
    // given
    final String processId = helper.getBpmnProcessId();
    final String targetProcessId = helper.getBpmnProcessId() + "2";

    final var deployment =
        ENGINE
            .deployment()
            .withXmlResource(
                Bpmn.createExecutableProcess(processId)
                    .startEvent()
                    .serviceTask(
                        "A",
                        a ->
                            a.zeebeJobType("A")
                                .zeebeInputExpression(
                                    "\"This is just a string\"", "variable_to_migrate")
                                .zeebeInputExpression(
                                    "{\"this\": \"is\", \"a\": \"context\"}",
                                    "another_variable_to_migrate"))
                    .endEvent()
                    .done())
            .withXmlResource(
                Bpmn.createExecutableProcess(targetProcessId)
                    .startEvent()
                    .serviceTask("A", a -> a.zeebeJobType("A"))
                    .endEvent()
                    .done())
            .deploy();
    final long targetProcessDefinitionKey =
        extractProcessDefinitionKeyByProcessId(deployment, targetProcessId);

    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId).create();

    final var variable =
        RecordingExporter.variableRecords(VariableIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .withName("variable_to_migrate")
            .getFirst()
            .getValue();
    final var variable2 =
        RecordingExporter.variableRecords(VariableIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .withName("another_variable_to_migrate")
            .getFirst()
            .getValue();

    RecordingExporter.jobRecords(JobIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .await();

    // when
    ENGINE
        .processInstance()
        .withInstanceKey(processInstanceKey)
        .migration()
        .withTargetProcessDefinitionKey(targetProcessDefinitionKey)
        .addMappingInstruction("A", "A")
        .migrate();

    // then
    assertThat(
            RecordingExporter.variableRecords(VariableIntent.MIGRATED)
                .withProcessInstanceKey(processInstanceKey)
                .withName("variable_to_migrate")
                .getFirst()
                .getValue())
        .describedAs("Expect that process definition is updated")
        .hasProcessDefinitionKey(targetProcessDefinitionKey)
        .hasBpmnProcessId(targetProcessId)
        .describedAs("Expect that the value is unset to avoid exceeding the max record size")
        .hasValue("null")
        .describedAs("Expect that the other variable data did not change")
        .hasName(variable.getName())
        .hasProcessInstanceKey(variable.getProcessInstanceKey())
        .hasScopeKey(variable.getScopeKey())
        .hasTenantId(variable.getTenantId());
    assertThat(
            RecordingExporter.variableRecords(VariableIntent.MIGRATED)
                .withProcessInstanceKey(processInstanceKey)
                .withName("another_variable_to_migrate")
                .getFirst()
                .getValue())
        .describedAs("Expect that process definition is updated")
        .hasProcessDefinitionKey(targetProcessDefinitionKey)
        .hasBpmnProcessId(targetProcessId)
        .describedAs("Expect that the value is unset to avoid exceeding the max record size")
        .hasValue("null")
        .describedAs("Expect that the other variable data did not change")
        .hasName(variable2.getName())
        .hasProcessInstanceKey(variable2.getProcessInstanceKey())
        .hasScopeKey(variable2.getScopeKey())
        .hasTenantId(variable2.getTenantId());
  }
}
