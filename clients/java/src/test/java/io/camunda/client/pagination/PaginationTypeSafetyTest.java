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
package io.camunda.client.pagination;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import io.camunda.client.protocol.rest.IncidentProcessInstanceStatisticsByErrorQuery;
import io.camunda.client.protocol.rest.IncidentProcessInstanceStatisticsByErrorQueryResult;
import io.camunda.client.protocol.rest.IncidentProcessInstanceStatisticsByErrorResult;
import io.camunda.client.protocol.rest.JobTypeStatisticsQuery;
import io.camunda.client.protocol.rest.JobTypeStatisticsQueryResult;
import io.camunda.client.protocol.rest.MappingRuleSearchQueryRequest;
import io.camunda.client.protocol.rest.OffsetPagination;
import io.camunda.client.protocol.rest.SearchQueryPageRequest;
import io.camunda.client.protocol.rest.SearchQueryPageResponse;
import io.camunda.client.util.ClientRestTest;
import io.camunda.client.util.RestGatewayService;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import org.junit.jupiter.api.Test;

/**
 * Tests verifying that the typed pagination model correctly restricts and serializes pagination
 * parameters per endpoint type.
 *
 * <p>The Java client enforces at compile-time which pagination model is valid for each endpoint:
 *
 * <ul>
 *   <li><b>OffsetPage</b> ({@code from} + {@code limit}): incident/process-definition statistics
 *   <li><b>CursorForwardPage</b> ({@code after} + {@code limit}): job statistics
 *   <li><b>AnyPage</b> (all styles): standard search endpoints
 * </ul>
 *
 * <p>These tests verify that the correct pagination fields are serialized in the request body and
 * that incompatible fields do not leak through.
 */
public class PaginationTypeSafetyTest extends ClientRestTest {

  // ==================== OffsetPage tests (incident statistics) ====================

  @Test
  void shouldSerializeOnlyOffsetFieldsForOffsetPageEndpoint() {
    // given
    gatewayService.onIncidentProcessInstanceStatisticsByErrorRequest(
        new IncidentProcessInstanceStatisticsByErrorQueryResult()
            .page(new SearchQueryPageResponse().totalItems(1L))
            .items(
                Collections.singletonList(
                    new IncidentProcessInstanceStatisticsByErrorResult()
                        .errorHashCode(1)
                        .errorMessage("test")
                        .activeInstancesWithErrorCount(5L))));

    // when
    client
        .newIncidentProcessInstanceStatisticsByErrorRequest()
        .page(p -> p.from(5).limit(10))
        .send()
        .join();

    // then - verify only offset pagination fields are serialized
    final IncidentProcessInstanceStatisticsByErrorQuery request =
        gatewayService.getLastRequest(IncidentProcessInstanceStatisticsByErrorQuery.class);
    final OffsetPagination page = request.getPage();
    assertThat(page).isNotNull();
    assertThat(page.getFrom()).isEqualTo(5);
    assertThat(page.getLimit()).isEqualTo(10);

    // then - verify no cursor fields leak into the serialized JSON
    final LoggedRequest rawRequest = RestGatewayService.getLastRequest();
    final String body = rawRequest.getBodyAsString();
    assertThat(body).doesNotContain("\"after\"");
    assertThat(body).doesNotContain("\"before\"");
  }

  @Test
  void shouldSerializeOffsetPageWithOnlyLimit() {
    // given
    gatewayService.onIncidentProcessInstanceStatisticsByErrorRequest(
        new IncidentProcessInstanceStatisticsByErrorQueryResult()
            .page(new SearchQueryPageResponse().totalItems(1L))
            .items(
                Collections.singletonList(
                    new IncidentProcessInstanceStatisticsByErrorResult()
                        .errorHashCode(1)
                        .errorMessage("test")
                        .activeInstancesWithErrorCount(5L))));

    // when
    client
        .newIncidentProcessInstanceStatisticsByErrorRequest()
        .page(p -> p.limit(25))
        .send()
        .join();

    // then
    final IncidentProcessInstanceStatisticsByErrorQuery request =
        gatewayService.getLastRequest(IncidentProcessInstanceStatisticsByErrorQuery.class);
    final OffsetPagination page = request.getPage();
    assertThat(page).isNotNull();
    assertThat(page.getFrom()).isNull();
    assertThat(page.getLimit()).isEqualTo(25);
  }

  @Test
  void shouldNotSerializePageWhenNoPaginationSetOnOffsetEndpoint() {
    // given
    gatewayService.onIncidentProcessInstanceStatisticsByErrorRequest(
        new IncidentProcessInstanceStatisticsByErrorQueryResult()
            .page(new SearchQueryPageResponse().totalItems(1L))
            .items(
                Collections.singletonList(
                    new IncidentProcessInstanceStatisticsByErrorResult()
                        .errorHashCode(1)
                        .errorMessage("test")
                        .activeInstancesWithErrorCount(5L))));

    // when
    client.newIncidentProcessInstanceStatisticsByErrorRequest().send().join();

    // then
    final IncidentProcessInstanceStatisticsByErrorQuery request =
        gatewayService.getLastRequest(IncidentProcessInstanceStatisticsByErrorQuery.class);
    assertThat(request.getPage()).isNull();
  }

