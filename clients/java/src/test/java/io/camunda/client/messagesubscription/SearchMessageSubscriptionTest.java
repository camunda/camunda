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
package io.camunda.client.messagesubscription;

import static io.camunda.client.util.assertions.SortAssert.assertSort;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.http.RequestMethod;
import io.camunda.client.api.search.enums.MessageSubscriptionType;
import io.camunda.client.impl.search.request.SearchRequestSort;
import io.camunda.client.impl.search.request.SearchRequestSortMapper;
import io.camunda.client.protocol.rest.MessageSubscriptionSearchQuery;
import io.camunda.client.protocol.rest.MessageSubscriptionTypeEnum;
import io.camunda.client.protocol.rest.SearchQueryPageRequest;
import io.camunda.client.protocol.rest.SortOrderEnum;
import io.camunda.client.util.ClientRestTest;
import io.camunda.client.util.RestGatewayService;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;

public class SearchMessageSubscriptionTest extends ClientRestTest {

  @Test
  void shouldSearchWithEmptyQuery() {
    // When
    client.newMessageSubscriptionSearchRequest().send().join();

    // Then
    assertThat(RestGatewayService.getLastRequest()).isNotNull();
    assertThat(RestGatewayService.getLastRequest().getMethod()).isEqualTo(RequestMethod.POST);
    assertThat(RestGatewayService.getLastRequest().getUrl())
        .isEqualTo("/v2/message-subscriptions/search");
    final MessageSubscriptionSearchQuery request =
        gatewayService.getLastRequest(MessageSubscriptionSearchQuery.class);
    assertThat(request.getFilter()).isNull();
  }

  @Test
  void shouldSearchWithAllFilters() {
    // Given
    final OffsetDateTime lastUpdatedDate = Instant.now().atOffset(ZoneOffset.UTC);
    // When
    client
        .newMessageSubscriptionSearchRequest()
        .filter(
            f ->
                f.messageSubscriptionKey(123L)
                    .processDefinitionId("process-definition-id")
                    .processInstanceKey(456L)
                    .elementId("element-id")
                    .elementInstanceKey(789L)
                    .messageSubscriptionType(MessageSubscriptionType.CREATED)
                    .lastUpdatedDate(lastUpdatedDate)
                    .messageName("message-name")
                    .correlationKey("correlation-key")
                    .tenantId("tenant-id"))
        .send()
        .join();

    // Then
    final MessageSubscriptionSearchQuery request =
        gatewayService.getLastRequest(MessageSubscriptionSearchQuery.class);
    assertThat(request.getFilter()).isNotNull();
    assertThat(request.getFilter().getMessageSubscriptionKey()).isNotNull();
    assertThat(request.getFilter().getMessageSubscriptionKey().get$Eq()).isEqualTo("123");
    assertThat(request.getFilter().getProcessDefinitionId()).isNotNull();
    assertThat(request.getFilter().getProcessDefinitionId().get$Eq())
        .isEqualTo("process-definition-id");
    assertThat(request.getFilter().getProcessInstanceKey()).isNotNull();
    assertThat(request.getFilter().getProcessInstanceKey().get$Eq()).isEqualTo("456");
    assertThat(request.getFilter().getElementId()).isNotNull();
    assertThat(request.getFilter().getElementId().get$Eq()).isEqualTo("element-id");
    assertThat(request.getFilter().getElementInstanceKey()).isNotNull();
    assertThat(request.getFilter().getElementInstanceKey().get$Eq()).isEqualTo("789");
    assertThat(request.getFilter().getMessageSubscriptionType()).isNotNull();
    assertThat(request.getFilter().getMessageSubscriptionType().get$Eq())
        .isEqualTo(MessageSubscriptionTypeEnum.CREATED);
    assertThat(request.getFilter().getLastUpdatedDate()).isNotNull();
    assertThat(request.getFilter().getLastUpdatedDate().get$Eq())
        .isEqualTo(lastUpdatedDate.toString());
    assertThat(request.getFilter().getMessageName()).isNotNull();
    assertThat(request.getFilter().getMessageName().get$Eq()).isEqualTo("message-name");
    assertThat(request.getFilter().getCorrelationKey()).isNotNull();
    assertThat(request.getFilter().getCorrelationKey().get$Eq()).isEqualTo("correlation-key");
    assertThat(request.getFilter().getTenantId()).isNotNull();
    assertThat(request.getFilter().getTenantId().get$Eq()).isEqualTo("tenant-id");
  }

  @Test
  void shouldSearchWithAllSorting() {
    // When
    client
        .newMessageSubscriptionSearchRequest()
        .sort(
            s ->
                s.messageSubscriptionKey()
                    .asc()
                    .processDefinitionId()
                    .desc()
                    .processInstanceKey()
                    .desc()
                    .elementId()
                    .asc()
                    .elementInstanceKey()
                    .desc()
                    .messageSubscriptionType()
                    .asc()
                    .lastUpdatedDate()
                    .desc()
                    .messageName()
                    .asc()
                    .correlationKey()
                    .desc()
                    .tenantId()
                    .asc())
        .send()
        .join();

    // Then
    final MessageSubscriptionSearchQuery request =
        gatewayService.getLastRequest(MessageSubscriptionSearchQuery.class);
    final List<SearchRequestSort> sorts =
        SearchRequestSortMapper.fromMessageSubscriptionSearchQuerySortRequest(
            Objects.requireNonNull(request.getSort()));
    assertThat(sorts).hasSize(10);
    assertSort(sorts.get(0), "messageSubscriptionKey", SortOrderEnum.ASC);
    assertSort(sorts.get(1), "processDefinitionId", SortOrderEnum.DESC);
    assertSort(sorts.get(2), "processInstanceKey", SortOrderEnum.DESC);
    assertSort(sorts.get(3), "elementId", SortOrderEnum.ASC);
    assertSort(sorts.get(4), "elementInstanceKey", SortOrderEnum.DESC);
    assertSort(sorts.get(5), "messageSubscriptionType", SortOrderEnum.ASC);
    assertSort(sorts.get(6), "lastUpdatedDate", SortOrderEnum.DESC);
    assertSort(sorts.get(7), "messageName", SortOrderEnum.ASC);
    assertSort(sorts.get(8), "correlationKey", SortOrderEnum.DESC);
    assertSort(sorts.get(9), "tenantId", SortOrderEnum.ASC);
  }

  @Test
  void shouldSearchWithFullPagination() {
    // When
    client
        .newMessageSubscriptionSearchRequest()
        .page(p -> p.from(2).limit(3).before("beforeCursor").after("afterCursor"))
        .send()
        .join();

    // Then
    final MessageSubscriptionSearchQuery request =
        gatewayService.getLastRequest(MessageSubscriptionSearchQuery.class);
    final SearchQueryPageRequest pageRequest = request.getPage();
    assertThat(pageRequest).isNotNull();
    assertThat(pageRequest.getFrom()).isEqualTo(2);
    assertThat(pageRequest.getLimit()).isEqualTo(3);
    assertThat(pageRequest.getBefore()).isEqualTo("beforeCursor");
    assertThat(pageRequest.getAfter()).isEqualTo("afterCursor");
  }
}
