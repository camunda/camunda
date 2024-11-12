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
package io.camunda.zeebe.client.decision;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.client.protocol.rest.DecisionDefinitionSearchQueryRequest;
import io.camunda.zeebe.client.util.ClientRestTest;
import org.junit.jupiter.api.Test;

public final class SearchDecisionDefinitionTest extends ClientRestTest {

  @Test
  void shouldSearchDecisionDefinition() {
    // when
    client.newDecisionDefinitionQuery().send().join();

    // then
    final DecisionDefinitionSearchQueryRequest request =
        gatewayService.getLastRequest(DecisionDefinitionSearchQueryRequest.class);
    assertThat(request.getFilter()).isNull();
  }

  @Test
  void shouldSearchDecisionDefinitionWithFullFilters() {
    // when
    client
        .newDecisionDefinitionQuery()
        .filter(
            f ->
                f.decisionDefinitionKey(1L)
                    .decisionDefinitionId("ddi")
                    .name("ddm")
                    .decisionRequirementsKey(2L)
                    .decisionRequirementsId("ddri")
                    .version(3)
                    .tenantId("t"))
        .send()
        .join();

    // then
    final DecisionDefinitionSearchQueryRequest request =
        gatewayService.getLastRequest(DecisionDefinitionSearchQueryRequest.class);
    assertThat(request.getFilter().getDecisionDefinitionKey()).isEqualTo(1L);
    assertThat(request.getFilter().getDecisionDefinitionId()).isEqualTo("ddi");
    assertThat(request.getFilter().getName()).isEqualTo("ddm");
    assertThat(request.getFilter().getDecisionRequirementsKey()).isEqualTo(2L);
    assertThat(request.getFilter().getDecisionRequirementsId()).isEqualTo("ddri");
    assertThat(request.getFilter().getVersion()).isEqualTo(3);
    assertThat(request.getFilter().getTenantId()).isEqualTo("t");
  }

  @Test
  void shouldSearchDecisionDefinitionWithFullSorting() {
    // when
    client
        .newDecisionDefinitionQuery()
        .sort(
            s ->
                s.decisionDefinitionKey()
                    .asc()
                    .decisionDefinitionId()
                    .asc()
                    .name()
                    .desc()
                    .decisionRequirementsKey()
                    .asc()
                    .decisionRequirementsId()
                    .asc()
                    .version()
                    .asc()
                    .tenantId()
                    .asc())
        .send()
        .join();

    // then
    final DecisionDefinitionSearchQueryRequest request =
        gatewayService.getLastRequest(DecisionDefinitionSearchQueryRequest.class);
    assertThat(request.getSort().size()).isEqualTo(7);
    assertThat(request.getSort().get(0).getField()).isEqualTo("decisionDefinitionKey");
    assertThat(request.getSort().get(0).getOrder()).isEqualTo("asc");
    assertThat(request.getSort().get(1).getField()).isEqualTo("decisionDefinitionId");
    assertThat(request.getSort().get(1).getOrder()).isEqualTo("asc");
    assertThat(request.getSort().get(2).getField()).isEqualTo("name");
    assertThat(request.getSort().get(2).getOrder()).isEqualTo("desc");
    assertThat(request.getSort().get(3).getField()).isEqualTo("decisionRequirementsKey");
    assertThat(request.getSort().get(3).getOrder()).isEqualTo("asc");
    assertThat(request.getSort().get(4).getField()).isEqualTo("decisionRequirementsId");
    assertThat(request.getSort().get(4).getOrder()).isEqualTo("asc");
    assertThat(request.getSort().get(5).getField()).isEqualTo("version");
    assertThat(request.getSort().get(5).getOrder()).isEqualTo("asc");
    assertThat(request.getSort().get(6).getField()).isEqualTo("tenantId");
    assertThat(request.getSort().get(6).getOrder()).isEqualTo("asc");
  }
}
