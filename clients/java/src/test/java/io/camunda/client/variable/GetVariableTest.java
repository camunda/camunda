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
package io.camunda.client.variable;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.http.RequestMethod;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import io.camunda.client.protocol.rest.VariableResult;
import io.camunda.client.util.ClientRestTest;
import io.camunda.client.util.RestGatewayPaths;
import org.instancio.Instancio;
import org.junit.jupiter.api.Test;

public class GetVariableTest extends ClientRestTest {
  @Test
  void shouldGetVariable() {
    // given
    final long variableKey = 1L;
    gatewayService.onVariableRequest(
        variableKey,
        Instancio.create(VariableResult.class)
            .variableKey("1")
            .processInstanceKey("2")
            .scopeKey("3")
            .rootProcessInstanceKey("4"));

    // when
    client.newVariableGetRequest(variableKey).send().join();

    // then
    final LoggedRequest request = gatewayService.getLastRequest();
    assertThat(request.getUrl()).isEqualTo(RestGatewayPaths.getVariableUrl(variableKey));
    assertThat(request.getMethod()).isEqualTo(RequestMethod.GET);
  }
}
