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
package io.camunda.process.test.impl.assertions;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.api.search.enums.IncidentErrorType;
import io.camunda.client.api.search.enums.IncidentState;
import io.camunda.client.api.search.response.Incident;
import io.camunda.process.test.api.CamundaAssertAwaitBehavior;
import io.camunda.process.test.api.assertions.IncidentAssert;
import io.camunda.process.test.api.assertions.IncidentSelector;
import java.time.Duration;
import java.util.Optional;
import java.util.function.Consumer;
import org.assertj.core.api.AbstractAssert;

public class IncidentAssertj extends AbstractAssert<IncidentAssertj, IncidentSelector>
    implements IncidentAssert {

  private final CamundaDataSource dataSource;
  private CamundaAssertAwaitBehavior awaitBehavior;

  public IncidentAssertj(
      final CamundaDataSource dataSource,
      final CamundaAssertAwaitBehavior awaitBehavior,
      final IncidentSelector incidentSelector) {
    super(incidentSelector, IncidentAssert.class);
    this.dataSource = dataSource;
    this.awaitBehavior = awaitBehavior;
  }

  @Override
  public IncidentAssert withAssertionTimeout(final Duration assertionTimeout) {
    awaitBehavior = awaitBehavior.withAssertionTimeout(assertionTimeout);
    return this;
  }

  @Override
  public IncidentAssert isActive() {
    awaitIncident(
        incident ->
            assertThat(IncidentStateSupport.isActive(incident.getState()))
                .withFailMessage(
                    "Expected incident [%s] to be active, but was %s",
                    actual.describe(), formatState(incident.getState()))
                .isTrue());
    return this;
  }

  @Override
  public IncidentAssert isResolved() {
    awaitIncident(
        incident ->
            assertThat(incident.getState())
                .withFailMessage(
                    "Expected incident [%s] to be resolved, but was %s",
                    actual.describe(), formatState(incident.getState()))
                .isEqualTo(IncidentState.RESOLVED));
    return this;
  }

  @Override
  public IncidentAssert hasErrorType(final IncidentErrorType errorType) {
    awaitIncident(
        incident ->
            assertThat(incident.getErrorType())
                .withFailMessage(
                    "Expected incident [%s] to have error type %s, but was %s",
                    actual.describe(), errorType, incident.getErrorType())
                .isEqualTo(errorType));
    return this;
  }

  @Override
  public IncidentAssert hasErrorMessage(final String errorMessage) {
    awaitIncident(
        incident ->
            assertThat(incident.getErrorMessage())
                .withFailMessage(
                    "Expected incident [%s] to have error message '%s', but was '%s'",
                    actual.describe(), errorMessage, incident.getErrorMessage())
                .isEqualTo(errorMessage));
    return this;
  }

  @Override
  public IncidentAssert hasElementId(final String elementId) {
    awaitIncident(
        incident ->
            assertThat(incident.getElementId())
                .withFailMessage(
                    "Expected incident [%s] to have element ID '%s', but was '%s'",
                    actual.describe(), elementId, incident.getElementId())
                .isEqualTo(elementId));
    return this;
  }

  @Override
  public IncidentAssert hasProcessInstanceKey(final long processInstanceKey) {
    awaitIncident(
        incident ->
            assertThat(incident.getProcessInstanceKey())
                .withFailMessage(
                    "Expected incident [%s] to have process instance key %d, but was %d",
                    actual.describe(), processInstanceKey, incident.getProcessInstanceKey())
                .isEqualTo(processInstanceKey));
    return this;
  }

  private void awaitIncident(final Consumer<Incident> assertion) {
    awaitBehavior.untilAsserted(
        () -> dataSource.findIncidents(actual::applyFilter),
        incidents -> {
          final Optional<Incident> incident = incidents.stream().filter(actual::test).findFirst();

          assertThat(incident)
              .withFailMessage("No incident [%s] found", actual.describe())
              .isPresent();

          assertion.accept(incident.get());
        });
  }

  private static String formatState(final IncidentState state) {
    return state == null ? "unknown" : state.name().toLowerCase();
  }
}
