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

import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.http.RequestMethod;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import io.camunda.client.util.ClientRestTest;
import org.junit.jupiter.api.Test;

public class SearchMappingRuleTest extends ClientRestTest {

  @Test
  void shouldSearchMappingRules() {
    // when
    client.newMappingRulesSearchRequest().send().join();

    // then
    final LoggedRequest request = gatewayService.getLastRequest();
    assertThat(request.getUrl()).isEqualTo("/v2/mapping-rules/search");
    assertThat(request.getMethod()).isEqualTo(RequestMethod.POST);
  }

  @Test
  void shouldSearchMappingRulesByClaimName() {
    // when
    client.newMappingRulesSearchRequest().filter(f -> f.claimName("demo")).send().join();

    // then
    final LoggedRequest request = gatewayService.getLastRequest();
    assertThat(request.getBodyAsString()).contains("\"claimName\":\"demo\"");
  }

  @Test
  void shouldSearchMappingRulesByClaimValue() {
    // when
    client.newMappingRulesSearchRequest().filter(f -> f.claimValue("test-value")).send().join();

    // then
    final LoggedRequest request = gatewayService.getLastRequest();
    assertThat(request.getBodyAsString()).contains("\"claimValue\":\"test-value\"");
  }

  @Test
  void shouldSearchMappingRulesByMappingRuleId() {
    // when
    client.newMappingRulesSearchRequest().filter(f -> f.mappingRuleId("rule123")).send().join();

    // then
    final LoggedRequest request = gatewayService.getLastRequest();
    assertThat(request.getBodyAsString()).contains("\"mappingRuleId\":\"rule123\"");
  }

  @Test
  void shouldSearchMappingRulesByName() {
    // when
    client.newMappingRulesSearchRequest().filter(f -> f.name("ruleName")).send().join();

    // then
    final LoggedRequest request = gatewayService.getLastRequest();
    assertThat(request.getBodyAsString()).contains("\"name\":\"ruleName\"");
  }

  @Test
  void shouldSearchMappingRulesWithSorting() {
    // when
    client.newMappingRulesSearchRequest().sort(s -> s.claimName().asc()).send().join();

    // then
    final LoggedRequest request = gatewayService.getLastRequest();
    assertThat(request.getBodyAsString())
        .contains("{\"sort\":[{\"field\":\"claimName\",\"order\":\"ASC\"}]}");
  }

  @Test
  void shouldSearchMappingRulesWithPaging() {
    // when
    client.newMappingRulesSearchRequest().page(p -> p.limit(10).from(5)).send().join();

    // then
    final LoggedRequest request = gatewayService.getLastRequest();
    assertThat(request.getBodyAsString()).contains("\"page\":{\"from\":5,\"limit\":10}");
  }
}
