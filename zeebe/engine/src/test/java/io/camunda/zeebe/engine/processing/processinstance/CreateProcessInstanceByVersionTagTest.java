/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.processinstance;

import static io.camunda.zeebe.protocol.record.RecordAssert.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceCreationIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import io.camunda.zeebe.protocol.record.value.deployment.ProcessMetadataValue;
import io.camunda.zeebe.test.util.BrokerClassRuleHelper;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public final class CreateProcessInstanceByVersionTagTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Rule public final BrokerClassRuleHelper helper = new BrokerClassRuleHelper();

  @Test
  public void shouldCreateProcessInstanceOfNonLatestVersionByVersionTag() {
    // given
    final var processId = helper.getBpmnProcessId();
    final var first = deploy(processId, "a", null);
    deploy(processId, "b", null);

    // when
    final long processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(processId).withVersionTag("a").create();

    // then
    final var process = activatedProcess(processInstanceKey);
    assertThat(process.getBpmnProcessId()).isEqualTo(processId);
    assertThat(process.getProcessDefinitionKey()).isEqualTo(first.getProcessDefinitionKey());
    assertThat(process.getVersion()).isEqualTo(1);
  }

  @Test
  public void shouldCreateLatestDeployedVersionWhenVersionTagIsShared() {
    // given
    final var processId = helper.getBpmnProcessId();
    deploy(processId, "shared", "first");
    final var second = deploy(processId, "shared", "second");

    // when
    final long processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(processId).withVersionTag("shared").create();

    // then
    final var process = activatedProcess(processInstanceKey);
    assertThat(process.getProcessDefinitionKey()).isEqualTo(second.getProcessDefinitionKey());
    assertThat(process.getVersion()).isEqualTo(2);
  }

  @Test
  public void shouldCreateProcessInstanceByVersionTagWithAwaitingResult() {
    // given
    final var processId = helper.getBpmnProcessId();
    final var deployed = deploy(processId, "v1", null);

    // when
    final long processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(processId)
            .withResult()
            .withVersionTag("v1")
            .create();

    // then
    final var process = activatedProcess(processInstanceKey);
    assertThat(process.getProcessDefinitionKey()).isEqualTo(deployed.getProcessDefinitionKey());
  }

  @Test
  public void shouldResolveVersionTagWithinTenant() {
    // given
    final var processId = helper.getBpmnProcessId();
    final var tenantA = "tenant-a-" + processId;
    final var tenantB = "tenant-b-" + processId;
    final var inA = deployForTenant(processId, tenantA);
    final var inB = deployForTenant(processId, tenantB);

    // when
    final long instanceInA =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(processId)
            .withVersionTag("v1")
            .withTenantId(tenantA)
            .create();
    final long instanceInB =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(processId)
            .withVersionTag("v1")
            .withTenantId(tenantB)
            .create();

    // then
    assertThat(activatedProcess(instanceInA).getProcessDefinitionKey())
        .isEqualTo(inA.getProcessDefinitionKey());
    assertThat(activatedProcess(instanceInB).getProcessDefinitionKey())
        .isEqualTo(inB.getProcessDefinitionKey());
  }

  @Test
  public void shouldRejectUnknownVersionTag() {
    // given
    final var processId = helper.getBpmnProcessId();
    deploy(processId, "v1", null);

    // when
    ENGINE
        .processInstance()
        .ofBpmnProcessId(processId)
        .withVersionTag("unknown")
        .expectRejection()
        .create();

    // then
    final var rejection =
        RecordingExporter.processInstanceCreationRecords()
            .onlyCommandRejections()
            .withIntent(ProcessInstanceCreationIntent.CREATE)
            .getFirst();
    assertThat(rejection)
        .hasRejectionType(RejectionType.NOT_FOUND)
        .hasRejectionReason(
            "Expected to find process definition with process ID '%s' and version tag 'unknown', but none found"
                .formatted(processId));
  }

  @Test
  public void shouldRejectVersionTagCombinedWithVersion() {
    // given
    final var processId = helper.getBpmnProcessId();
    deploy(processId, "v1", null);

    // when
    ENGINE
        .processInstance()
        .ofBpmnProcessId(processId)
        .withVersionTag("v1")
        .withVersion(1)
        .expectRejection()
        .create();

    // then
    final var rejection =
        RecordingExporter.processInstanceCreationRecords()
            .onlyCommandRejections()
            .withIntent(ProcessInstanceCreationIntent.CREATE)
            .getFirst();
    assertThat(rejection)
        .hasRejectionType(RejectionType.INVALID_ARGUMENT)
        .hasRejectionReason(
            "Expected to create process instance by either version or version tag, but both were provided: version '1', version tag 'v1'");
  }

  @Test
  public void shouldRejectVersionTagCombinedWithProcessDefinitionKey() {
    // given
    final var deployed = deploy(helper.getBpmnProcessId(), "v1", null);

    // when
    ENGINE
        .processInstance()
        .ofBpmnProcessId("")
        .withProcessDefinitionKey(deployed.getProcessDefinitionKey())
        .withVersionTag("v1")
        .expectRejection()
        .create();

    // then
    final var rejection =
        RecordingExporter.processInstanceCreationRecords()
            .onlyCommandRejections()
            .withIntent(ProcessInstanceCreationIntent.CREATE)
            .getFirst();
    assertThat(rejection)
        .hasRejectionType(RejectionType.INVALID_ARGUMENT)
        .hasRejectionReason(
            "Expected to create process instance by either process definition key or version tag, but both were provided: key '%d', version tag 'v1'"
                .formatted(deployed.getProcessDefinitionKey()));
  }

  private ProcessMetadataValue deploy(
      final String processId, final String versionTag, final String startEventId) {
    final var builder = Bpmn.createExecutableProcess(processId).versionTag(versionTag);
    final var model =
        startEventId == null
            ? builder.startEvent().endEvent().done()
            : builder.startEvent(startEventId).endEvent().done();
    return ENGINE
        .deployment()
        .withXmlResource(model)
        .deploy()
        .getValue()
        .getProcessesMetadata()
        .getFirst();
  }

  private ProcessMetadataValue deployForTenant(final String processId, final String tenantId) {
    return ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId).versionTag("v1").startEvent().endEvent().done())
        .withTenantId(tenantId)
        .deploy()
        .getValue()
        .getProcessesMetadata()
        .getFirst();
  }

  private ProcessInstanceRecordValue activatedProcess(final long processInstanceKey) {
    return RecordingExporter.processInstanceRecords()
        .withProcessInstanceKey(processInstanceKey)
        .withElementType(BpmnElementType.PROCESS)
        .withIntent(ProcessInstanceIntent.ELEMENT_ACTIVATED)
        .getFirst()
        .getValue();
  }
}
