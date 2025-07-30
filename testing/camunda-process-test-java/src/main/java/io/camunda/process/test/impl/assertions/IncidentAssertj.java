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

import static org.apache.commons.lang3.StringUtils.abbreviate;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.api.search.enums.IncidentState;
import io.camunda.client.api.search.filter.IncidentFilter;
import io.camunda.client.api.search.response.Incident;
import io.camunda.process.test.api.CamundaAssertAwaitBehavior;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.assertj.core.api.AbstractAssert;

public class IncidentAssertj extends AbstractAssert<ElementAssertj, String> {

  private static final int MAX_ERROR_MESSAGE_LENGTH = 500;

  private static final List<IncidentState> ACTIVE_INCIDENT_STATES =
      Arrays.asList(IncidentState.ACTIVE, IncidentState.PENDING, IncidentState.MIGRATED);

  private final CamundaDataSource dataSource;
  private final CamundaAssertAwaitBehavior awaitBehavior;

  protected IncidentAssertj(
      final CamundaDataSource dataSource,
      final CamundaAssertAwaitBehavior awaitBehavior,
      final String failureMessagePrefix) {
    super(failureMessagePrefix, IncidentAssertj.class);
    this.dataSource = dataSource;
    this.awaitBehavior = awaitBehavior;
  }

  public void hasNoActiveIncidents(final long processInstanceKey) {
    awaitIncidentAssertion(
        f -> f.processInstanceKey(processInstanceKey),
        (incidents) -> {
          final List<Incident> activeIncidents = activeIncidents(incidents);

          assertThat(activeIncidents)
              .withFailMessage(
                  "%s should have no incidents, but the following incidents were active:\n%s",
                  actual, collectIncidentReports(activeIncidents))
              .isEmpty();
        });
  }

  public void hasActiveIncidents(final long processInstanceKey) {
    awaitIncidentAssertion(
        f -> f.processInstanceKey(processInstanceKey),
        (incidents) -> {
          final List<Incident> activeIncidents = activeIncidents(incidents);

          assertThat(activeIncidents)
              .withFailMessage(
                  "%s should have at least one active incident, but none were found", actual)
              .isNotEmpty();
        });
  }

  private List<Incident> activeIncidents(final List<Incident> incidents) {
    return incidents.stream()
        .filter(i -> ACTIVE_INCIDENT_STATES.contains(i.getState()))
        .collect(Collectors.toList());
  }

  private void awaitIncidentAssertion(
      final Consumer<IncidentFilter> filter, final Consumer<List<Incident>> assertion) {
    awaitBehavior.untilAsserted(() -> dataSource.findIncidents(filter), assertion);
  }

  private String collectIncidentReports(final List<Incident> incidents) {
    return incidents.stream()
        .map(
            i ->
                String.format(
                    "\t- '%s' [type: %s] \"%s\"",
                    i.getElementId(),
                    i.getErrorType(),
                    abbreviate(i.getErrorMessage(), MAX_ERROR_MESSAGE_LENGTH)))
        .collect(Collectors.joining("\n"));
  }
}
