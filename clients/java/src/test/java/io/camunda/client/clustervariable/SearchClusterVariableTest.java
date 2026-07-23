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

import com.github.tomakehurst.wiremock.http.QueryParameter;
import com.github.tomakehurst.wiremock.http.RequestMethod;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import io.camunda.client.api.search.enums.ClusterVariableKind;
import io.camunda.client.api.search.enums.ClusterVariableScope;
import io.camunda.client.protocol.rest.AdvancedMetadataValueFilter;
import io.camunda.client.protocol.rest.ClusterVariableKindEnum;
import io.camunda.client.protocol.rest.ClusterVariableSearchQueryRequest;
import io.camunda.client.util.ClientRestTest;
import io.camunda.client.util.RestGatewayPaths;
import io.camunda.client.util.RestGatewayService;
import java.util.Collections;
import org.junit.jupiter.api.Test;

public class SearchClusterVariableTest extends ClientRestTest {

  @Test
  void shouldSearchClusterVariables() {
    // when
    client.newClusterVariableSearchRequest().send().join();

    // then
    final LoggedRequest request = RestGatewayService.getLastRequest();
    assertThat(request.getUrl()).isEqualTo(RestGatewayPaths.getClusterVariablesSearchUrl());
    assertThat(request.getMethod()).isEqualTo(RequestMethod.POST);
  }

  @Test
  void shouldSearchClusterVariablesWithoutParamsByDefault() {
    // when
    client.newClusterVariableSearchRequest().send().join();

    // then
    final QueryParameter param =
        RestGatewayService.getLastRequest().getQueryParams().get("truncateValues");
    assertThat(param).isNull();
  }

  @Test
  void shouldSearchClusterVariablesWithoutTruncating() {
    // when
    client.newClusterVariableSearchRequest().withFullValues().send().join();

    // then
    final QueryParameter param =
        RestGatewayService.getLastRequest().getQueryParams().get("truncateValues");
    assertThat(param.isSingleValued()).isTrue();
    assertThat(param.firstValue()).isEqualTo("false");
  }

  @Test
  void shouldSearchClusterVariablesByName() {
    // when
    client.newClusterVariableSearchRequest().filter(f -> f.name("myVariable")).send().join();

    // then
    final LoggedRequest request = RestGatewayService.getLastRequest();
    assertThat(request.getUrl()).isEqualTo(RestGatewayPaths.getClusterVariablesSearchUrl());
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
    assertThat(request.getUrl()).isEqualTo(RestGatewayPaths.getClusterVariablesSearchUrl());
  }

  @Test
  void shouldSearchClusterVariablesByValue() {
    // when
    client.newClusterVariableSearchRequest().filter(f -> f.value("myValue")).send().join();

    // then
    final LoggedRequest request = RestGatewayService.getLastRequest();
    assertThat(request.getUrl()).isEqualTo(RestGatewayPaths.getClusterVariablesSearchUrl());
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
    assertThat(request.getUrl()).isEqualTo(RestGatewayPaths.getClusterVariablesSearchUrl());
  }

  @Test
  void shouldSearchClusterVariablesByTenantId() {
    // when
    client.newClusterVariableSearchRequest().filter(f -> f.tenantId("tenant_1")).send().join();

    // then
    final LoggedRequest request = RestGatewayService.getLastRequest();
    assertThat(request.getUrl()).isEqualTo(RestGatewayPaths.getClusterVariablesSearchUrl());
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
    assertThat(request.getUrl()).isEqualTo(RestGatewayPaths.getClusterVariablesSearchUrl());
    assertThat(request.getMethod()).isEqualTo(RequestMethod.POST);
  }

  @Test
  void shouldSearchClusterVariablesWithSort() {
    // when
    client.newClusterVariableSearchRequest().sort(s -> s.name().asc().scope()).send().join();

    // then
    final LoggedRequest request = RestGatewayService.getLastRequest();
    assertThat(request.getUrl()).isEqualTo(RestGatewayPaths.getClusterVariablesSearchUrl());
    assertThat(request.getMethod()).isEqualTo(RequestMethod.POST);
  }

  @Test
  void shouldSearchClusterVariablesWithFullValuesAndFilter() {
    // when
    client
        .newClusterVariableSearchRequest()
        .filter(f -> f.name("myVariable"))
        .withFullValues()
        .send()
        .join();

    // then
    final QueryParameter param =
        RestGatewayService.getLastRequest().getQueryParams().get("truncateValues");
    assertThat(param.isSingleValued()).isTrue();
    assertThat(param.firstValue()).isEqualTo("false");
  }

  @Test
  void shouldSearchClusterVariablesFilterByIsTruncatedFalse() {
    // when
    client.newClusterVariableSearchRequest().filter(f -> f.isTruncated(false)).send().join();

    // then
    final ClusterVariableSearchQueryRequest request =
        gatewayService.getLastRequest(ClusterVariableSearchQueryRequest.class);
    assertThat(request.getFilter().getIsTruncated()).isEqualTo(false);
  }

  @Test
  void shouldSearchClusterVariablesByIsTruncatedTrue() {
    // when
    client.newClusterVariableSearchRequest().filter(f -> f.isTruncated(true)).send().join();

    // then
    final ClusterVariableSearchQueryRequest request =
        gatewayService.getLastRequest(ClusterVariableSearchQueryRequest.class);
    assertThat(request.getFilter().getIsTruncated()).isEqualTo(true);
  }

