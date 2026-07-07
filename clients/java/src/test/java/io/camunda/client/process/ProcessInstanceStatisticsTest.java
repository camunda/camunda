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
package io.camunda.client.process;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.http.RequestMethod;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import io.camunda.client.api.statistics.response.ProcessInstanceWaitStateStatistics;
import io.camunda.client.protocol.rest.ProcessInstanceWaitStateStatisticsQueryResult;
import io.camunda.client.protocol.rest.ProcessInstanceWaitStateStatisticsResult;
import io.camunda.client.util.ClientRestTest;
import io.camunda.client.util.RestGatewayService;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

public class ProcessInstanceStatisticsTest extends ClientRestTest {

  public static final long PROCESS_INSTANCE_KEY = 123L;

  @Test
  void shouldGetProcessInstanceElementStatistics() {
    // when
    client.newProcessInstanceElementStatisticsRequest(PROCESS_INSTANCE_KEY).send().join();

    // then
    final LoggedRequest request = RestGatewayService.getLastRequest();
    assertThat(request.getUrl())
        .isEqualTo(
            "/v2/process-instances/" + PROCESS_INSTANCE_KEY + "/statistics/element-instances");
    assertThat(request.getMethod()).isEqualTo(RequestMethod.GET);
    assertThat(request.getBodyAsString()).isEmpty();
  }

  @Test
  void shouldGetProcessInstanceWaitStateStatistics() {
    // when
    client.newProcessInstanceWaitStateStatisticsRequest(PROCESS_INSTANCE_KEY).send().join();

    // then
    final LoggedRequest request = RestGatewayService.getLastRequest();
    assertThat(request.getUrl())
        .isEqualTo("/v2/process-instances/" + PROCESS_INSTANCE_KEY + "/statistics/wait-states");
    assertThat(request.getMethod()).isEqualTo(RequestMethod.GET);
    assertThat(request.getBodyAsString()).isEmpty();
  }

  @Test
  void shouldMapProcessInstanceWaitStateStatisticsResponse() {
    // given
    final ProcessInstanceWaitStateStatisticsQueryResult response =
        new ProcessInstanceWaitStateStatisticsQueryResult();
    response.setItems(Arrays.asList(waitStateItem("task-a", 1L), waitStateItem("task-b", 500L)));
    gatewayService.onProcessInstanceWaitStateStatisticsRequest(PROCESS_INSTANCE_KEY, response);

    // when
    final List<ProcessInstanceWaitStateStatistics> result =
        client.newProcessInstanceWaitStateStatisticsRequest(PROCESS_INSTANCE_KEY).send().join();

    // then
    assertThat(result).hasSize(2);
    assertThat(result.get(0).getElementId()).isEqualTo("task-a");
    assertThat(result.get(0).getWaitingCount()).isEqualTo(1L);
    assertThat(result.get(1).getElementId()).isEqualTo("task-b");
    assertThat(result.get(1).getWaitingCount()).isEqualTo(500L);
  }

  private static ProcessInstanceWaitStateStatisticsResult waitStateItem(
      final String elementId, final long waitingCount) {
    final ProcessInstanceWaitStateStatisticsResult item =
        new ProcessInstanceWaitStateStatisticsResult();
    item.setElementId(elementId);
    item.setWaitingCount(waitingCount);
    return item;
  }
}
