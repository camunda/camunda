/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.camunda.process.test.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.camunda.client.api.response.ProcessInstanceEvent;
import io.camunda.client.api.search.enums.IncidentErrorType;
import io.camunda.client.api.search.enums.IncidentState;
import io.camunda.client.api.search.response.Incident;
import io.camunda.process.test.impl.assertions.CamundaDataSource;
import io.camunda.process.test.utils.CamundaAssertExpectFailure;
import io.camunda.process.test.utils.CamundaAssertExtension;
import io.camunda.process.test.utils.IncidentBuilder;
import io.camunda.process.test.utils.ProcessInstanceBuilder;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.junit.jupiter.params.provider.ArgumentsSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith({CamundaAssertExtension.class, MockitoExtension.class})
public class IncidentAssertTest {

  private static final long PROCESS_INSTANCE_KEY = 1L;

  @Mock private CamundaDataSource camundaDataSource;
  @Mock private ProcessInstanceEvent processInstanceEvent;

  @BeforeEach
  void configureAssertions() {
    CamundaAssert.initialize(camundaDataSource);
  }

  @BeforeEach
  void configureMocks() {
    when(camundaDataSource.findProcessInstances(any()))
        .thenReturn(
            Collections.singletonList(
                ProcessInstanceBuilder.newActiveProcessInstance(PROCESS_INSTANCE_KEY).build()));
  }

  private static Incident newActiveIncident(final String id) {
    return newIncident(id, IncidentErrorType.CONDITION_ERROR, IncidentState.ACTIVE, "error");
  }

  private static Incident newResolvedIncident(final String id) {
    return newIncident(id, IncidentErrorType.CONDITION_ERROR, IncidentState.RESOLVED, "error");
  }

  private static Incident newIncident(
      final String id,
      final IncidentErrorType type,
      final IncidentState state,
      final String error) {
    return IncidentBuilder.newActiveIncident(type, error).setElementId(id).setState(state).build();
  }

  static class ActiveIncidentsProvider implements ArgumentsProvider {
    @Override
    public Stream<? extends Arguments> provideArguments(final ExtensionContext context) {
      return Stream.of(
          Arguments.of(
              "Active Incident",
              newIncident("1", IncidentErrorType.CONDITION_ERROR, IncidentState.ACTIVE, "error")),
          Arguments.of(
              "Migrated Incident",
              newIncident("1", IncidentErrorType.CONDITION_ERROR, IncidentState.MIGRATED, "error")),
          Arguments.of(
              "Pending Incident",
              newIncident("1", IncidentErrorType.CONDITION_ERROR, IncidentState.PENDING, "error")));
    }
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
      CamundaAssert.assertThatProcessInstance(processInstanceEvent).hasNoActiveIncidents();
    }

    @ParameterizedTest(name = "{0}")
    @ArgumentsSource(ActiveIncidentsProvider.class)
    @CamundaAssertExpectFailure
    void shouldFailIfActiveIncidentsFound(final String label, final Incident incident) {
      // given
      when(camundaDataSource.findIncidents(any())).thenReturn(Collections.singletonList(incident));

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      Assertions.assertThatThrownBy(
              () ->
                  CamundaAssert.assertThatProcessInstance(processInstanceEvent)
                      .hasNoActiveIncidents())
          .hasMessage(
              "Process instance [key: %d] should have no incidents, but the following incidents were active:\n"
                  + "\t- '1' [type: CONDITION_ERROR] \"error\"",
              PROCESS_INSTANCE_KEY);
    }

