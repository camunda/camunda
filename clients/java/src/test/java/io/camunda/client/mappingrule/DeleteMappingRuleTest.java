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
package io.camunda.client.mappingrule;

import static io.camunda.client.impl.http.HttpClientFactory.REST_API_PATH;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.http.RequestMethod;
import io.camunda.client.api.command.ProblemException;
import io.camunda.client.protocol.rest.ProblemDetail;
import io.camunda.client.util.ClientRestTest;
import io.camunda.client.util.RestGatewayService;
import org.junit.jupiter.api.Test;

public class DeleteMappingRuleTest extends ClientRestTest {

  private static final String MAPPING_RULE_ID = "mappingRuleId";

  @Test
  void shouldDeleteMappingRule() {
    // when
    client.newDeleteMappingRuleCommand(MAPPING_RULE_ID).send().join();

    // then
    final String requestPath = RestGatewayService.getLastRequest().getUrl();
    final RequestMethod method = RestGatewayService.getLastRequest().getMethod();
    assertThat(requestPath).isEqualTo(REST_API_PATH + "/mapping-rules/" + MAPPING_RULE_ID);
    assertThat(method).isEqualTo(RequestMethod.DELETE);
  }

  @Test
  void shouldRaiseExceptionOnNullMappingRuleId() {
    // when / then
    assertThatThrownBy(() -> client.newDeleteMappingRuleCommand(null).send().join())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("mappingRuleId must not be null");
  }

  @Test
  void shouldRaiseExceptionOnEmptyMappingRuleId() {
    // when / then
    assertThatThrownBy(() -> client.newDeleteMappingRuleCommand("").send().join())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("mappingRuleId must not be empty");
  }

  @Test
  void shouldRaiseExceptionOnNotFoundMappingRule() {
    // given
    gatewayService.errorOnRequest(
        REST_API_PATH + "/mapping-rules/" + MAPPING_RULE_ID,
        () -> new ProblemDetail().title("Not Found").status(404));

    // when / then
    assertThatThrownBy(() -> client.newDeleteMappingRuleCommand(MAPPING_RULE_ID).send().join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("Failed with code 404: 'Not Found'");
  }

  @Test
  void shouldRaiseExceptionOnForbiddenRequest() {
    // given
    gatewayService.errorOnRequest(
        REST_API_PATH + "/mapping-rules/" + MAPPING_RULE_ID,
        () -> new ProblemDetail().title("Forbidden").status(403));

    // when / then
    assertThatThrownBy(() -> client.newDeleteMappingRuleCommand(MAPPING_RULE_ID).send().join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("Failed with code 403: 'Forbidden'");
  }
}
