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
package io.camunda.client.decision;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.http.RequestMethod;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import io.camunda.client.protocol.rest.DecisionInstanceResult;
import io.camunda.client.util.ClientRestTest;
import java.time.OffsetDateTime;
import org.instancio.Instancio;
import org.junit.jupiter.api.Test;

public final class GetDecisionInstanceTest extends ClientRestTest {

  @Test
  void shouldGetDecisionInstance() {
    // given
    final String decisionInstanceId = "1-1";
    gatewayService.onDecisionInstanceRequest(
        decisionInstanceId,
        Instancio.create(DecisionInstanceResult.class)
            .decisionEvaluationKey("1")
            .decisionDefinitionKey("2")
            .elementInstanceKey("3")
            .processDefinitionKey("4")
            .processInstanceKey("5")
            .rootDecisionDefinitionKey("6")
            .rootProcessInstanceKey("7")
            .evaluationDate(OffsetDateTime.now().toString()));

    // when
    client.newDecisionInstanceGetRequest(decisionInstanceId).send().join();

    // then
    final LoggedRequest request = gatewayService.getLastRequest();
    assertThat(request.getUrl()).isEqualTo("/v2/decision-instances/" + decisionInstanceId);
    assertThat(request.getMethod()).isEqualTo(RequestMethod.GET);
  }
}
