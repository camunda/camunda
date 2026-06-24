/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.processinstance;

import static io.camunda.zeebe.protocol.record.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import io.camunda.zeebe.test.util.BrokerClassRuleHelper;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

/**
 * Tests for business ID format validation during process instance creation. The business ID must
 * not exceed the maximum length of 256 characters.
 */
public final class CreateProcessInstanceBusinessIdValidationTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();
  private static final int MAX_BUSINESS_ID_LENGTH = 256;

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Rule public final BrokerClassRuleHelper helper = new BrokerClassRuleHelper();

  @Test
  public void shouldRejectBusinessIdExceedingMaxLength() {
    final String processId = helper.getBpmnProcessId();
    // Business ID exceeding max length (257 characters)
    final String tooLongBusinessId = "a".repeat(MAX_BUSINESS_ID_LENGTH + 1);

    // given
    ENGINE
        .deployment()
        .withXmlResource(Bpmn.createExecutableProcess(processId).startEvent().endEvent().done())
        .deploy();

    // when
    ENGINE
        .processInstance()
        .ofBpmnProcessId(processId)
        .withBusinessId(tooLongBusinessId)
        .expectRejection()
        .create();

    // then
    assertThat(
            RecordingExporter.processInstanceCreationRecords()
                .onlyCommandRejections()
                .withBpmnProcessId(processId)
                .getFirst())
        .hasRejectionType(RejectionType.INVALID_ARGUMENT)
        .hasRejectionReason(
            "Expected to create instance of process with a valid business id, but the business id exceeds the max length of %d."
                .formatted(MAX_BUSINESS_ID_LENGTH));
  }

  @Test
  public void shouldAcceptBusinessIdWithMaxLength() {
    final String processId = helper.getBpmnProcessId();
    // Business ID with exactly max length (256 characters)
    final String maxLengthBusinessId = "a".repeat(MAX_BUSINESS_ID_LENGTH);

    // given
    ENGINE
        .deployment()
        .withXmlResource(Bpmn.createExecutableProcess(processId).startEvent().endEvent().done())
        .deploy();

    // when
    final long processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(processId)
            .withBusinessId(maxLengthBusinessId)
            .create();

    // then
    final Record<ProcessInstanceRecordValue> processActivated =
        RecordingExporter.processInstanceRecords()
            .withProcessInstanceKey(processInstanceKey)
            .withIntent(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withElementType(BpmnElementType.PROCESS)
            .getFirst();

    assertThat(processActivated.getValue().getBusinessId()).isEqualTo(maxLengthBusinessId);
  }

  @Test
  public void shouldAcceptEmptyBusinessId() {
    final String processId = helper.getBpmnProcessId();

    // given
    ENGINE
        .deployment()
        .withXmlResource(Bpmn.createExecutableProcess(processId).startEvent().endEvent().done())
        .deploy();

    // when - create without specifying a business ID
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId).create();

    // then - process instance should be created successfully
    final Record<ProcessInstanceRecordValue> processActivated =
        RecordingExporter.processInstanceRecords()
            .withProcessInstanceKey(processInstanceKey)
            .withIntent(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withElementType(BpmnElementType.PROCESS)
            .getFirst();

    assertThat(processActivated.getValue().getBusinessId()).isEqualTo("");
  }
}
