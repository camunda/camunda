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
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import io.camunda.client.api.command.ProblemException;
import io.camunda.client.api.search.filter.MappingRuleFilterBase;
import io.camunda.client.protocol.rest.MappingRuleFilter;
import io.camunda.client.protocol.rest.MappingRuleSearchQueryRequest;
import io.camunda.client.protocol.rest.MappingRuleSearchQueryResult;
import io.camunda.client.protocol.rest.MappingRuleSearchQuerySortRequest;
import io.camunda.client.protocol.rest.MappingRuleSearchQuerySortRequest.FieldEnum;
import io.camunda.client.protocol.rest.ProblemDetail;
import io.camunda.client.protocol.rest.SearchQueryPageRequest;
import io.camunda.client.protocol.rest.SortOrderEnum;
import io.camunda.client.util.ClientRestTest;
import io.camunda.client.util.RestGatewayService;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.instancio.Instancio;
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

    final MappingRuleSearchQueryRequest requestBody =
        gatewayService.getLastRequest(MappingRuleSearchQueryRequest.class);
    assertThat(requestBody.getFilter()).isNull();
  }

  @Test
  void shouldSearchMappingRulesByClaimName() {
    // when
    client.newMappingRulesSearchRequest().filter(f -> f.claimName("demo")).send().join();

    // then
    final MappingRuleSearchQueryRequest requestBody =
        gatewayService.getLastRequest(MappingRuleSearchQueryRequest.class);

    final MappingRuleFilter filter = requestBody.getFilter();
    assertThat(filter).isNotNull();
    assertThat(filter.getClaimName()).isEqualTo("demo");
  }

  @Test
  void shouldSearchMappingRulesByClaimValue() {
    // when
    client.newMappingRulesSearchRequest().filter(f -> f.claimValue("test-value")).send().join();

    // then
    final MappingRuleSearchQueryRequest requestBody =
        gatewayService.getLastRequest(MappingRuleSearchQueryRequest.class);

    final MappingRuleFilter filter = requestBody.getFilter();
    assertThat(filter).isNotNull();
    assertThat(filter.getClaimValue()).isEqualTo("test-value");
  }

  @Test
  void shouldSearchMappingRulesByMappingRuleId() {
    // when
    client.newMappingRulesSearchRequest().filter(f -> f.mappingRuleId("rule123")).send().join();

    // then
    final MappingRuleSearchQueryRequest requestBody =
        gatewayService.getLastRequest(MappingRuleSearchQueryRequest.class);

    final MappingRuleFilter filter = requestBody.getFilter();
    assertThat(filter).isNotNull();
    assertThat(filter.getMappingRuleId().get$Eq()).isEqualTo("rule123");
  }

  @Test
  void shouldSearchMappingRulesByMappingRuleIdLike() {
    // when
    client
        .newMappingRulesSearchRequest()
        .filter(f -> f.mappingRuleId(b -> b.like("rule*")))
        .send()
        .join();

    // then
    final MappingRuleSearchQueryRequest requestBody =
        gatewayService.getLastRequest(MappingRuleSearchQueryRequest.class);

    final MappingRuleFilter filter = requestBody.getFilter();
    assertThat(filter).isNotNull();
    assertThat(filter.getMappingRuleId().get$Like()).isEqualTo("rule*");
  }

  @Test
  void shouldSearchMappingRulesByName() {
    // when
    client.newMappingRulesSearchRequest().filter(f -> f.name("ruleName")).send().join();

    // then
    final MappingRuleSearchQueryRequest requestBody =
        gatewayService.getLastRequest(MappingRuleSearchQueryRequest.class);

    final MappingRuleFilter filter = requestBody.getFilter();
    assertThat(filter).isNotNull();
    assertThat(filter.getName().get$Eq()).isEqualTo("ruleName");
  }

  @Test
  void shouldIncludeSortAndFilterInMappingRulesSearchRequestBody() {
    // when
    client
        .newMappingRulesSearchRequest()
        .filter(fn -> fn.claimName("department").claimValue("engineering"))
        .sort(s -> s.claimName().asc())
        .page(fn -> fn.limit(5).from(10))
        .send()
        .join();

    // then
    final MappingRuleSearchQueryRequest requestBody =
        gatewayService.getLastRequest(MappingRuleSearchQueryRequest.class);

    final MappingRuleFilter filter = requestBody.getFilter();
    assertThat(filter).isNotNull();
    assertThat(filter.getClaimName()).isEqualTo("department");
    assertThat(filter.getClaimValue()).isEqualTo("engineering");

    final List<MappingRuleSearchQuerySortRequest> sort = requestBody.getSort();
    assertThat(sort).isNotNull();
    assertThat(sort).hasSize(1);
    assertThat(sort.get(0).getField()).isEqualTo(FieldEnum.CLAIM_NAME);
    assertThat(sort.get(0).getOrder()).isEqualTo(SortOrderEnum.ASC);

    final SearchQueryPageRequest page = requestBody.getPage();
    assertThat(page).isNotNull();
    assertThat(page.getFrom()).isEqualTo(10);
    assertThat(page.getLimit()).isEqualTo(5);
  }

  @Test
  void shouldSearchMappingRulesWithFilterOnly() {
    // when
    client.newMappingRulesSearchRequest().filter(fn -> fn.mappingRuleId("rule123")).send().join();

    // then
    final MappingRuleSearchQueryRequest requestBody =
        gatewayService.getLastRequest(MappingRuleSearchQueryRequest.class);
    final MappingRuleFilter filter = requestBody.getFilter();
    assertThat(filter).isNotNull();
    assertThat(filter.getMappingRuleId().get$Eq()).isEqualTo("rule123");

    assertThat(requestBody.getSort()).isEmpty();
    assertThat(requestBody.getPage()).isNull();
  }

  @Test
  void shouldSearchMappingRulesWithSortOnly() {
    // when
    client.newMappingRulesSearchRequest().sort(s -> s.claimValue().desc()).send().join();

    // then
    final MappingRuleSearchQueryRequest requestBody =
        gatewayService.getLastRequest(MappingRuleSearchQueryRequest.class);

    final List<MappingRuleSearchQuerySortRequest> sort = requestBody.getSort();
    assertThat(sort).isNotNull();
    assertThat(sort).hasSize(1);
    assertThat(sort.get(0).getField()).isEqualTo(FieldEnum.CLAIM_VALUE);
    assertThat(sort.get(0).getOrder()).isEqualTo(SortOrderEnum.DESC);

    assertThat(requestBody.getFilter()).isNull();
    assertThat(requestBody.getPage()).isNull();
  }

  @Test
  void shouldSearchMappingRulesWithPageOnly() {
    // when
    client.newMappingRulesSearchRequest().page(fn -> fn.limit(20).from(10)).send().join();

    // then
    final MappingRuleSearchQueryRequest requestBody =
        gatewayService.getLastRequest(MappingRuleSearchQueryRequest.class);

    final SearchQueryPageRequest page = requestBody.getPage();
    assertThat(page).isNotNull();
    assertThat(page.getFrom()).isEqualTo(10);
    assertThat(page.getLimit()).isEqualTo(20);

    assertThat(requestBody.getFilter()).isNull();
    assertThat(requestBody.getSort()).isEmpty();
  }

  @Test
  void shouldSearchMappingRulesWithOrFilters() {
    // when
    client
        .newMappingRulesSearchRequest()
        .filter(
            fn ->
                fn.orFilters(
                    Arrays.asList(
                        f1 -> f1.mappingRuleId("rule-1"), f2 -> f2.mappingRuleId("rule-2"))))
        .send()
        .join();

    // then
    final MappingRuleSearchQueryRequest request =
        gatewayService.getLastRequest(MappingRuleSearchQueryRequest.class);
    final MappingRuleFilter filter = request.getFilter();
    assertThat(filter).isNotNull();
    assertThat(filter.get$Or()).hasSize(2);
    assertThat(filter.get$Or().get(0).getMappingRuleId().get$Eq()).isEqualTo("rule-1");
    assertThat(filter.get$Or().get(1).getMappingRuleId().get$Eq()).isEqualTo("rule-2");
  }

  @Test
  void shouldFallBackToPlainMappingRuleIdFilterOnLegacyClusterRejection() {
    // given -- an 8.9-or-earlier cluster rejects the advanced { "$eq": ... } shape
    gatewayService.errorThenSuccessOnPostRequest(
        REST_API_PATH + "/mapping-rules/search",
        new ProblemDetail()
            .title("INVALID_ARGUMENT")
            .status(400)
            .detail("Request property [filter.mappingRuleId] cannot be parsed"),
        Instancio.create(MappingRuleSearchQueryResult.class));

    // when -- the first search retries once and falls back to the plain-string shape
    client.newMappingRulesSearchRequest().filter(fn -> fn.mappingRuleId("rule-1")).send().join();

    // then
    final List<LoggedRequest> requests = RestGatewayService.getAllRequests();
    assertThat(requests).hasSize(2);
    assertThat(requests)
        .anySatisfy(r -> assertThat(r.getBodyAsString()).contains("\"$eq\":\"rule-1\""));
    assertThat(requests)
        .anySatisfy(
            r ->
                assertThat(r.getBodyAsString())
                    .contains("\"mappingRuleId\":\"rule-1\"")
                    .doesNotContain("$eq"));

    // when -- a second search on the same client goes straight to the remembered plain-string
    // shape, without repeating the doomed first attempt
    client.newMappingRulesSearchRequest().filter(fn -> fn.mappingRuleId("rule-2")).send().join();

    // then
    final List<LoggedRequest> allRequests = RestGatewayService.getAllRequests();
    assertThat(allRequests).hasSize(3);
    final List<LoggedRequest> rule2Requests =
        allRequests.stream()
            .filter(r -> r.getBodyAsString().contains("rule-2"))
            .collect(Collectors.toList());
    assertThat(rule2Requests).hasSize(1);
    assertThat(rule2Requests.get(0).getBodyAsString())
        .contains("\"mappingRuleId\":\"rule-2\"")
        .doesNotContain("$eq");
  }

  @Test
  void shouldNotFallBackForAdvancedMappingRuleIdFilterOnLegacyClusterRejection() {
    // given -- an advanced operator has no plain-string equivalent, so no fallback is attempted
    gatewayService.errorOnRequest(
        REST_API_PATH + "/mapping-rules/search",
        () ->
            new ProblemDetail()
                .title("INVALID_ARGUMENT")
                .status(400)
                .detail("Request property [filter.mappingRuleId] cannot be parsed"));

    // when / then
    assertThatThrownBy(
            () ->
                client
                    .newMappingRulesSearchRequest()
                    .filter(fn -> fn.mappingRuleId(b -> b.like("rule*")))
                    .send()
                    .join())
        .isInstanceOf(ProblemException.class);
    assertThat(RestGatewayService.getAllRequests()).hasSize(1);
  }

  @Test
  void shouldHaveMatchingFilterMethodsInBaseAndFullInterfaces() {
    final Set<String> baseMethods = publicMethodSignatures(MappingRuleFilterBase.class);
    final Set<String> fullMethods =
        publicMethodSignatures(io.camunda.client.api.search.filter.MappingRuleFilter.class);

    assertThat(fullMethods)
        .withFailMessage("Full interface is missing methods from base interface")
        .containsAll(baseMethods);

    final Set<String> expectedExtras = new HashSet<>();
    expectedExtras.add("orFilters[interface java.util.List]");
    final Set<String> actualExtras = new HashSet<>(fullMethods);
    actualExtras.removeAll(baseMethods);

    assertThat(actualExtras)
        .withFailMessage("Unexpected methods in full interface: %s", actualExtras)
        .isEqualTo(expectedExtras);
  }

  private static Set<String> publicMethodSignatures(final Class<?> clazz) {
    return Arrays.stream(clazz.getMethods())
        .filter(m -> Modifier.isPublic(m.getModifiers()) && !m.isSynthetic())
        .map(m -> m.getName() + Arrays.toString(m.getParameterTypes()))
        .collect(Collectors.toSet());
  }
}
