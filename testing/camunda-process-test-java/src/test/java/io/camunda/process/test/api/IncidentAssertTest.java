/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.process.test.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.camunda.client.api.response.ProcessInstanceEvent;
import io.camunda.client.api.search.response.Incident;
import io.camunda.client.api.search.response.IncidentErrorType;
import io.camunda.process.test.impl.assertions.CamundaDataSource;
import io.camunda.process.test.utils.IncidentBuilder;
import io.camunda.process.test.utils.ProcessInstanceBuilder;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class IncidentAssertTest {

  private static final long PROCESS_INSTANCE_KEY = 1L;

  @Mock private CamundaDataSource camundaDataSource;
  @Mock private ProcessInstanceEvent processInstanceEvent;

  @BeforeEach
  void configureAssertions() {
    CamundaAssert.initialize(camundaDataSource);
    CamundaAssert.setAssertionInterval(Duration.ZERO);
    CamundaAssert.setAssertionTimeout(Duration.ofSeconds(1));
  }

  @AfterEach
  void resetAssertions() {
    CamundaAssert.setAssertionInterval(CamundaAssert.DEFAULT_ASSERTION_INTERVAL);
    CamundaAssert.setAssertionTimeout(CamundaAssert.DEFAULT_ASSERTION_TIMEOUT);
  }

  @BeforeEach
  void configureMocks() {
    when(camundaDataSource.findProcessInstances(any()))
        .thenReturn(
            Collections.singletonList(
                ProcessInstanceBuilder.newActiveProcessInstance(PROCESS_INSTANCE_KEY).build()));
  }

  private Incident newIncident(final long key, final IncidentErrorType type, final String error) {
    return IncidentBuilder.newActiveIncident(type, error).setIncidentKey(key).build();
  }

  @Nested
  class HasNoIncidents {
    @Test
    void shouldPassIfNoIncidentsFound() {
      // given
      when(camundaDataSource.findIncidents(any())).thenReturn(Collections.emptyList());

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      CamundaAssert.assertThat(processInstanceEvent).hasNoIncidents();
    }

    @Test
    void shouldFailIfActiveIncidentsFound() {
      // given
      final List<Incident> incidents =
          Collections.singletonList(newIncident(1L, IncidentErrorType.CONDITION_ERROR, "error"));
      when(camundaDataSource.findIncidents(any())).thenReturn(incidents);

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      Assertions.assertThatThrownBy(
              () -> CamundaAssert.assertThat(processInstanceEvent).hasNoIncidents())
          .hasMessage(
              "Process instance [key: %d] should have zero incidents, but the following incidents were active:\n"
                  + "\t- '1' (CONDITION_ERROR): error",
              PROCESS_INSTANCE_KEY);
    }

    @Test
    void shouldFormatMultipleActiveIncidents() {
      // given
      final List<Incident> incidents =
          Arrays.asList(
              newIncident(1L, IncidentErrorType.CONDITION_ERROR, "error1"),
              newIncident(2L, IncidentErrorType.CALLED_DECISION_ERROR, "error2"),
              newIncident(3L, IncidentErrorType.DECISION_EVALUATION_ERROR, "error3"));
      when(camundaDataSource.findIncidents(any())).thenReturn(incidents);

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      Assertions.assertThatThrownBy(
              () -> CamundaAssert.assertThat(processInstanceEvent).hasNoIncidents())
          .hasMessage(
              "Process instance [key: %d] should have zero incidents, but the following incidents were active:\n"
                  + "\t- '1' (CONDITION_ERROR): error",
              PROCESS_INSTANCE_KEY);
    }

    @Test
    void shouldPassIfAllIncidentsAreResolved() {}

    @Test
    void shouldEventuallyPassOnceAllIncidentsAreResolved() {}
  }
}
