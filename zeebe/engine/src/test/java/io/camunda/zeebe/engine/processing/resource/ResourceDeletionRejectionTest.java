/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.resource;

import static io.camunda.zeebe.protocol.record.RecordAssert.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceCreationIntent;
import io.camunda.zeebe.test.util.BrokerClassRuleHelper;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import org.junit.Rule;
import org.junit.Test;

public class ResourceDeletionRejectionTest {

  @Rule public final EngineRule engine = EngineRule.singlePartition();
  @Rule public final BrokerClassRuleHelper helper = new BrokerClassRuleHelper();

  @Test
  public void shouldRejectCommandWhenResourceIsNotFound() {
    // given
    final long key = 123L;

    // when
    final var rejection = engine.resourceDeletion().withResourceKey(key).expectRejection().delete();

    // then
    assertThat(rejection)
        .describedAs("Expect resource is not found")
        .hasRejectionType(RejectionType.NOT_FOUND)
        .hasRejectionReason(
            "Expected to delete resource but no resource found with key `%d`".formatted(key));
  }

  @Test
  public void shouldRejectCommandWhenResourceIsAlreadyDeleted() {
    // given
    final var processId = helper.getBpmnProcessId();
    final var processDefinitionKey = deployProcess(processId);
    engine.resourceDeletion().withResourceKey(processDefinitionKey).delete();

    // when
    final var rejection =
        engine.resourceDeletion().withResourceKey(processDefinitionKey).expectRejection().delete();

    // then
    assertThat(rejection)
        .describedAs("Expect resource is not found")
        .hasRejectionType(RejectionType.NOT_FOUND)
        .hasRejectionReason(
            "Expected to delete resource but no resource found with key `%d`"
                .formatted(processDefinitionKey));
  }

  @Test
  public void shouldRejectCreateInstanceCommandWhenOnlyProcessVersionIsDeleted() {
    // given
    final var processId = helper.getBpmnProcessId();
    final var processDefinitionKey = deployProcess(processId);
    engine.resourceDeletion().withResourceKey(processDefinitionKey).delete();

    // when
    engine.processInstance().ofBpmnProcessId(processId).expectRejection().create();

    // then
    final var rejectionRecord =
        RecordingExporter.processInstanceCreationRecords().onlyCommandRejections().getFirst();

    Assertions.assertThat(rejectionRecord)
        .hasIntent(ProcessInstanceCreationIntent.CREATE)
        .hasRejectionType(RejectionType.NOT_FOUND)
        .hasRejectionReason(
            String.format(
                "Expected to find process definition with process ID '%s', but none found",
                processId));
  }

  @Test
  public void shouldRejectCreateInstanceByVersionCommandWhenDeletedProcessVersionIsDeleted() {
    // given
    final var processId = helper.getBpmnProcessId();
    final var firstProcessDefinitionKey = deployProcess(processId);
    deployProcess(processId);
    engine.resourceDeletion().withResourceKey(firstProcessDefinitionKey).delete();

    // when
    engine.processInstance().ofBpmnProcessId(processId).withVersion(1).expectRejection().create();

    // then
    final var rejectionRecord =
        RecordingExporter.processInstanceCreationRecords().onlyCommandRejections().getFirst();

    Assertions.assertThat(rejectionRecord)
        .hasIntent(ProcessInstanceCreationIntent.CREATE)
        .hasRejectionType(RejectionType.NOT_FOUND)
        .hasRejectionReason(
            String.format(
                "Expected to find process definition with process ID '%s' and version '%d', but none found",
                processId, 1));
  }

  @Test
  public void shouldRejectDeletionWhenRunningInstances() {
    // given
    final var processId = helper.getBpmnProcessId();
    final var processDefinitionKey = deployProcess(processId);
    engine.processInstance().ofBpmnProcessId(processId).create();

    // when
    final var rejection =
        engine.resourceDeletion().withResourceKey(processDefinitionKey).expectRejection().delete();

    // then
    assertThat(rejection)
        .describedAs("Expect running instances")
        .hasRejectionType(RejectionType.INVALID_STATE)
        .hasRejectionReason(
            "Expected to delete resource with key `%d` but there are still running instances"
                .formatted(processDefinitionKey));
  }

  private long deployProcess(final String processId) {
    return engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId).startEvent().userTask().endEvent().done())
        .deploy()
        .getValue()
        .getProcessesMetadata()
        .get(0)
        .getProcessDefinitionKey();
  }
}
