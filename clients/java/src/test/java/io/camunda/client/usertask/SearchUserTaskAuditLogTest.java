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
package io.camunda.client.usertask;

import static io.camunda.client.util.assertions.SortAssert.assertSort;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.http.RequestMethod;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import io.camunda.client.api.search.enums.AuditLogActorTypeEnum;
import io.camunda.client.api.search.enums.AuditLogOperationTypeEnum;
import io.camunda.client.api.search.enums.AuditLogResultEnum;
import io.camunda.client.impl.search.request.SearchRequestSort;
import io.camunda.client.impl.search.request.SearchRequestSortMapper;
import io.camunda.client.protocol.rest.AuditLogSearchQueryResult;
import io.camunda.client.protocol.rest.SortOrderEnum;
import io.camunda.client.protocol.rest.UserTaskAuditLogFilter;
import io.camunda.client.protocol.rest.UserTaskAuditLogSearchQueryRequest;
import io.camunda.client.util.ClientRestTest;
import io.camunda.client.util.RestGatewayPaths;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import org.instancio.Instancio;
import org.junit.jupiter.api.Test;

public class SearchUserTaskAuditLogTest extends ClientRestTest {

  private static final long USER_TASK_KEY = 1L;

  @Test
  public void shouldSearchUserTaskAuditLogsWithEmptyQuery() {
    // when
    gatewayService.onSearchUserTaskAuditLogRequest(
        USER_TASK_KEY, Instancio.create(AuditLogSearchQueryResult.class));
    client.newUserTaskAuditLogSearchRequest(USER_TASK_KEY).send().join();

    // then
    final LoggedRequest request = gatewayService.getLastRequest();
    assertThat(request.getUrl())
        .isEqualTo(RestGatewayPaths.getUserTaskAuditLogSearchUrl(USER_TASK_KEY));
    assertThat(request.getMethod()).isEqualTo(RequestMethod.POST);
  }

  @Test
  public void shouldSearchUserTaskAuditLogsWithArgs() {
    // when
    client
        .newUserTaskAuditLogSearchRequest(USER_TASK_KEY)
        .filter(fn -> fn.actorId("actorId"))
        .sort(s -> s.timestamp().desc())
        .page(fn -> fn.limit(10))
        .send()
        .join();

    // then
    final LoggedRequest request = gatewayService.getLastRequest();
    assertThat(request.getUrl())
        .isEqualTo(String.format("/v2/user-tasks/%d/audit-logs/search", USER_TASK_KEY));
    assertThat(request.getMethod()).isEqualTo(RequestMethod.POST);
  }

  @Test
  public void shouldSearchAuditLogsWithFullFilters() {
    // when
    client
        .newUserTaskAuditLogSearchRequest(USER_TASK_KEY)
        .filter(
            fn ->
                fn.operationType(AuditLogOperationTypeEnum.CREATE)
                    .result(AuditLogResultEnum.SUCCESS)
                    .timestamp(OffsetDateTime.MIN)
                    .actorId("actorId")
                    .actorType(AuditLogActorTypeEnum.CLIENT))
        .send()
        .join();

    // then
    final UserTaskAuditLogSearchQueryRequest request =
        gatewayService.getLastRequest(UserTaskAuditLogSearchQueryRequest.class);
    final UserTaskAuditLogFilter filter = request.getFilter();
    assertThat(filter).isNotNull();
    assertThat(filter.getOperationType().get$Eq().getValue()).isEqualTo("CREATE");
    assertThat(filter.getResult().get$Eq().getValue()).isEqualTo("SUCCESS");
    assertThat(filter.getTimestamp().get$Eq()).isEqualTo(OffsetDateTime.MIN.toString());
    assertThat(filter.getActorId().get$Eq()).isEqualTo("actorId");
    assertThat(filter.getActorType().get$Eq().getValue()).isEqualTo("CLIENT");
  }

  @Test
  public void shouldSearchUserTaskAuditLogsWithAdvancedFilters() {
    // when
    client
        .newUserTaskAuditLogSearchRequest(USER_TASK_KEY)
        .filter(
            fn ->
                fn.operationType(f -> f.like("CREATE"))
                    .result(f -> f.in(AuditLogResultEnum.SUCCESS))
                    .timestamp(f -> f.lte(OffsetDateTime.MAX))
                    .actorId(f -> f.eq("actorId"))
                    .actorType(f -> f.neq(AuditLogActorTypeEnum.CLIENT)))
        .send()
        .join();

    // then
    final UserTaskAuditLogSearchQueryRequest request =
        gatewayService.getLastRequest(UserTaskAuditLogSearchQueryRequest.class);
    final UserTaskAuditLogFilter filter = request.getFilter();
    assertThat(filter).isNotNull();
    assertThat(filter.getOperationType().get$Like()).isEqualTo("CREATE");
    assertThat(filter.getResult().get$In().get(0).getValue()).isEqualTo("SUCCESS");
    assertThat(filter.getTimestamp().get$Lte()).isEqualTo(OffsetDateTime.MAX.toString());
    assertThat(filter.getActorId().get$Eq()).isEqualTo("actorId");
    assertThat(filter.getActorType().get$Neq().getValue()).isEqualTo("CLIENT");
  }

  @Test
  void shouldSearchUserTaskAuditLogsWithFullSorting() {
    // when
    client
        .newUserTaskAuditLogSearchRequest(USER_TASK_KEY)
        .sort(s -> s.operationType().asc().result().asc().timestamp().desc())
        .send()
        .join();

    // then
    final UserTaskAuditLogSearchQueryRequest request =
        gatewayService.getLastRequest(UserTaskAuditLogSearchQueryRequest.class);
    final List<SearchRequestSort> sorts =
        SearchRequestSortMapper.fromUserTaskAuditLogSearchQuerySortRequest(
            Objects.requireNonNull(request.getSort()));
    assertThat(sorts).hasSize(3);

    assertSort(sorts.get(0), "operationType", SortOrderEnum.ASC);
    assertSort(sorts.get(1), "result", SortOrderEnum.ASC);
    assertSort(sorts.get(2), "timestamp", SortOrderEnum.DESC);
  }

  @Test
  void shouldIncludeSortAndFilterInUserTaskAuditLogSearchRequestBody() {
    // when
    client
        .newUserTaskAuditLogSearchRequest(USER_TASK_KEY)
        .filter(fn -> fn.actorId("actorId"))
        .sort(s -> s.operationType().desc())
        .page(fn -> fn.limit(5))
        .send()
        .join();

    // then
    final LoggedRequest lastRequest = gatewayService.getLastRequest();
    final String requestBody = lastRequest.getBodyAsString();

    assertThat(requestBody).contains("\"sort\":[{\"field\":\"operationType\",\"order\":\"DESC\"}]");
    assertThat(requestBody).contains("\"filter\":{\"actorId\"");
  }
}