    @Test
    @CamundaAssertExpectFailure
    void shouldFormatMultipleActiveIncidents() {
      // given
      final List<Incident> incidents =
          Arrays.asList(
              newIncident("1", IncidentErrorType.CONDITION_ERROR, IncidentState.ACTIVE, "error1"),
              newIncident(
                  "2", IncidentErrorType.CALLED_DECISION_ERROR, IncidentState.ACTIVE, "error2"),
              newIncident(
                  "3",
                  IncidentErrorType.DECISION_EVALUATION_ERROR,
                  IncidentState.ACTIVE,
                  "error3"));
      when(camundaDataSource.findIncidents(any())).thenReturn(incidents);

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      Assertions.assertThatThrownBy(
              () ->
                  CamundaAssert.assertThatProcessInstance(processInstanceEvent)
                      .hasNoActiveIncidents())
          .hasMessage(
              "Process instance [key: %d] should have no incidents, but the following incidents were active:\n"
                  + "\t- '1' [type: CONDITION_ERROR] \"error1\"\n"
                  + "\t- '2' [type: CALLED_DECISION_ERROR] \"error2\"\n"
                  + "\t- '3' [type: DECISION_EVALUATION_ERROR] \"error3\"",
              PROCESS_INSTANCE_KEY);
    }

    @Test
    void shouldPassIfAllIncidentsAreResolved() {
      // given
      when(camundaDataSource.findIncidents(any()))
          .thenReturn(
              Arrays.asList(
                  newResolvedIncident("1"), newResolvedIncident("2"), newResolvedIncident("3")));

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      CamundaAssert.assertThatProcessInstance(processInstanceEvent).hasNoActiveIncidents();
    }

    @Test
    void shouldEventuallyPassOnceAllIncidentsAreResolved() {
      // given
      when(camundaDataSource.findIncidents(any()))
          .thenReturn(Collections.singletonList(newActiveIncident("1")))
          .thenReturn(Collections.singletonList(newActiveIncident("1")))
          .thenReturn(Collections.singletonList(newActiveIncident("1")))
          .thenReturn(Collections.singletonList(newResolvedIncident("1")));

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      CamundaAssert.assertThatProcessInstance(processInstanceEvent).hasNoActiveIncidents();
    }
  }

  @Nested
  class HasActiveIncidents {
    @ParameterizedTest(name = "{0}")
    @ArgumentsSource(ActiveIncidentsProvider.class)
    void shouldPassIfActiveIncidentsFound(final String label, final Incident incident) {
      // given
      when(camundaDataSource.findIncidents(any())).thenReturn(Collections.singletonList(incident));

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      CamundaAssert.assertThatProcessInstance(processInstanceEvent).hasActiveIncidents();
    }

    @Test
    @CamundaAssertExpectFailure
    void shouldFailIfOnlyResolvedIncidentsAreFound() {
      // given
      when(camundaDataSource.findIncidents(any()))
          .thenReturn(Collections.singletonList(newResolvedIncident("1")));

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      Assertions.assertThatThrownBy(
              () ->
                  CamundaAssert.assertThatProcessInstance(processInstanceEvent)
                      .hasActiveIncidents())
          .hasMessage(
              "Process instance [key: %d] should have at least one active incident, but none were found",
              PROCESS_INSTANCE_KEY);
    }

    @Test
    @CamundaAssertExpectFailure
    void shouldFailIfNoIncidentsFound() {
      // given
      when(camundaDataSource.findIncidents(any())).thenReturn(Collections.emptyList());

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      Assertions.assertThatThrownBy(
              () ->
                  CamundaAssert.assertThatProcessInstance(processInstanceEvent)
                      .hasActiveIncidents())
          .hasMessage(
              "Process instance [key: %d] should have at least one active incident, but none were found",
              PROCESS_INSTANCE_KEY);
    }

    @Test
    void shouldEventuallyPassIfActiveIncidentsAreFound() {
      // given
      when(camundaDataSource.findIncidents(any()))
          .thenReturn(Collections.emptyList())
          .thenReturn(Collections.emptyList())
          .thenReturn(Collections.emptyList())
          .thenReturn(Collections.singletonList(newActiveIncident("1")));

      // when
      when(processInstanceEvent.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // then
      CamundaAssert.assertThatProcessInstance(processInstanceEvent).hasActiveIncidents();
    }
  }
}
