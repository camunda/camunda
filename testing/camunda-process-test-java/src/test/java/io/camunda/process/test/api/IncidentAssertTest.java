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

import static io.camunda.process.test.api.CamundaAssert.assertThatIncident;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.camunda.client.api.search.enums.IncidentErrorType;
import io.camunda.client.api.search.enums.IncidentState;
import io.camunda.client.api.search.response.Incident;
import io.camunda.process.test.api.assertions.IncidentSelectors;
import io.camunda.process.test.impl.assertions.CamundaDataSource;
import io.camunda.process.test.utils.CamundaAssertExpectFailure;
import io.camunda.process.test.utils.CamundaAssertExtension;
import io.camunda.process.test.utils.IncidentBuilder;
import java.util.Arrays;
import java.util.Collections;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith({CamundaAssertExtension.class, MockitoExtension.class})
public class IncidentAssertTest {

  private static final String ELEMENT_ID = "payment";
  private static final long PROCESS_INSTANCE_KEY = 123L;

  @Mock private CamundaDataSource camundaDataSource;

  @BeforeEach
  void configureAssertions() {
    CamundaAssert.initialize(camundaDataSource);
  }

  @ParameterizedTest
  @EnumSource(
      value = IncidentState.class,
      names = {"ACTIVE", "PENDING", "MIGRATED"})
  void shouldAssertIncidentIsActive(final IncidentState state) {
    // given
    when(camundaDataSource.findIncidents(any()))
        .thenReturn(Collections.singletonList(newIncident(ELEMENT_ID, state)));

    // when / then
    assertThatIncident(IncidentSelectors.byElementId(ELEMENT_ID)).isActive();
  }

  @ParameterizedTest
  @EnumSource(
      value = IncidentState.class,
      names = {"RESOLVED", "UNKNOWN", "UNKNOWN_ENUM_VALUE"})
  @CamundaAssertExpectFailure
  void shouldFailIfIncidentIsNotActive(final IncidentState state) {
    // given
    when(camundaDataSource.findIncidents(any()))
        .thenReturn(Collections.singletonList(newIncident(ELEMENT_ID, state)));

    // when / then
    Assertions.assertThatThrownBy(
            () -> assertThatIncident(IncidentSelectors.byElementId(ELEMENT_ID)).isActive())
        .hasMessage(
            "Expected incident [elementId: %s] to be active, but was %s",
            ELEMENT_ID, state.name().toLowerCase());
  }

  @Test
  @CamundaAssertExpectFailure
  void shouldFailIfNoMatchingIncidentExists() {
    // given
    when(camundaDataSource.findIncidents(any()))
        .thenReturn(Collections.singletonList(newIncident("shipping", IncidentState.ACTIVE)));

    // when / then
    Assertions.assertThatThrownBy(
            () -> assertThatIncident(IncidentSelectors.byElementId(ELEMENT_ID)).isActive())
        .hasMessage("No incident [elementId: %s] found", ELEMENT_ID);
  }

  @Test
  void shouldEventuallyFindMatchingIncident() {
    // given
    when(camundaDataSource.findIncidents(any()))
        .thenReturn(Collections.emptyList())
        .thenReturn(Collections.singletonList(newIncident(ELEMENT_ID, IncidentState.ACTIVE)));

    // when / then
    assertThatIncident(IncidentSelectors.byElementId(ELEMENT_ID)).isActive();
  }

  @Test
  void shouldIgnoreNonMatchingIncidents() {
    // given
    when(camundaDataSource.findIncidents(any()))
        .thenReturn(
            Arrays.asList(
                newIncident("shipping", IncidentState.RESOLVED),
                newIncident(ELEMENT_ID, IncidentState.ACTIVE)));

    // when / then
    assertThatIncident(IncidentSelectors.byElementId(ELEMENT_ID)).isActive();
  }

  @Test
  void shouldAssertIncidentIsResolved() {
    // given
    when(camundaDataSource.findIncidents(any()))
        .thenReturn(Collections.singletonList(newIncident(ELEMENT_ID, IncidentState.RESOLVED)));

    // when / then
    assertThatIncident(IncidentSelectors.byElementId(ELEMENT_ID)).isResolved();
  }

