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

import com.github.tomakehurst.wiremock.http.RequestMethod;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import io.camunda.zeebe.client.util.ClientRestTest;
import org.junit.jupiter.api.Test;

public final class GetDecisionDefinitionXmlTest extends ClientRestTest {

  @Test
  void shouldGetDecisionDefinitionXml() {
    // when
    final long decisionDefinitionKey = 1L;
    client.newDecisionDefinitionGetXmlRequest(decisionDefinitionKey).send().join();

    // then
    final LoggedRequest request = gatewayService.getLastRequest();
    assertThat(request.getUrl())
        .isEqualTo("/v2/decision-definitions/" + decisionDefinitionKey + "/xml");
    assertThat(request.getMethod()).isEqualTo(RequestMethod.GET);
  }
}
