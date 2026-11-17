/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.bpmn.behavior;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.engine.metrics.IncidentMetrics;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.util.ProcessingStateExtension;
import io.camunda.zeebe.protocol.impl.record.value.incident.IncidentRecord;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.record.value.ErrorType;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ProcessingStateExtension.class)
final class BpmnIncidentBehaviorTest {

  private static final long JOB_KEY = 42L;
  private static final long EXISTING_INCIDENT_KEY = 77L;

  /** Injected by {@link ProcessingStateExtension} */
  private MutableProcessingState processingState;

  private StateWriter stateWriter;
  private BpmnIncidentBehavior incidentBehavior;

  @BeforeEach
  void setUp() {
    stateWriter = mock(StateWriter.class);
    final var keyGenerator = mock(KeyGenerator.class);
    when(keyGenerator.nextKey()).thenReturn(999L);
    incidentBehavior =
        new BpmnIncidentBehavior(
            processingState, keyGenerator, stateWriter, mock(IncidentMetrics.class));
  }

  @Test
  void shouldNotCreateSecondIncidentForJobThatAlreadyHasOne() {
    // given - a job that already carries an unresolved incident, as a job whose secret reference
    // failed to resolve does while it waits for an operator
    final var job = new JobRecord().setType("type").setBpmnProcessId("process");
    processingState.getJobState().create(JOB_KEY, job);
    processingState
        .getIncidentState()
        .createIncident(
            EXISTING_INCIDENT_KEY,
            new IncidentRecord()
                .setJobKey(JOB_KEY)
                .setErrorType(ErrorType.SECRET_RESOLUTION_ERROR));

    // when - a second reference of the same job fails too, or the job reaches an activation again
    incidentBehavior.createJobIncident(
        JOB_KEY, job, ErrorType.SECRET_RESOLUTION_ERROR, "failed again");

    // then - nothing is appended: the job to incident index holds one entry per job and is written
    // with an insert, so a second incident would fail the applier rather than replace the first
    verify(stateWriter, never()).appendFollowUpEvent(anyLong(), any(), any());
    assertThat(processingState.getIncidentState().getJobIncidentKey(JOB_KEY))
        .describedAs("the incident the job already had is left alone")
        .isEqualTo(EXISTING_INCIDENT_KEY);
  }
}