  @ParameterizedTest
  @EnumSource(
      value = IncidentState.class,
      names = {"ACTIVE", "PENDING", "MIGRATED", "UNKNOWN", "UNKNOWN_ENUM_VALUE"})
  @CamundaAssertExpectFailure
  void shouldFailIfIncidentIsNotResolved(final IncidentState state) {
    // given
    when(camundaDataSource.findIncidents(any()))
        .thenReturn(Collections.singletonList(newIncident(ELEMENT_ID, state)));

    // when / then
    Assertions.assertThatThrownBy(
            () -> assertThatIncident(IncidentSelectors.byElementId(ELEMENT_ID)).isResolved())
        .hasMessage(
            "Expected incident [elementId: %s] to be resolved, but was %s",
            ELEMENT_ID, state.name().toLowerCase());
  }

  @Test
  void shouldEventuallyAssertIncidentIsResolved() {
    // given
    when(camundaDataSource.findIncidents(any()))
        .thenReturn(Collections.singletonList(newIncident(ELEMENT_ID, IncidentState.ACTIVE)))
        .thenReturn(Collections.singletonList(newIncident(ELEMENT_ID, IncidentState.RESOLVED)));

    // when / then
    assertThatIncident(IncidentSelectors.byElementId(ELEMENT_ID)).isResolved();
  }

  @Test
  void shouldRefreshIncidentForEachChainedAssertion() {
    // given
    when(camundaDataSource.findIncidents(any()))
        .thenReturn(Collections.singletonList(newIncident(ELEMENT_ID, IncidentState.ACTIVE)))
        .thenReturn(Collections.singletonList(newIncident(ELEMENT_ID, IncidentState.RESOLVED)));

    // when / then
    assertThatIncident(IncidentSelectors.byElementId(ELEMENT_ID)).isActive().isResolved();
  }

  @Test
  void shouldChainIncidentAssertions() {
    // given
    when(camundaDataSource.findIncidents(any()))
        .thenReturn(Collections.singletonList(newIncident(ELEMENT_ID, IncidentState.ACTIVE)));

    // when / then
    assertThatIncident(IncidentSelectors.byElementId(ELEMENT_ID))
        .isActive()
        .hasErrorType(IncidentErrorType.JOB_NO_RETRIES)
        .hasErrorMessage("Payment failed")
        .hasElementId(ELEMENT_ID)
        .hasProcessInstanceKey(PROCESS_INSTANCE_KEY);
  }

  @Test
  void shouldAssertIncidentHasErrorType() {
    // given
    when(camundaDataSource.findIncidents(any()))
        .thenReturn(
            Collections.singletonList(
                newIncident(ELEMENT_ID, IncidentState.ACTIVE, IncidentErrorType.JOB_NO_RETRIES)));

    // when / then
    assertThatIncident(IncidentSelectors.byElementId(ELEMENT_ID))
        .hasErrorType(IncidentErrorType.JOB_NO_RETRIES);
  }

  @Test
  @CamundaAssertExpectFailure
  void shouldFailIfIncidentHasDifferentErrorType() {
    // given
    when(camundaDataSource.findIncidents(any()))
        .thenReturn(
            Collections.singletonList(
                newIncident(ELEMENT_ID, IncidentState.ACTIVE, IncidentErrorType.CONDITION_ERROR)));

    // when / then
    Assertions.assertThatThrownBy(
            () ->
                assertThatIncident(IncidentSelectors.byElementId(ELEMENT_ID))
                    .hasErrorType(IncidentErrorType.JOB_NO_RETRIES))
        .hasMessage(
            "Expected incident [elementId: %s] to have error type %s, but was %s",
            ELEMENT_ID, IncidentErrorType.JOB_NO_RETRIES, IncidentErrorType.CONDITION_ERROR);
  }

