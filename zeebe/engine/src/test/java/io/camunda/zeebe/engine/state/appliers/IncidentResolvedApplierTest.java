/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.appliers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.engine.processing.job.JobThrowErrorProcessor;
import io.camunda.zeebe.engine.state.immutable.JobState.State;
import io.camunda.zeebe.engine.state.instance.ElementInstance;
import io.camunda.zeebe.engine.state.mutable.MutableElementInstanceState;
import io.camunda.zeebe.engine.state.mutable.MutableIncidentState;
import io.camunda.zeebe.engine.state.mutable.MutableJobState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.util.ProcessingStateExtension;
import io.camunda.zeebe.protocol.impl.record.value.incident.IncidentRecord;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.record.value.ErrorType;
import io.camunda.zeebe.util.buffer.BufferUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ProcessingStateExtension.class)
public class IncidentResolvedApplierTest {

  private MutableProcessingState processingState;
  private MutableIncidentState incidentState;
  private MutableJobState jobStateMock;
  private MutableElementInstanceState elementInstanceStateMock;
  private IncidentResolvedV3Applier applier;

  @BeforeEach
  public void setup() {
    jobStateMock = mock(MutableJobState.class);
    incidentState = processingState.getIncidentState();
    elementInstanceStateMock = mock(MutableElementInstanceState.class);
    applier = new IncidentResolvedV3Applier(incidentState, jobStateMock, elementInstanceStateMock);
  }

  @Test
  void shouldRevertJobElementIdOnIncidentResolution() {
    // given
    final var incidentKey = 1L;
    final var jobKey = 2L;
    final var elementInstanceKey = 3L;
    final var elementId = "elementId";
    final var incidentRecord =
        new IncidentRecord()
            .setErrorType(ErrorType.UNHANDLED_ERROR_EVENT)
            .setJobKey(jobKey)
            .setElementId(BufferUtil.wrapString(elementId));

    final var job =
        new JobRecord()
            .setElementInstanceKey(elementInstanceKey)
            .setElementId(JobThrowErrorProcessor.NO_CATCH_EVENT_FOUND);
    when(jobStateMock.getState(jobKey)).thenReturn(State.ERROR_THROWN);
    when(jobStateMock.getJob(jobKey)).thenReturn(job);

    // when
    applier.applyState(incidentKey, incidentRecord);

    // then
    assertThat(job.getElementId()).isEqualTo(elementId);
  }

  @Test
  void shouldRevertJobElementIdOnIncidentResolutionWhenIncidentContainsWrongElementId() {
    // given
    final var incidentKey = 1L;
    final var jobKey = 2L;
    final var elementInstanceKey = 3L;
    final var oldElementId = JobThrowErrorProcessor.NO_CATCH_EVENT_FOUND;
    final var newElementId = "elementId";
    final var incidentRecord =
        new IncidentRecord()
            .setErrorType(ErrorType.UNHANDLED_ERROR_EVENT)
            .setJobKey(jobKey)
            .setElementId(BufferUtil.wrapString(oldElementId));

    final var job =
        new JobRecord().setElementInstanceKey(elementInstanceKey).setElementId(oldElementId);
    when(jobStateMock.getState(jobKey)).thenReturn(State.ERROR_THROWN);
    when(jobStateMock.getJob(jobKey)).thenReturn(job);

    final var elementInstance = new ElementInstance();
    elementInstance.getValue().setElementId(newElementId);
    when(elementInstanceStateMock.getInstance(elementInstanceKey)).thenReturn(elementInstance);

    // when
    applier.applyState(incidentKey, incidentRecord);

    // then
    assertThat(job.getElementId()).isEqualTo(newElementId);
  }
}
