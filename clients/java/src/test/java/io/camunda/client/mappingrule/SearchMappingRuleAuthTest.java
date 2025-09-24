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

public class SearchMappingRuleAuthTest extends ClientRestTest {

  @Test
  public void shouldSearchMappingRules() {
    // when
    client
        .newMappingRulesSearchRequest()
        .filter(fn -> fn.claimName("department"))
        .sort(s -> s.claimName().desc())
        .page(fn -> fn.limit(10))
        .send()
        .join();

    // then
    final LoggedRequest request = gatewayService.getLastRequest();
    assertThat(request.getUrl()).isEqualTo("/v2/mapping-rules/search");
    assertThat(request.getMethod()).isEqualTo(RequestMethod.POST);
  }

  @Test
  void shouldIncludeSortAndFilterInMappingRulesSearchRequestBody() {
    // when
    client
        .newMappingRulesSearchRequest()
        .filter(fn -> fn.claimName("department").claimValue("engineering"))
        .sort(s -> s.claimName().asc())
        .page(fn -> fn.limit(5).from(0))
        .send()
        .join();

    // then
    final LoggedRequest request = gatewayService.getLastRequest();
    assertThat(request.getUrl()).isEqualTo("/v2/mapping-rules/search");
    assertThat(request.getMethod()).isEqualTo(RequestMethod.POST);

    final String requestBody = request.getBodyAsString();
    assertThat(requestBody).contains("\"claimName\"");
    assertThat(requestBody).contains("\"claimValue\"");
    assertThat(requestBody).contains("\"sort\"");
    assertThat(requestBody).contains("\"page\"");
  }

  @Test
  void shouldSearchMappingRulesWithFilterOnly() {
    // when
    client.newMappingRulesSearchRequest().filter(fn -> fn.mappingRuleId("rule123")).send().join();

    // then
    final LoggedRequest request = gatewayService.getLastRequest();
    assertThat(request.getUrl()).isEqualTo("/v2/mapping-rules/search");
    assertThat(request.getMethod()).isEqualTo(RequestMethod.POST);

    final String requestBody = request.getBodyAsString();
    assertThat(requestBody).contains("\"mappingRuleId\"");
  }

  @Test
  void shouldSearchMappingRulesWithSortOnly() {
    // when
    client.newMappingRulesSearchRequest().sort(s -> s.claimValue().desc()).send().join();

    // then
    final LoggedRequest request = gatewayService.getLastRequest();
    assertThat(request.getUrl()).isEqualTo("/v2/mapping-rules/search");
    assertThat(request.getMethod()).isEqualTo(RequestMethod.POST);

    final String requestBody = request.getBodyAsString();
    assertThat(requestBody).contains("\"sort\"");
  }

  @Test
  void shouldSearchMappingRulesWithPageOnly() {
    // when
    client.newMappingRulesSearchRequest().page(fn -> fn.limit(20).from(10)).send().join();

    // then
    final LoggedRequest request = gatewayService.getLastRequest();
    assertThat(request.getUrl()).isEqualTo("/v2/mapping-rules/search");
    assertThat(request.getMethod()).isEqualTo(RequestMethod.POST);

    final String requestBody = request.getBodyAsString();
    assertThat(requestBody).contains("\"page\"");
    assertThat(requestBody).contains("\"limit\":20");
    assertThat(requestBody).contains("\"from\":10");
  }
}
