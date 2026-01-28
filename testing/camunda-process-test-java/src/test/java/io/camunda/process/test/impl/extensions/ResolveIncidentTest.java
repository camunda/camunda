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
package io.camunda.process.test.impl.extensions;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import io.camunda.client.CamundaClient;
import io.camunda.client.CamundaClientBuilder;
import io.camunda.client.api.JsonMapper;
import io.camunda.client.api.command.ClientException;
import io.camunda.client.api.response.ResolveIncidentResponse;
import io.camunda.client.api.search.enums.IncidentState;
import io.camunda.client.api.search.filter.IncidentFilter;
import io.camunda.client.api.search.response.Incident;
import io.camunda.process.test.api.CamundaClientBuilderFactory;
import io.camunda.process.test.api.CamundaProcessTestContext;
import io.camunda.process.test.api.assertions.IncidentSelectors;
import io.camunda.process.test.impl.client.CamundaManagementClient;
import io.camunda.process.test.impl.extension.CamundaProcessTestContextImpl;
import io.camunda.process.test.impl.runtime.CamundaProcessTestRuntime;
import io.camunda.process.test.utils.DevAwaitBehavior;
import java.util.Collections;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class ResolveIncidentTest {

  private static final Long INCIDENT_KEY = 100L;
  private static final Long JOB_KEY = 200L;
  private static final Long PROCESS_INSTANCE_KEY = 300L;
  private static final String ELEMENT_ID = "test-task";
  private static final String PROCESS_DEFINITION_ID = "test-process";

  @Mock private CamundaProcessTestRuntime camundaProcessTestRuntime;
  @Mock private Consumer<AutoCloseable> clientCreationCallback;
  @Mock private CamundaManagementClient camundaManagementClient;
  @Mock private JsonMapper jsonMapper;
  @Mock private io.camunda.zeebe.client.api.JsonMapper zeebeJsonMapper;

  @Mock private CamundaClientBuilderFactory camundaClientBuilderFactory;
  @Mock private CamundaClientBuilder camundaClientBuilder;

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private CamundaClient camundaClient;

  @Mock private Incident incident;

  @Captor private ArgumentCaptor<Consumer<IncidentFilter>> incidentFilterCaptor;

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private IncidentFilter incidentFilter;

  private CamundaProcessTestContext camundaProcessTestContext;

  @BeforeEach
  void configureMocks() {
    when(camundaProcessTestRuntime.getCamundaClientBuilderFactory())
        .thenReturn(camundaClientBuilderFactory);
    when(camundaClientBuilderFactory.get()).thenReturn(camundaClientBuilder);
    when(camundaClientBuilder.build()).thenReturn(camundaClient);
  }

  @Nested
  class HappyCases {

    @BeforeEach
    void setup() {
      camundaProcessTestContext =
          new CamundaProcessTestContextImpl(
              camundaProcessTestRuntime,
              clientCreationCallback,
              camundaManagementClient,
              DevAwaitBehavior.expectSuccess(),
              jsonMapper,
              zeebeJsonMapper);

      when(camundaClient
              .newIncidentSearchRequest()
              .filter(incidentFilterCaptor.capture())
              .send()
              .join()
              .items())
          .thenReturn(Collections.singletonList(incident));

      when(incident.getIncidentKey()).thenReturn(INCIDENT_KEY);
    }

    @Test
    void shouldResolveIncidentByElementId() {
      // given
      when(incident.getJobKey()).thenReturn(null);
      when(incident.getElementId()).thenReturn(ELEMENT_ID);

      // when
      camundaProcessTestContext.resolveIncident(IncidentSelectors.byElementId(ELEMENT_ID));

      // then
      verify(camundaClient.newResolveIncidentCommand(INCIDENT_KEY)).send();
    }

    @Test
    void shouldResolveIncidentByProcessDefinitionId() {
      // given
      when(incident.getJobKey()).thenReturn(null);
      when(incident.getProcessDefinitionId()).thenReturn(PROCESS_DEFINITION_ID);

      // when
      camundaProcessTestContext.resolveIncident(
          IncidentSelectors.byProcessDefinitionId(PROCESS_DEFINITION_ID));

      // then
      verify(camundaClient.newResolveIncidentCommand(INCIDENT_KEY)).send();
    }

    @Test
    void shouldResolveIncidentByProcessInstanceKey() {
      // given
      when(incident.getJobKey()).thenReturn(null);
      when(incident.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // when
      camundaProcessTestContext.resolveIncident(
          IncidentSelectors.byProcessInstanceKey(PROCESS_INSTANCE_KEY));

      // then
      verify(camundaClient.newResolveIncidentCommand(INCIDENT_KEY)).send();
    }

    @Test
    void shouldResolveIncidentWithJobRetryUpdate() {
      // given
      when(incident.getJobKey()).thenReturn(JOB_KEY);
      when(incident.getElementId()).thenReturn(ELEMENT_ID);

      // when
      camundaProcessTestContext.resolveIncident(IncidentSelectors.byElementId(ELEMENT_ID));

      // then
      verify(camundaClient.newUpdateRetriesCommand(JOB_KEY).retries(1)).send();
      verify(camundaClient.newResolveIncidentCommand(INCIDENT_KEY)).send();
    }

    @Test
    void shouldSearchByElementId() {
      // given
      when(incident.getJobKey()).thenReturn(null);
      when(incident.getElementId()).thenReturn(ELEMENT_ID);

      // when
      camundaProcessTestContext.resolveIncident(IncidentSelectors.byElementId(ELEMENT_ID));

      // then
      incidentFilterCaptor.getValue().accept(incidentFilter);
      verify(incidentFilter).state(IncidentState.ACTIVE);
      verify(incidentFilter).elementId(ELEMENT_ID);

      verifyNoMoreInteractions(incidentFilter);
    }

    @Test
    void shouldSearchByProcessDefinitionId() {
      // given
      when(incident.getJobKey()).thenReturn(null);
      when(incident.getProcessDefinitionId()).thenReturn(PROCESS_DEFINITION_ID);

      // when
      camundaProcessTestContext.resolveIncident(
          IncidentSelectors.byProcessDefinitionId(PROCESS_DEFINITION_ID));

      // then
      incidentFilterCaptor.getValue().accept(incidentFilter);
      verify(incidentFilter).state(IncidentState.ACTIVE);
      verify(incidentFilter).processDefinitionId(PROCESS_DEFINITION_ID);

      verifyNoMoreInteractions(incidentFilter);
    }

    @Test
    void shouldSearchByProcessInstanceKey() {
      // given
      when(incident.getJobKey()).thenReturn(null);
      when(incident.getProcessInstanceKey()).thenReturn(PROCESS_INSTANCE_KEY);

      // when
      camundaProcessTestContext.resolveIncident(
          IncidentSelectors.byProcessInstanceKey(PROCESS_INSTANCE_KEY));

      // then
      incidentFilterCaptor.getValue().accept(incidentFilter);
      verify(incidentFilter).state(IncidentState.ACTIVE);
      verify(incidentFilter).processInstanceKey(PROCESS_INSTANCE_KEY);

      verifyNoMoreInteractions(incidentFilter);
    }

    @Test
    void shouldAwaitUntilIncidentIsPresent() {
      // given
      when(incident.getJobKey()).thenReturn(null);
      when(incident.getElementId()).thenReturn(ELEMENT_ID);
      when(camundaClient
              .newIncidentSearchRequest()
              .filter(incidentFilterCaptor.capture())
              .send()
              .join()
              .items())
          .thenReturn(Collections.emptyList())
          .thenReturn(Collections.singletonList(incident));

      clearInvocations(camundaClient);

      // when
      camundaProcessTestContext.resolveIncident(IncidentSelectors.byElementId(ELEMENT_ID));

      // then
      verify(camundaClient, times(2)).newIncidentSearchRequest();
    }

    @Test
    void shouldRetryResolution() {
      // given
      when(incident.getJobKey()).thenReturn(null);
      when(incident.getElementId()).thenReturn(ELEMENT_ID);
      when(camundaClient.newResolveIncidentCommand(INCIDENT_KEY).send().join())
          .thenThrow(new ClientException("expected"))
          .thenReturn(mock(ResolveIncidentResponse.class));

      clearInvocations(camundaClient);

      // when
      camundaProcessTestContext.resolveIncident(IncidentSelectors.byElementId(ELEMENT_ID));

      // then
      verify(camundaClient, times(2)).newResolveIncidentCommand(INCIDENT_KEY);
    }
  }

  @Nested
  class FailureCases {

    @BeforeEach
    void setup() {
      camundaProcessTestContext =
          new CamundaProcessTestContextImpl(
              camundaProcessTestRuntime,
              clientCreationCallback,
              camundaManagementClient,
              DevAwaitBehavior.expectFailure(),
              jsonMapper,
              zeebeJsonMapper);
    }

    @Test
    void shouldFailIfNoIncidentIsPresent() {
      // given
      when(camundaClient
              .newIncidentSearchRequest()
              .filter(incidentFilterCaptor.capture())
              .send()
              .join()
              .items())
          .thenReturn(Collections.emptyList());

      // when/then
      assertThatThrownBy(
              () ->
                  camundaProcessTestContext.resolveIncident(
                      IncidentSelectors.byElementId(ELEMENT_ID)))
          .isInstanceOf(AssertionError.class)
          .hasMessageContaining(
              "Expected to resolve an incident [elementId: %s] but no incident was found.",
              ELEMENT_ID);
    }
  }
}
