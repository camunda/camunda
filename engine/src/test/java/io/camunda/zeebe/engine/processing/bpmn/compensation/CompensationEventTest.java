/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.bpmn.compensation;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.ProcessIntent;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public class CompensationEventTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Test
  public void shouldDeployCompensationBoundaryEvent() {
    ENGINE
        .deployment()
        .withXmlClasspathResource("/compensation/compensation-boundary-event.bpmn")
        .deploy();

    // when
    ENGINE.processInstance().ofBpmnProcessId("compensation-process").create();

    // then
    assertThat(
            RecordingExporter.processRecords().withBpmnProcessId("compensation-process").limit(1))
        .extracting(Record::getIntent)
        .contains(ProcessIntent.CREATED);
  }

  @Test
  public void shouldNotDeployCompensationBoundaryEventWithoutAssociation() {
    final var rejectedDeploy =
        ENGINE
            .deployment()
            .withXmlClasspathResource(
                "/compensation/compensation-boundary-event-no-association.bpmn")
            .expectRejection()
            .deploy();

    // then
    assertThat(rejectedDeploy.getRecordType()).isEqualTo(RecordType.COMMAND_REJECTION);
    assertThat(rejectedDeploy.getRejectionType()).isEqualTo(RejectionType.INVALID_ARGUMENT);
  }
}