  @Test
  void shouldEventuallyAssertIncidentHasErrorType() {
    // given
    when(camundaDataSource.findIncidents(any()))
        .thenReturn(
            Collections.singletonList(
                newIncident(ELEMENT_ID, IncidentState.ACTIVE, IncidentErrorType.CONDITION_ERROR)))
        .thenReturn(
            Collections.singletonList(
                newIncident(ELEMENT_ID, IncidentState.ACTIVE, IncidentErrorType.JOB_NO_RETRIES)));

    // when / then
    assertThatIncident(IncidentSelectors.byElementId(ELEMENT_ID))
        .hasErrorType(IncidentErrorType.JOB_NO_RETRIES);
  }

  @Test
  void shouldAssertIncidentHasErrorMessage() {
    // given
    when(camundaDataSource.findIncidents(any()))
        .thenReturn(
            Collections.singletonList(
                newIncident(
                    ELEMENT_ID,
                    IncidentState.ACTIVE,
                    IncidentErrorType.JOB_NO_RETRIES,
                    "Payment failed")));

    // when / then
    assertThatIncident(IncidentSelectors.byElementId(ELEMENT_ID)).hasErrorMessage("Payment failed");
  }

  @Test
  @CamundaAssertExpectFailure
  void shouldFailIfIncidentHasDifferentErrorMessage() {
    // given
    when(camundaDataSource.findIncidents(any()))
        .thenReturn(
            Collections.singletonList(
                newIncident(
                    ELEMENT_ID,
                    IncidentState.ACTIVE,
                    IncidentErrorType.JOB_NO_RETRIES,
                    "Card declined")));

    // when / then
    Assertions.assertThatThrownBy(
            () ->
                assertThatIncident(IncidentSelectors.byElementId(ELEMENT_ID))
                    .hasErrorMessage("Payment failed"))
        .hasMessage(
            "Expected incident [elementId: %s] to have error message '%s', but was '%s'",
            ELEMENT_ID, "Payment failed", "Card declined");
  }

  @Test
  @CamundaAssertExpectFailure
  void shouldFailSafelyIfIncidentHasNullErrorMessage() {
    // given
    when(camundaDataSource.findIncidents(any()))
        .thenReturn(
            Collections.singletonList(
                newIncident(
                    ELEMENT_ID, IncidentState.ACTIVE, IncidentErrorType.JOB_NO_RETRIES, null)));

    // when / then
    Assertions.assertThatThrownBy(
            () ->
                assertThatIncident(IncidentSelectors.byElementId(ELEMENT_ID))
                    .hasErrorMessage("Payment failed"))
        .hasMessage(
            "Expected incident [elementId: %s] to have error message '%s', but was '%s'",
            ELEMENT_ID, "Payment failed", null);
  }

  @Test
  void shouldEventuallyAssertIncidentHasErrorMessage() {
    // given
    when(camundaDataSource.findIncidents(any()))
        .thenReturn(
            Collections.singletonList(
                newIncident(
                    ELEMENT_ID,
                    IncidentState.ACTIVE,
                    IncidentErrorType.JOB_NO_RETRIES,
                    "Card declined")))
        .thenReturn(
            Collections.singletonList(
                newIncident(
                    ELEMENT_ID,
                    IncidentState.ACTIVE,
                    IncidentErrorType.JOB_NO_RETRIES,
                    "Payment failed")));

    // when / then
    assertThatIncident(IncidentSelectors.byElementId(ELEMENT_ID)).hasErrorMessage("Payment failed");
  }

  @Test
  void shouldAssertIncidentHasElementId() {
    // given
    when(camundaDataSource.findIncidents(any()))
        .thenReturn(Collections.singletonList(newIncident(ELEMENT_ID, IncidentState.ACTIVE)));

    // when / then
    assertThatIncident(IncidentSelectors.byProcessInstanceKey(PROCESS_INSTANCE_KEY))
        .hasElementId(ELEMENT_ID);
  }