  @Test
  void shouldFilterClusterVariablesByKind() {
    // when
    client
        .newClusterVariableSearchRequest()
        .filter(f -> f.kind(ClusterVariableKind.SECRET_REFERENCE))
        .send()
        .join();

    // then
    final ClusterVariableSearchQueryRequest request =
        gatewayService.getLastRequest(ClusterVariableSearchQueryRequest.class);
    assertThat(request.getFilter().getKind().get$Eq())
        .isEqualTo(ClusterVariableKindEnum.SECRET_REFERENCE);
  }

  @Test
  void shouldChainWithFullValuesMethodCall() {
    // when
    client
        .newClusterVariableSearchRequest()
        .withFullValues()
        .filter(f -> f.name("test"))
        .send()
        .join();

    // then
    final QueryParameter param =
        RestGatewayService.getLastRequest().getQueryParams().get("truncateValues");
    assertThat(param.isSingleValued()).isTrue();
    assertThat(param.firstValue()).isEqualTo("false");
  }

  @Test
  void shouldSearchClusterVariablesByMetadataEquality() {
    // when
    client
        .newClusterVariableSearchRequest()
        .filter(f -> f.metadata(Collections.singletonMap("env", "prod")))
        .send()
        .join();

    // then
    final ClusterVariableSearchQueryRequest request =
        gatewayService.getLastRequest(ClusterVariableSearchQueryRequest.class);
    assertThat(request.getFilter().getMetadata().get("env").get$Eq()).isEqualTo("prod");
  }

  @Test
  void shouldSearchClusterVariablesByMetadataNumericEquality() {
    // when
    client
        .newClusterVariableSearchRequest()
        .filter(f -> f.metadata(Collections.singletonMap("count", 42)))
        .send()
        .join();

    // then
    final ClusterVariableSearchQueryRequest request =
        gatewayService.getLastRequest(ClusterVariableSearchQueryRequest.class);
    assertThat(request.getFilter().getMetadata().get("count").get$Eq()).isEqualTo(42);
  }

  @Test
  void shouldSearchClusterVariablesByMetadataAdvancedFilter() {
    // when
    client
        .newClusterVariableSearchRequest()
        .filter(f -> f.metadata("count", m -> m.gt(5).lt(100)))
        .send()
        .join();

    // then
    final ClusterVariableSearchQueryRequest request =
        gatewayService.getLastRequest(ClusterVariableSearchQueryRequest.class);
    final AdvancedMetadataValueFilter count = request.getFilter().getMetadata().get("count");
    assertThat(count.get$Gt()).isEqualByComparingTo("5");
    assertThat(count.get$Lt()).isEqualByComparingTo("100");
  }

  @Test
  void shouldSearchClusterVariablesByMetadataIn() {
    // when
    client
        .newClusterVariableSearchRequest()
        .filter(f -> f.metadata("region", m -> m.in("eu", "us")))
        .send()
        .join();

    // then
    final ClusterVariableSearchQueryRequest request =
        gatewayService.getLastRequest(ClusterVariableSearchQueryRequest.class);
    assertThat(request.getFilter().getMetadata().get("region").get$In())
        .containsExactly("eu", "us");
  }

  @Test
  void shouldSearchClusterVariablesByMetadataExists() {
    // when
    client
        .newClusterVariableSearchRequest()
        .filter(f -> f.metadata("env", m -> m.exists(true)))
        .send()
        .join();

    // then
    final ClusterVariableSearchQueryRequest request =
        gatewayService.getLastRequest(ClusterVariableSearchQueryRequest.class);
    assertThat(request.getFilter().getMetadata().get("env").get$Exists()).isTrue();
  }

  @Test
  void shouldSearchClusterVariablesByMetadataGte() {
    // when
    client
        .newClusterVariableSearchRequest()
        .filter(f -> f.metadata("count", m -> m.gte(5)))
        .send()
        .join();

    // then
    final ClusterVariableSearchQueryRequest request =
        gatewayService.getLastRequest(ClusterVariableSearchQueryRequest.class);
    assertThat(request.getFilter().getMetadata().get("count").get$Gte()).isEqualByComparingTo("5");
  }

  @Test
  void shouldSearchClusterVariablesByMetadataLte() {
    // when
    client
        .newClusterVariableSearchRequest()
        .filter(f -> f.metadata("count", m -> m.lte(100)))
        .send()
        .join();

    // then
    final ClusterVariableSearchQueryRequest request =
        gatewayService.getLastRequest(ClusterVariableSearchQueryRequest.class);
    assertThat(request.getFilter().getMetadata().get("count").get$Lte())
        .isEqualByComparingTo("100");
  }

  @Test
  void shouldSearchClusterVariablesByMetadataLike() {
    // when
    client
        .newClusterVariableSearchRequest()
        .filter(f -> f.metadata("region", m -> m.like("eu-*")))
        .send()
        .join();

    // then
    final ClusterVariableSearchQueryRequest request =
        gatewayService.getLastRequest(ClusterVariableSearchQueryRequest.class);
    assertThat(request.getFilter().getMetadata().get("region").get$Like()).isEqualTo("eu-*");
  }
}
