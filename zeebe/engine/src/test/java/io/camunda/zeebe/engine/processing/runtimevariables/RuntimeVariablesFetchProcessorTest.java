/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.runtimevariables;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.engine.util.client.RuntimeVariablesClient;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.RuntimeVariableScope;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.util.Map;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class RuntimeVariablesFetchProcessorTest {

  private static final String PROCESS_ID = "runtime-variables";

  @Rule public final EngineRule engine = EngineRule.singlePartition();

  private long processInstanceKey;
  private long taskInstanceKey;
  private RuntimeVariablesClient runtimeVariables;

  @Before
  public void setUp() {
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .serviceTask("task", task -> task.zeebeJobType("test"))
                .endEvent()
                .done())
        .deploy();
    processInstanceKey =
        engine
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariables(Map.of("inherited", "root", "shadowed", "root"))
            .create();
    taskInstanceKey =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementId("task")
            .getFirst()
            .getKey();
    runtimeVariables = engine.runtimeVariables();
  }

  @Test
  public void shouldFetchEffectiveVariablesFromProcessInstance() {
    // when
    final var result = runtimeVariables.withScopeKey(processInstanceKey).fetch();

    // then
    assertThat(result.getVariables())
        .containsExactlyInAnyOrderEntriesOf(Map.of("inherited", "root", "shadowed", "root"));
    assertThat(result.getScope()).isEqualTo(RuntimeVariableScope.EFFECTIVE);
    assertThat(result.getTenantId()).isEqualTo(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
  }

  @Test
  public void shouldFetchInheritedAndShadowedEffectiveVariablesFromElement() {
    // given
    engine
        .variables()
        .ofScope(taskInstanceKey)
        .withLocalSemantic()
        .withDocument(Map.of("local", "task", "shadowed", "task"))
        .update();

    // when
    final var result = runtimeVariables.withScopeKey(taskInstanceKey).fetch();

    // then
    assertThat(result.getVariables())
        .containsExactlyInAnyOrderEntriesOf(
            Map.of("inherited", "root", "local", "task", "shadowed", "task"));
  }

  @Test
  public void shouldFetchOnlyExactLocalVariablesFromElement() {
    // given
    engine
        .variables()
        .ofScope(taskInstanceKey)
        .withLocalSemantic()
        .withDocument(Map.of("local", "task", "shadowed", "task"))
        .update();

    // when
    final var result =
        runtimeVariables
            .withScopeKey(taskInstanceKey)
            .withScope(RuntimeVariableScope.LOCAL)
            .fetch();

    // then
    assertThat(result.getVariables())
        .containsExactlyInAnyOrderEntriesOf(Map.of("local", "task", "shadowed", "task"));
  }

  @Test
  public void shouldRejectMissingScope() {
    // given
    final var missingScopeKey = 123L;

    // when
    final var rejection = runtimeVariables.withScopeKey(missingScopeKey).fetchRejection();

    // then
    Assertions.assertThat(rejection)
        .hasRejectionType(RejectionType.NOT_FOUND)
        .hasRejectionReason("No scope found with key '123'");
  }
}