  @Test
  @CamundaAssertExpectFailure
  void shouldFailIfIncidentHasDifferentElementId() {
    // given
    when(camundaDataSource.findIncidents(any()))
        .thenReturn(Collections.singletonList(newIncident("shipping", IncidentState.ACTIVE)));

    // when / then
    Assertions.assertThatThrownBy(
            () ->
                assertThatIncident(IncidentSelectors.byProcessInstanceKey(PROCESS_INSTANCE_KEY))
                    .hasElementId(ELEMENT_ID))
        .hasMessage(
            "Expected incident [processInstanceKey: %d] to have element ID '%s', but was '%s'",
            PROCESS_INSTANCE_KEY, ELEMENT_ID, "shipping");
  }

  @Test
  void shouldEventuallyAssertIncidentHasElementId() {
    // given
    when(camundaDataSource.findIncidents(any()))
        .thenReturn(Collections.singletonList(newIncident("shipping", IncidentState.ACTIVE)))
        .thenReturn(Collections.singletonList(newIncident(ELEMENT_ID, IncidentState.ACTIVE)));

    // when / then
    assertThatIncident(IncidentSelectors.byProcessInstanceKey(PROCESS_INSTANCE_KEY))
        .hasElementId(ELEMENT_ID);
  }

  @Test
  void shouldAssertIncidentHasProcessInstanceKey() {
    // given
    when(camundaDataSource.findIncidents(any()))
        .thenReturn(Collections.singletonList(newIncident(ELEMENT_ID, IncidentState.ACTIVE)));

    // when / then
    assertThatIncident(IncidentSelectors.byElementId(ELEMENT_ID))
        .hasProcessInstanceKey(PROCESS_INSTANCE_KEY);
  }

  @Test
  @CamundaAssertExpectFailure
  void shouldFailIfIncidentHasDifferentProcessInstanceKey() {
    // given
    when(camundaDataSource.findIncidents(any()))
        .thenReturn(
            Collections.singletonList(
                newIncident(
                    ELEMENT_ID,
                    IncidentState.ACTIVE,
                    IncidentErrorType.JOB_NO_RETRIES,
                    "Payment failed",
                    456L)));

    // when / then
    Assertions.assertThatThrownBy(
            () ->
                assertThatIncident(IncidentSelectors.byElementId(ELEMENT_ID))
                    .hasProcessInstanceKey(PROCESS_INSTANCE_KEY))
        .hasMessage(
            "Expected incident [elementId: %s] to have process instance key %d, but was %d",
            ELEMENT_ID, PROCESS_INSTANCE_KEY, 456L);
  }

  @Test
  void shouldEventuallyAssertIncidentHasProcessInstanceKey() {
    // given
    when(camundaDataSource.findIncidents(any()))
        .thenReturn(
            Collections.singletonList(
                newIncident(
                    ELEMENT_ID,
                    IncidentState.ACTIVE,
                    IncidentErrorType.JOB_NO_RETRIES,
                    "Payment failed",
                    456L)))
        .thenReturn(Collections.singletonList(newIncident(ELEMENT_ID, IncidentState.ACTIVE)));

    // when / then
    assertThatIncident(IncidentSelectors.byElementId(ELEMENT_ID))
        .hasProcessInstanceKey(PROCESS_INSTANCE_KEY);
  }

  private static Incident newIncident(final String elementId, final IncidentState state) {
    return newIncident(elementId, state, IncidentErrorType.JOB_NO_RETRIES);
  }

  private static Incident newIncident(
      final String elementId,
      final IncidentState state,
      final IncidentErrorType incidentErrorType) {
    return newIncident(elementId, state, incidentErrorType, "Payment failed");
  }

  private static Incident newIncident(
      final String elementId,
      final IncidentState state,
      final IncidentErrorType incidentErrorType,
      final String errorMessage) {
    return newIncident(elementId, state, incidentErrorType, errorMessage, PROCESS_INSTANCE_KEY);
  }

  private static Incident newIncident(
      final String elementId,
      final IncidentState state,
      final IncidentErrorType incidentErrorType,
      final String errorMessage,
      final long processInstanceKey) {
    return IncidentBuilder.newActiveIncident(incidentErrorType, errorMessage)
        .setElementId(elementId)
        .setProcessInstanceKey(processInstanceKey)
        .setState(state)
        .build();
  }
}
