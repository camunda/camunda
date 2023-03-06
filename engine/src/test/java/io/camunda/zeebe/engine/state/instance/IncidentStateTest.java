/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.state.instance;

import static io.camunda.zeebe.util.buffer.BufferUtil.wrapString;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.state.immutable.IncidentState;
import io.camunda.zeebe.engine.state.mutable.MutableElementInstanceState;
import io.camunda.zeebe.engine.state.mutable.MutableIncidentState;
import io.camunda.zeebe.engine.state.mutable.MutableJobState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.util.ProcessingStateRule;
import io.camunda.zeebe.protocol.impl.record.value.incident.IncidentRecord;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.ErrorType;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public final class IncidentStateTest {

  @Rule public final ProcessingStateRule stateRule = new ProcessingStateRule();

  private MutableIncidentState incidentState;
  private MutableElementInstanceState elementInstanceState;
  private MutableJobState jobState;
  private MutableProcessingState processingState;

  @Before
  public void setUp() {
    processingState = stateRule.getProcessingState();
    elementInstanceState = processingState.getElementInstanceState();
    jobState = processingState.getJobState();
    incidentState = processingState.getIncidentState();
  }

  @Test
  public void shouldCreateProcessIncident() {
    // given
    final IncidentRecord expectedRecord = createProcessInstanceIncident();

    // when
    incidentState.createIncident(5_000, expectedRecord);

    // then
    final IncidentRecord storedRecord = incidentState.getIncidentRecord(5_000);
    assertIncident(expectedRecord, storedRecord);
  }

  @Test
  public void shouldFindIncidentByElementInstanceKey() {
    // given
    final IncidentRecord expectedRecord = createProcessInstanceIncident();
    incidentState.createIncident(5_000, expectedRecord);

    // when
    final long processInstanceIncidentKey = incidentState.getProcessInstanceIncidentKey(1234);

    // then
    assertThat(processInstanceIncidentKey).isEqualTo(5_000);
    final IncidentRecord storedRecord = incidentState.getIncidentRecord(processInstanceIncidentKey);
    assertIncident(expectedRecord, storedRecord);
  }

  @Test
  public void shouldNotFindIncidentByElementInstanceKey() {
    // given
    final IncidentRecord expectedRecord = createJobIncident();
    incidentState.createIncident(5_000, expectedRecord);

    // when
    final long processInstanceIncidentKey = incidentState.getProcessInstanceIncidentKey(1234);

    // then
    assertThat(processInstanceIncidentKey).isEqualTo(IncidentState.MISSING_INCIDENT);
  }

  @Test
  public void shouldDeleteProcessInstanceIncident() {
    // given
    final IncidentRecord expectedRecord = createProcessInstanceIncident();
    incidentState.createIncident(5_000, expectedRecord);

    // when
    incidentState.deleteIncident(5_000);

    // then
    final IncidentRecord incidentRecord = incidentState.getIncidentRecord(5_000);
    assertThat(incidentRecord).isNull();

    final long processInstanceIncidentKey = incidentState.getProcessInstanceIncidentKey(1234);
    assertThat(processInstanceIncidentKey).isEqualTo(IncidentState.MISSING_INCIDENT);
  }

  @Test
  public void shouldCreateJobIncident() {
    // given
    final IncidentRecord expectedRecord = createJobIncident();

    // when
    incidentState.createIncident(5_000, expectedRecord);

    // then
    final IncidentRecord storedRecord = incidentState.getIncidentRecord(5_000);
    assertIncident(expectedRecord, storedRecord);
  }

  @Test
  public void shouldFindIncidentByJobKey() {
    // given
    final IncidentRecord expectedRecord = createJobIncident();
    incidentState.createIncident(5_000, expectedRecord);

    // when
    final long jobIncidentKey = incidentState.getJobIncidentKey(1234);

    // then
    assertThat(jobIncidentKey).isEqualTo(5_000);
    final IncidentRecord storedRecord = incidentState.getIncidentRecord(jobIncidentKey);
    assertIncident(expectedRecord, storedRecord);
  }

  @Test
  public void shouldNotFindIncidentByJobKey() {
    // given
    final IncidentRecord expectedRecord = createProcessInstanceIncident();
    incidentState.createIncident(5_000, expectedRecord);

    // when
    final long jobIncidentKey = incidentState.getJobIncidentKey(1234);

    // then
    assertThat(jobIncidentKey).isEqualTo(IncidentState.MISSING_INCIDENT);
  }

  @Test
  public void shouldDeleteJobIncident() {
    // given
    final IncidentRecord expectedRecord = createJobIncident();
    incidentState.createIncident(5_000, expectedRecord);

    // when
    incidentState.deleteIncident(5_000);

    // then
    final IncidentRecord incidentRecord = incidentState.getIncidentRecord(5_000);
    assertThat(incidentRecord).isNull();

    final long jobIncidentKey = incidentState.getJobIncidentKey(1234);
    assertThat(jobIncidentKey).isEqualTo(IncidentState.MISSING_INCIDENT);
  }

  @Test
  public void shouldNotOverwritePreviousRecord() {
    // given
    final long key = 1L;
    final IncidentRecord writtenRecord = createJobIncident();

    // when
    incidentState.createIncident(key, writtenRecord);
    writtenRecord.setJobKey(2048);

    // then
    final IncidentRecord readRecord = incidentState.getIncidentRecord(1L);
    assertThat(readRecord.getJobKey()).isNotEqualTo(writtenRecord.getJobKey()).isEqualTo(1234);
    assertThat(writtenRecord.getJobKey()).isEqualTo(2048);
  }

  public IncidentRecord createJobIncident() {
    jobState.create(1234, new JobRecord().setType("test"));

    final IncidentRecord expectedRecord = new IncidentRecord();
    expectedRecord.setJobKey(1234);
    expectedRecord.setErrorMessage("Error because of error");
    expectedRecord.setErrorType(ErrorType.EXTRACT_VALUE_ERROR);
    return expectedRecord;
  }

  public IncidentRecord createProcessInstanceIncident() {
    elementInstanceState.createInstance(
        new ElementInstance(
            1234, ProcessInstanceIntent.ELEMENT_ACTIVATED, new ProcessInstanceRecord()));

    final IncidentRecord expectedRecord = new IncidentRecord();
    expectedRecord.setElementInstanceKey(1234);
    expectedRecord.setBpmnProcessId(wrapString("process"));
    expectedRecord.setElementId(wrapString("process"));
    expectedRecord.setProcessInstanceKey(4321);
    expectedRecord.setErrorMessage("Error because of error");
    expectedRecord.setErrorType(ErrorType.EXTRACT_VALUE_ERROR);
    return expectedRecord;
  }

  public void assertIncident(
      final IncidentRecord expectedRecord, final IncidentRecord storedRecord) {

    assertThat(expectedRecord.getJobKey()).isEqualTo(storedRecord.getJobKey());
    assertThat(expectedRecord.getElementInstanceKey())
        .isEqualTo(storedRecord.getElementInstanceKey());
    assertThat(expectedRecord.getBpmnProcessIdBuffer())
        .isEqualTo(storedRecord.getBpmnProcessIdBuffer());
    assertThat(expectedRecord.getElementIdBuffer()).isEqualTo(storedRecord.getElementIdBuffer());

    assertThat(expectedRecord.getErrorMessageBuffer())
        .isEqualTo(storedRecord.getErrorMessageBuffer());
    assertThat(expectedRecord.getErrorType()).isEqualTo(storedRecord.getErrorType());
  }
}
