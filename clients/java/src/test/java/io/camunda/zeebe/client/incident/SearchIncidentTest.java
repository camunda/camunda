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
package io.camunda.zeebe.client.incident;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.client.protocol.rest.IncidentFilterRequest;
import io.camunda.zeebe.client.protocol.rest.IncidentSearchQueryRequest;
import io.camunda.zeebe.client.util.ClientRestTest;
import org.junit.jupiter.api.Test;

public class SearchIncidentTest extends ClientRestTest {

  @Test
  public void shouldSearchIncident() {
    // when
    client.newIncidentQuery().send().join();

    // then
    final IncidentSearchQueryRequest request =
        gatewayService.getLastRequest(IncidentSearchQueryRequest.class);
    assertThat(request.getFilter()).isNull();
  }

  @Test
  public void shouldSearchIncidentWithFullFilters() {
    // when
    client
        .newIncidentQuery()
        .filter(
            f ->
                f.key(1L)
                    .processDefinitionKey(2L)
                    .processInstanceKey(3L)
                    .tenantId("tenant")
                    .flowNodeId("flowNode")
                    .flowNodeInstanceId("flowNodeInstance")
                    .jobKey(4L)
                    .state("state")
                    .type("type")
                    .hasActiveOperation(false))
        .send()
        .join();
    // then
    final IncidentSearchQueryRequest request =
        gatewayService.getLastRequest(IncidentSearchQueryRequest.class);
    final IncidentFilterRequest filter = request.getFilter();
    assertThat(filter.getKey()).isEqualTo(1L);
    assertThat(filter.getProcessDefinitionKey()).isEqualTo(2L);
    assertThat(filter.getProcessInstanceKey()).isEqualTo(3L);
    assertThat(filter.getTenantId()).isEqualTo("tenant");
    assertThat(filter.getFlowNodeId()).isEqualTo("flowNode");
    assertThat(filter.getFlowNodeInstanceId()).isEqualTo("flowNodeInstance");
    assertThat(filter.getJobKey()).isEqualTo(4L);
    assertThat(filter.getState()).isEqualTo("state");
    assertThat(filter.getType()).isEqualTo("type");
    assertThat(filter.getHasActiveOperation()).isFalse();
  }
}