  // ==================== CursorForwardPage tests (job statistics) ====================

  @Test
  void shouldSerializeOnlyCursorForwardFieldsForCursorEndpoint() {
    // given
    final OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    gatewayService.onJobTypeStatisticsRequest(
        new JobTypeStatisticsQueryResult()
            .items(Collections.emptyList())
            .page(new SearchQueryPageResponse().totalItems(0L)));

    // when
    client
        .newJobTypeStatisticsRequest(now.minusDays(1), now.plusDays(1))
        .page(p -> p.after("cursor123").limit(10))
        .send()
        .join();

    // then - verify cursor-forward pagination fields are serialized in the page object
    final JobTypeStatisticsQuery request =
        gatewayService.getLastRequest(JobTypeStatisticsQuery.class);
    assertThat(request.getPage()).isNotNull();
    assertThat(request.getPage().getAfter()).isEqualTo("cursor123");
    assertThat(request.getPage().getLimit()).isEqualTo(10);

    // then - verify no backward-cursor fields leak into the serialized JSON
    final LoggedRequest rawRequest = RestGatewayService.getLastRequest();
    final String body = rawRequest.getBodyAsString();
    assertThat(body).doesNotContain("\"before\"");
  }

  @Test
  void shouldSerializeCursorForwardPageWithOnlyLimit() {
    // given
    final OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    gatewayService.onJobTypeStatisticsRequest(
        new JobTypeStatisticsQueryResult()
            .items(Collections.emptyList())
            .page(new SearchQueryPageResponse().totalItems(0L)));

    // when
    client
        .newJobTypeStatisticsRequest(now.minusDays(1), now.plusDays(1))
        .page(p -> p.limit(50))
        .send()
        .join();

    // then - verify only limit is set in the page, no cursor or offset fields
    final JobTypeStatisticsQuery request =
        gatewayService.getLastRequest(JobTypeStatisticsQuery.class);
    assertThat(request.getPage()).isNotNull();
    assertThat(request.getPage().getLimit()).isEqualTo(50);
    assertThat(request.getPage().getAfter()).isNull();

    // then - verify no backward-cursor fields leak into the serialized JSON
    final LoggedRequest rawRequest = RestGatewayService.getLastRequest();
    final String body = rawRequest.getBodyAsString();
    assertThat(body).doesNotContain("\"before\"");
  }

  // ==================== AnyPage tests (search endpoints) ====================

  @Test
  void shouldSerializeOffsetPaginationOnAnyPageEndpoint() {
    // when
    client.newMappingRulesSearchRequest().page(p -> p.from(10).limit(20)).send().join();

    // then
    final MappingRuleSearchQueryRequest request =
        gatewayService.getLastRequest(MappingRuleSearchQueryRequest.class);
    final SearchQueryPageRequest page = request.getPage();
    assertThat(page).isNotNull();
    assertThat(page.getFrom()).isEqualTo(10);
    assertThat(page.getLimit()).isEqualTo(20);

    // then - verify no cursor fields leak
    final LoggedRequest rawRequest = RestGatewayService.getLastRequest();
    final String body = rawRequest.getBodyAsString();
    assertThat(body).doesNotContain("\"after\"");
    assertThat(body).doesNotContain("\"before\"");
  }

  @Test
  void shouldSerializeCursorForwardPaginationOnAnyPageEndpoint() {
    // when
    client
        .newMappingRulesSearchRequest()
        .page(p -> p.after("endCursor123").limit(15))
        .send()
        .join();

    // then
    final LoggedRequest rawRequest = RestGatewayService.getLastRequest();
    final String body = rawRequest.getBodyAsString();
    assertThat(body).contains("\"after\":\"endCursor123\"");
    assertThat(body).contains("\"limit\":15");
    assertThat(body).doesNotContain("\"from\"");
    assertThat(body).doesNotContain("\"before\"");
  }

  @Test
  void shouldSerializeCursorBackwardPaginationOnAnyPageEndpoint() {
    // when
    client
        .newMappingRulesSearchRequest()
        .page(p -> p.before("startCursor456").limit(15))
        .send()
        .join();

    // then
    final LoggedRequest rawRequest = RestGatewayService.getLastRequest();
    final String body = rawRequest.getBodyAsString();
    assertThat(body).contains("\"before\":\"startCursor456\"");
    assertThat(body).contains("\"limit\":15");
    assertThat(body).doesNotContain("\"from\"");
    assertThat(body).doesNotContain("\"after\"");
  }

  @Test
  void shouldSerializeLimitOnlyPaginationOnAnyPageEndpoint() {
    // when
    client.newMappingRulesSearchRequest().page(p -> p.limit(100)).send().join();

    // then
    final MappingRuleSearchQueryRequest request =
        gatewayService.getLastRequest(MappingRuleSearchQueryRequest.class);
    final SearchQueryPageRequest page = request.getPage();
    assertThat(page).isNotNull();
    assertThat(page.getLimit()).isEqualTo(100);
    assertThat(page.getFrom()).isNull();
  }
}
