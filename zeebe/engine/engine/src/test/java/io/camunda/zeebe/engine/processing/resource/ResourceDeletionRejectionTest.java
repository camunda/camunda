/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.resource;

import static io.camunda.zeebe.protocol.record.RecordAssert.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceCreationIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessIntent;
import io.camunda.zeebe.protocol.record.intent.ResourceDeletionIntent;
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
  public void shouldDrainInsteadOfRejectingDeletionWhenRunningInstances() {
    // given
    final var processId = helper.getBpmnProcessId();
    final var processDefinitionKey = deployProcess(processId);
    engine.processInstance().ofBpmnProcessId(processId).create();

    // when
    final var deletion = engine.resourceDeletion().withResourceKey(processDefinitionKey).delete();

    // then - the deletion is accepted, not rejected
    Assertions.assertThat(deletion).hasIntent(ResourceDeletionIntent.DELETED);

    // and - the definition is marked draining, not physically deleted, while the instance runs
    final var eventsUpToDraining =
        RecordingExporter.processRecords()
            .withProcessDefinitionKey(processDefinitionKey)
            .limit(r -> r.getIntent() == ProcessIntent.DRAINING)
            .asList();
    assertThat(eventsUpToDraining)
        .extracting(Record::getIntent)
        .describedAs("the definition is not deleted while an instance is still running")
        .doesNotContain(ProcessIntent.DELETED)
        .contains(ProcessIntent.DRAINING);
  }

  @Test
  public void shouldAcceptSecondDeletionOfDrainingProcessWithoutRestartingIt() {
    // given - a definition that is draining because it still has a running instance
    final var processId = helper.getBpmnProcessId();
    final var processDefinitionKey = deployProcess(processId);
    engine.processInstance().ofBpmnProcessId(processId).create();
    engine.resourceDeletion().withResourceKey(processDefinitionKey).delete();
    RecordingExporter.processRecords()
        .withIntent(ProcessIntent.DRAINING)
        .withProcessDefinitionKey(processDefinitionKey)
        .await();

    // when - the same definition is deleted a second time while still draining
    final var repeat = engine.resourceDeletion().withResourceKey(processDefinitionKey).delete();

    // then - accepted without effect: no deletion is started for the repeat and the definition is
    // not finalized, so it stays draining until its instance ends
    assertThat(repeat.getIntent()).isEqualTo(ResourceDeletionIntent.DELETED);
    assertThat(
            RecordingExporter.<Boolean>expectNoMatchingRecords(
                records ->
                    RecordingExporter.resourceDeletionRecords()
                        .withIntent(ResourceDeletionIntent.DELETING)
                        .withRecordKey(repeat.getKey())
                        .exists()))
        .describedAs("a repeated delete on a draining definition must not start a deletion")
        .isFalse();
    assertThat(
            RecordingExporter.<Boolean>expectNoMatchingRecords(
                records ->
                    RecordingExporter.processRecords()
                        .withIntent(ProcessIntent.DELETED)
                        .withProcessDefinitionKey(processDefinitionKey)
                        .exists()))
        .describedAs("the definition must stay draining after the repeated delete")
        .isFalse();
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
