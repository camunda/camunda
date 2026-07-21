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
package io.camunda.client.impl.search.response;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.camunda.client.api.JsonMapper;
import io.camunda.client.protocol.rest.DecisionDefinitionResult;
import io.camunda.client.protocol.rest.IncidentResult;
import io.camunda.client.protocol.rest.ProcessInstanceResult;
import io.camunda.client.protocol.rest.UserTaskResult;
import io.camunda.client.protocol.rest.VariableResult;
import io.camunda.client.protocol.rest.VariableSearchResult;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

final class UpdateMetadataResponseTest {

  private static final String UPDATED_BY = "demo";
  private static final String UPDATED_AT_VALUE = "2026-07-21T12:34:56.789Z";
  private static final OffsetDateTime UPDATED_AT = OffsetDateTime.parse(UPDATED_AT_VALUE);

  @Test
  void shouldMapUserTaskUpdateMetadata() {
    final UserTaskImpl response =
        new UserTaskImpl(new UserTaskResult().updatedBy(UPDATED_BY).updatedAt(UPDATED_AT_VALUE));

    assertThat(response.getUpdatedBy()).isEqualTo(UPDATED_BY);
    assertThat(response.getUpdatedAt()).isEqualTo(UPDATED_AT);
  }

  @Test
  void shouldMapProcessInstanceUpdateMetadata() {
    final ProcessInstanceImpl response =
        new ProcessInstanceImpl(
            new ProcessInstanceResult().updatedBy(UPDATED_BY).updatedAt(UPDATED_AT_VALUE));

    assertThat(response.getUpdatedBy()).isEqualTo(UPDATED_BY);
    assertThat(response.getUpdatedAt()).isEqualTo(UPDATED_AT);
  }

  @Test
  void shouldMapVariableSearchUpdateMetadata() {
    final VariableImpl response =
        new VariableImpl(
            new VariableSearchResult().updatedBy(UPDATED_BY).updatedAt(UPDATED_AT_VALUE),
            mock(JsonMapper.class));

    assertThat(response.getUpdatedBy()).isEqualTo(UPDATED_BY);
    assertThat(response.getUpdatedAt()).isEqualTo(UPDATED_AT);
  }

  @Test
  void shouldMapVariableGetUpdateMetadata() {
    final VariableImpl response =
        new VariableImpl(
            new VariableResult().updatedBy(UPDATED_BY).updatedAt(UPDATED_AT_VALUE),
            mock(JsonMapper.class));

    assertThat(response.getUpdatedBy()).isEqualTo(UPDATED_BY);
    assertThat(response.getUpdatedAt()).isEqualTo(UPDATED_AT);
  }

  @Test
  void shouldMapIncidentUpdateMetadataAndIncludeItInValueSemantics() {
    final IncidentResult result =
        new IncidentResult().updatedBy(UPDATED_BY).updatedAt(UPDATED_AT_VALUE);
    final IncidentImpl response = new IncidentImpl(result);
    final IncidentImpl sameResponse = new IncidentImpl(result);
    final IncidentImpl differentResponse =
        new IncidentImpl(
            new IncidentResult().updatedBy("another-user").updatedAt(UPDATED_AT_VALUE));

    assertThat(response.getUpdatedBy()).isEqualTo(UPDATED_BY);
    assertThat(response.getUpdatedAt()).isEqualTo(UPDATED_AT);
    assertThat(response).isEqualTo(sameResponse).hasSameHashCodeAs(sameResponse);
    assertThat(response).isNotEqualTo(differentResponse);
  }

  @Test
  void shouldMapDecisionDefinitionUpdateMetadataAndIncludeItInValueSemantics() {
    final DecisionDefinitionResult result = decisionDefinitionResult(UPDATED_BY);
    final DecisionDefinitionImpl response = new DecisionDefinitionImpl(result);
    final DecisionDefinitionImpl sameResponse = new DecisionDefinitionImpl(result);
    final DecisionDefinitionImpl differentResponse =
        new DecisionDefinitionImpl(decisionDefinitionResult("another-user"));

    assertThat(response.getUpdatedBy()).isEqualTo(UPDATED_BY);
    assertThat(response.getUpdatedAt()).isEqualTo(UPDATED_AT);
    assertThat(response).isEqualTo(sameResponse).hasSameHashCodeAs(sameResponse);
    assertThat(response).isNotEqualTo(differentResponse);
    assertThat(response.toString())
        .contains("updatedBy='" + UPDATED_BY + "'")
        .contains("updatedAt=" + UPDATED_AT);
  }

  @Test
  void shouldMapNullUpdateMetadata() {
    final ProcessInstanceImpl response = new ProcessInstanceImpl(new ProcessInstanceResult());

    assertThat(response.getUpdatedBy()).isNull();
    assertThat(response.getUpdatedAt()).isNull();
  }

  private static DecisionDefinitionResult decisionDefinitionResult(final String updatedBy) {
    return new DecisionDefinitionResult()
        .decisionDefinitionId("decision")
        .name("Decision")
        .version(1)
        .decisionDefinitionKey("1")
        .decisionRequirementsId("requirements")
        .decisionRequirementsKey("2")
        .decisionRequirementsName("Requirements")
        .decisionRequirementsVersion(1)
        .tenantId("<default>")
        .updatedBy(updatedBy)
        .updatedAt(UPDATED_AT_VALUE);
  }
}
