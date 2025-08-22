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
import io.camunda.client.protocol.rest.ProcessInstanceSequenceFlowsQueryResult;
import io.camunda.client.util.ClientRestTest;
import io.camunda.client.util.RestGatewayPaths;
import io.camunda.client.util.RestGatewayService;
import org.instancio.Instancio;
import org.junit.jupiter.api.Test;

public class QueryProcessInstanceSequenceFlowsTest extends ClientRestTest {

  @Test
  public void shouldGetProcessInstanceSequenceFlowsByKey() {
    // given
    final long processInstanceKey = 123L;
    gatewayService.onProcessInstanceSequenceFlowsRequest(
        processInstanceKey, Instancio.create(ProcessInstanceSequenceFlowsQueryResult.class));

    // when
    client.newProcessInstanceSequenceFlowsRequest(processInstanceKey).send().join();

    // then
    final LoggedRequest request = RestGatewayService.getLastRequest();
    assertThat(request.getMethod()).isEqualTo(RequestMethod.GET);
    assertThat(request.getUrl())
        .isEqualTo(RestGatewayPaths.getProcessInstanceSequenceFlowsUrl(processInstanceKey));
    assertThat(request.getBodyAsString()).isEmpty();
  }
}
