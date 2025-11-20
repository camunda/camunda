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
package io.camunda.client.clustervariable;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.http.RequestMethod;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import io.camunda.client.api.search.enums.ClusterVariableScope;
import io.camunda.client.util.ClientRestTest;
import io.camunda.client.util.RestGatewayPaths;
import io.camunda.client.util.RestGatewayService;
import org.junit.jupiter.api.Test;

public class SearchClusterVariableTest extends ClientRestTest {

  @Test
  void shouldSearchClusterVariables() {
    // when
    client.newClusterVariableSearchRequest().send().join();

    // then
    final LoggedRequest request = RestGatewayService.getLastRequest();
    assertThat(request.getUrl()).isEqualTo(RestGatewayPaths.getClusterVariablesUrl() + "/search");
    assertThat(request.getMethod()).isEqualTo(RequestMethod.POST);
  }

  @Test
  void shouldSearchClusterVariablesByName() {
    // when
    client.newClusterVariableSearchRequest().filter(f -> f.name("myVariable")).send().join();

    // then
    final LoggedRequest request = RestGatewayService.getLastRequest();
    assertThat(request.getUrl()).isEqualTo(RestGatewayPaths.getClusterVariablesUrl() + "/search");
    assertThat(request.getMethod()).isEqualTo(RequestMethod.POST);
  }

  @Test
  void shouldSearchClusterVariablesByNameStringFilter() {
    // when
    client
        .newClusterVariableSearchRequest()
        .filter(f -> f.name(b -> b.in("var1", "var2")))
        .send()
        .join();

    // then
    final LoggedRequest request = RestGatewayService.getLastRequest();
    assertThat(request.getUrl()).isEqualTo(RestGatewayPaths.getClusterVariablesUrl() + "/search");
  }

  @Test
  void shouldSearchClusterVariablesByValue() {
    // when
    client.newClusterVariableSearchRequest().filter(f -> f.value("myValue")).send().join();

    // then
    final LoggedRequest request = RestGatewayService.getLastRequest();
    assertThat(request.getUrl()).isEqualTo(RestGatewayPaths.getClusterVariablesUrl() + "/search");
  }

  @Test
  void shouldSearchClusterVariablesByScope() {
    // when
    client
        .newClusterVariableSearchRequest()
        .filter(f -> f.scope(ClusterVariableScope.GLOBAL))
        .send()
        .join();

    // then
    final LoggedRequest request = RestGatewayService.getLastRequest();
    assertThat(request.getUrl()).isEqualTo(RestGatewayPaths.getClusterVariablesUrl() + "/search");
  }

  @Test
  void shouldSearchClusterVariablesByTenantId() {
    // when
    client.newClusterVariableSearchRequest().filter(f -> f.tenantId("tenant_1")).send().join();

    // then
    final LoggedRequest request = RestGatewayService.getLastRequest();
    assertThat(request.getUrl()).isEqualTo(RestGatewayPaths.getClusterVariablesUrl() + "/search");
  }

  @Test
  void shouldSearchClusterVariablesWithMultipleFilters() {
    // when
    client
        .newClusterVariableSearchRequest()
        .filter(f -> f.name("myVariable").scope(ClusterVariableScope.TENANT).tenantId("tenant_1"))
        .send()
        .join();

    // then
    final LoggedRequest request = RestGatewayService.getLastRequest();
    assertThat(request.getUrl()).isEqualTo(RestGatewayPaths.getClusterVariablesUrl() + "/search");
    assertThat(request.getMethod()).isEqualTo(RequestMethod.POST);
  }

  @Test
  void shouldSearchClusterVariablesWithSort() {
    // when
    client.newClusterVariableSearchRequest().sort(s -> s.name().asc().scope()).send().join();

    // then
    final LoggedRequest request = RestGatewayService.getLastRequest();
    assertThat(request.getUrl()).isEqualTo(RestGatewayPaths.getClusterVariablesUrl() + "/search");
    assertThat(request.getMethod()).isEqualTo(RequestMethod.POST);
  }
}
