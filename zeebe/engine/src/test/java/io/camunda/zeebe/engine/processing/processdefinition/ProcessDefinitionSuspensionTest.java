/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.processdefinition;

import static io.camunda.zeebe.protocol.record.RecordAssert.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessDefinitionSuspensionIntent;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.test.util.BrokerClassRuleHelper;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import org.junit.Rule;
import org.junit.Test;

public class ProcessDefinitionSuspensionTest {

  @Rule public final EngineRule engine = EngineRule.singlePartition();
  @Rule public final BrokerClassRuleHelper helper = new BrokerClassRuleHelper();

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  private long deployProcess(final String processId) {
    return engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .serviceTask("task", t -> t.zeebeJobType("task"))
                .endEvent()
                .done())
        .deploy()
        .getValue()
        .getProcessesMetadata()
        .getFirst()
        .getProcessDefinitionKey();
  }

  @Test
  public void shouldSuspendProcessDefinition() {
    // given
    final var processId = helper.getBpmnProcessId();
    final long key = deployProcess(processId);

    // when
    final var suspended = engine.processDefinitionSuspension().withKey(key).suspend();

    // then
    assertThat(suspended).hasIntent(ProcessDefinitionSuspensionIntent.SUSPENDED);
    assertThat(suspended.getValue().getProcessDefinitionKey()).isEqualTo(key);
    assertThat(suspended.getValue().getBpmnProcessId()).isEqualTo(processId);
    assertThat(suspended.getValue().getVersion()).isEqualTo(1);
    assertThat(suspended.getValue().getTenantId()).isEqualTo(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
  }

  @Test
  public void shouldResumeProcessDefinition() {
    // given
    final long key = deployProcess(helper.getBpmnProcessId());
    engine.processDefinitionSuspension().withKey(key).suspend();

    // when
    final var resumed = engine.processDefinitionSuspension().withKey(key).resume();

    // then
    assertThat(resumed).hasIntent(ProcessDefinitionSuspensionIntent.RESUMED);
    assertThat(resumed.getValue().getProcessDefinitionKey()).isEqualTo(key);
  }

  @Test
  public void shouldRejectSuspendOfUnknownKey() {
    // when
    final var rejection =
        engine.processDefinitionSuspension().withKey(123456L).expectRejection().suspend();

    // then
    assertThat(rejection)
        .hasRejectionType(RejectionType.NOT_FOUND)
        .hasRejectionReason(
            "Expected to suspend process definition with key '123456', but no such process definition was found");
  }

  @Test
  public void shouldRejectSuspendWhenAlreadySuspended() {
    // given
    final long key = deployProcess(helper.getBpmnProcessId());
    engine.processDefinitionSuspension().withKey(key).suspend();

    // when
    final var rejection =
        engine.processDefinitionSuspension().withKey(key).expectRejection().suspend();

    // then
    assertThat(rejection)
        .hasRejectionType(RejectionType.INVALID_STATE)
        .hasRejectionReason(
            "Expected to suspend process definition with key '%d', but it is already suspended"
                .formatted(key));
  }

  @Test
  public void shouldRejectResumeWhenNotSuspended() {
    // given
    final long key = deployProcess(helper.getBpmnProcessId());

    // when
    final var rejection =
        engine.processDefinitionSuspension().withKey(key).expectRejection().resume();

    // then
    assertThat(rejection)
        .hasRejectionType(RejectionType.INVALID_STATE)
        .hasRejectionReason(
            "Expected to resume process definition with key '%d', but it is not suspended"
                .formatted(key));
  }

  @Test
  public void shouldRejectSuspendOfDrainingVersion() {
    // given
    final var processId = helper.getBpmnProcessId();
    final long key = deployProcess(processId);
    engine.processInstance().ofBpmnProcessId(processId).create();
    RecordingExporter.jobRecords(JobIntent.CREATED).withType("task").await();
    engine.resourceDeletion().withResourceKey(key).delete();

    // when
    final var rejection =
        engine.processDefinitionSuspension().withKey(key).expectRejection().suspend();

    // then
    assertThat(rejection)
        .hasRejectionType(RejectionType.INVALID_STATE)
        .hasRejectionReason(
            "Expected to suspend process definition with key '%d', but it is being deleted"
                .formatted(key));
  }
}
