/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processing.deployment;

import static io.zeebe.test.util.TestUtil.waitUntil;
import static io.zeebe.util.buffer.BufferUtil.wrapString;

import io.zeebe.engine.state.deployment.WorkflowState;
import io.zeebe.engine.util.StreamProcessorRule;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.protocol.Protocol;
import io.zeebe.protocol.impl.record.value.deployment.DeploymentRecord;
import io.zeebe.protocol.record.Record;
import io.zeebe.protocol.record.RecordType;
import io.zeebe.protocol.record.intent.DeploymentIntent;
import io.zeebe.protocol.record.value.deployment.ResourceType;
import java.util.List;
import java.util.stream.Collectors;
import org.assertj.core.api.Assertions;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.MockitoAnnotations;

public final class DeploymentCreateProcessorTest {

  @Rule
  public final StreamProcessorRule rule =
      new StreamProcessorRule(Protocol.DEPLOYMENT_PARTITION + 1);

  private WorkflowState workflowState;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    rule.startTypedStreamProcessor(
        (typedRecordProcessors, processingContext) -> {
          final var zeebeState = processingContext.getZeebeState();
          workflowState = zeebeState.getWorkflowState();
          DeploymentEventProcessors.addDeploymentCreateProcessor(
              typedRecordProcessors,
              workflowState,
              (key, partition) -> {},
              Protocol.DEPLOYMENT_PARTITION + 1);
          return typedRecordProcessors;
        });
  }

  @Test
  public void shouldNotRejectTwoCreateDeploymentCommands() {
    // given
    creatingDeployment();

    // when
    waitUntil(
        () -> rule.events().onlyDeploymentRecords().withIntent(DeploymentIntent.CREATED).exists());
    creatingDeployment();

    // then
    waitUntil(() -> rule.events().onlyDeploymentRecords().count() >= 4);

    final List<Record<DeploymentRecord>> collect =
        rule.events().onlyDeploymentRecords().collect(Collectors.toList());
    //
    Assertions.assertThat(collect)
        .extracting(Record::getIntent)
        .containsExactly(
            DeploymentIntent.CREATE,
            DeploymentIntent.CREATED,
            DeploymentIntent.CREATE,
            DeploymentIntent.CREATED);
    //
    Assertions.assertThat(collect)
        .extracting(Record::getRecordType)
        .containsExactly(
            RecordType.COMMAND, RecordType.EVENT, RecordType.COMMAND, RecordType.EVENT);
  }

  @Test
  public void shouldNotRejectTwoCreatingCommandsWithDifferentKeys() {
    // given
    creatingDeployment(4);

    // when
    waitUntil(
        () -> rule.events().onlyDeploymentRecords().withIntent(DeploymentIntent.CREATED).exists());
    creatingDeployment(8);

    // then
    waitUntil(() -> rule.events().onlyDeploymentRecords().count() >= 4);

    final List<Record<DeploymentRecord>> collect =
        rule.events().onlyDeploymentRecords().collect(Collectors.toList());

    Assertions.assertThat(collect)
        .extracting(Record::getIntent)
        .containsExactly(
            DeploymentIntent.CREATE,
            DeploymentIntent.CREATED,
            DeploymentIntent.CREATE,
            DeploymentIntent.CREATED);

    Assertions.assertThat(collect)
        .extracting(Record::getRecordType)
        .containsExactly(
            RecordType.COMMAND, RecordType.EVENT, RecordType.COMMAND, RecordType.EVENT);
  }

  private void creatingDeployment() {
    creatingDeployment(4);
  }

  private void creatingDeployment(final long key) {
    final DeploymentRecord deploymentRecord = creatingDeploymentRecord();

    rule.writeCommand(key, DeploymentIntent.CREATE, deploymentRecord);
  }

  public static DeploymentRecord creatingDeploymentRecord() {
    final BpmnModelInstance modelInstance =
        Bpmn.createExecutableProcess("processId")
            .startEvent()
            .serviceTask("test", task -> task.zeebeJobType("type"))
            .endEvent()
            .done();

    final DeploymentRecord deploymentRecord = new DeploymentRecord();
    deploymentRecord
        .resources()
        .add()
        .setResourceName(wrapString("process.bpmn"))
        .setResource(wrapString(Bpmn.convertToString(modelInstance)))
        .setResourceType(ResourceType.BPMN_XML);

    return deploymentRecord;
  }
}
