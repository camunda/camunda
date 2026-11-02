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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import io.camunda.client.api.search.enums.IncidentErrorType;
import io.camunda.client.api.search.enums.IncidentState;
import io.camunda.client.api.search.filter.IncidentFilter;
import io.camunda.client.api.search.response.Incident;
import io.camunda.process.test.api.assertions.IncidentSelector;
import io.camunda.process.test.api.assertions.IncidentSelectors;
import io.camunda.process.test.utils.IncidentBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class IncidentSelectorsTest {

  @Mock private IncidentFilter incidentFilter;

  @Test
  void shouldSelectIncidentByErrorType() {
    // given
    final IncidentSelector selector =
        IncidentSelectors.byErrorType(IncidentErrorType.JOB_NO_RETRIES);

    // when
    selector.applyFilter(incidentFilter);

    // then
    verify(incidentFilter).errorType(IncidentErrorType.JOB_NO_RETRIES);
    assertThat(selector.test(newIncident(IncidentErrorType.JOB_NO_RETRIES, IncidentState.ACTIVE)))
        .isTrue();
    assertThat(selector.test(newIncident(IncidentErrorType.CONDITION_ERROR, IncidentState.ACTIVE)))
        .isFalse();
    assertThat(selector.describe()).isEqualTo("errorType: JOB_NO_RETRIES");
  }

  @Test
  void shouldSelectIncidentByExactState() {
    // given
    final IncidentSelector selector = IncidentSelectors.byState(IncidentState.ACTIVE);

    // when
    selector.applyFilter(incidentFilter);

    // then
    verify(incidentFilter).state(IncidentState.ACTIVE);
    assertThat(selector.test(newIncident(IncidentErrorType.JOB_NO_RETRIES, IncidentState.ACTIVE)))
        .isTrue();
    assertThat(selector.test(newIncident(IncidentErrorType.JOB_NO_RETRIES, IncidentState.PENDING)))
        .isFalse();
    assertThat(selector.describe()).isEqualTo("state: ACTIVE");
  }

  @Test
  void shouldComposeIncidentSelectors() {
    // given
    final IncidentSelector selector =
        IncidentSelectors.byErrorType(IncidentErrorType.JOB_NO_RETRIES)
            .and(IncidentSelectors.byState(IncidentState.ACTIVE));

    // when
    selector.applyFilter(incidentFilter);

    // then
    verify(incidentFilter).errorType(IncidentErrorType.JOB_NO_RETRIES);
    verify(incidentFilter).state(IncidentState.ACTIVE);
    assertThat(selector.test(newIncident(IncidentErrorType.JOB_NO_RETRIES, IncidentState.ACTIVE)))
        .isTrue();
    assertThat(selector.test(newIncident(IncidentErrorType.JOB_NO_RETRIES, IncidentState.RESOLVED)))
        .isFalse();
    assertThat(selector.describe()).isEqualTo("errorType: JOB_NO_RETRIES, state: ACTIVE");
  }

  private static Incident newIncident(
      final IncidentErrorType errorType, final IncidentState state) {
    return IncidentBuilder.newActiveIncident(errorType, "Payment failed").setState(state).build();
  }
}
