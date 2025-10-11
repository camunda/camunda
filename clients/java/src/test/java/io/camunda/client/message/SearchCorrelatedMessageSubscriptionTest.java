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
package io.camunda.client.message;

import static io.camunda.client.util.assertions.SortAssert.assertSort;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.http.RequestMethod;
import io.camunda.client.impl.search.request.SearchRequestSort;
import io.camunda.client.impl.search.request.SearchRequestSortMapper;
import io.camunda.client.protocol.rest.CorrelatedMessageSubscriptionSearchQuery;
import io.camunda.client.protocol.rest.SortOrderEnum;
import io.camunda.client.util.ClientRestTest;
import io.camunda.client.util.RestGatewayService;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;

public class SearchCorrelatedMessageSubscriptionTest extends ClientRestTest {

  @Test
  void shouldSearchWithEmptyQuery() {
    // When
    client.newCorrelatedMessageSubscriptionSearchRequest().send().join();

    // Then
    assertThat(RestGatewayService.getLastRequest()).isNotNull();
    assertThat(RestGatewayService.getLastRequest().getMethod()).isEqualTo(RequestMethod.POST);
    assertThat(RestGatewayService.getLastRequest().getUrl())
        .isEqualTo("/v2/correlated-message-subscriptions/search");
    final CorrelatedMessageSubscriptionSearchQuery request =
        gatewayService.getLastRequest(CorrelatedMessageSubscriptionSearchQuery.class);
    assertThat(request.getFilter()).isNull();
    assertThat(request.getSort()).isNullOrEmpty();
    assertThat(request.getPage()).isNull();
  }

  @Test
  void shouldSearchWithAllFilters() {
    // Given
    final OffsetDateTime correlationTime = Instant.now().atOffset(ZoneOffset.UTC);
    // When
    client
        .newCorrelatedMessageSubscriptionSearchRequest()
        .filter(
            f ->
                f.correlationKey("correlation-key")
                    .correlationTime(correlationTime)
                    .elementId("flow-node-id")
                    .elementInstanceKey(789L)
                    .messageKey(123L)
                    .messageName("message-name")
                    .partitionId(3)
                    .processDefinitionId("process-definition-id")
                    .processDefinitionKey(654L)
                    .processInstanceKey(456L)
                    .subscriptionKey(987L)
                    .tenantId("tenant-id"))
        .send()
        .join();

    // Then
    final CorrelatedMessageSubscriptionSearchQuery request =
        gatewayService.getLastRequest(CorrelatedMessageSubscriptionSearchQuery.class);
    assertThat(request.getFilter()).isNotNull();
    assertThat(request.getFilter().getCorrelationKey()).isNotNull();
    assertThat(request.getFilter().getCorrelationKey().get$Eq()).isEqualTo("correlation-key");
    assertThat(request.getFilter().getCorrelationTime()).isNotNull();
    assertThat(request.getFilter().getCorrelationTime().get$Eq())
        .isEqualTo(correlationTime.toString());
    assertThat(request.getFilter().getElementId()).isNotNull();
    assertThat(request.getFilter().getElementId().get$Eq()).isEqualTo("flow-node-id");
    assertThat(request.getFilter().getElementInstanceKey()).isNotNull();
    assertThat(request.getFilter().getElementInstanceKey().get$Eq()).isEqualTo("789");
    assertThat(request.getFilter().getMessageKey()).isNotNull();
    assertThat(request.getFilter().getMessageKey().get$Eq()).isEqualTo("123");
    assertThat(request.getFilter().getMessageName()).isNotNull();
    assertThat(request.getFilter().getMessageName().get$Eq()).isEqualTo("message-name");
    assertThat(request.getFilter().getPartitionId()).isNotNull();
    assertThat(request.getFilter().getPartitionId().get$Eq()).isEqualTo(3);
    assertThat(request.getFilter().getProcessDefinitionId()).isNotNull();
    assertThat(request.getFilter().getProcessDefinitionId().get$Eq())
        .isEqualTo("process-definition-id");
    assertThat(request.getFilter().getProcessDefinitionKey()).isNotNull();
    assertThat(request.getFilter().getProcessDefinitionKey().get$Eq()).isEqualTo("654");
    assertThat(request.getFilter().getProcessInstanceKey()).isNotNull();
    assertThat(request.getFilter().getProcessInstanceKey().get$Eq()).isEqualTo("456");
    assertThat(request.getFilter().getSubscriptionKey()).isNotNull();
    assertThat(request.getFilter().getSubscriptionKey().get$Eq()).isEqualTo("987");
    assertThat(request.getFilter().getTenantId()).isNotNull();
    assertThat(request.getFilter().getTenantId().get$Eq()).isEqualTo("tenant-id");
  }

  @Test
  void shouldSearchWithAllSorting() {
    // When
    client
        .newCorrelatedMessageSubscriptionSearchRequest()
        .sort(
            s ->
                s.correlationKey()
                    .asc()
                    .correlationTime()
                    .asc()
                    .elementId()
                    .asc()
                    .elementInstanceKey()
                    .desc()
                    .messageKey()
                    .asc()
                    .messageName()
                    .desc()
                    .partitionId()
                    .asc()
                    .processDefinitionId()
                    .desc()
                    .processDefinitionKey()
                    .asc()
                    .processInstanceKey()
                    .desc()
                    .subscriptionKey()
                    .asc()
                    .tenantId()
                    .desc())
        .send()
        .join();

    // Then
    final CorrelatedMessageSubscriptionSearchQuery request =
        gatewayService.getLastRequest(CorrelatedMessageSubscriptionSearchQuery.class);
    final List<SearchRequestSort> sorts =
        SearchRequestSortMapper.fromCorrelatedMessageSubscriptionSearchQuerySortRequest(
            Objects.requireNonNull(request.getSort()));
    assertThat(sorts).hasSize(12);
    assertSort(sorts.get(0), "correlationKey", SortOrderEnum.ASC);
    assertSort(sorts.get(1), "correlationTime", SortOrderEnum.ASC);
    assertSort(sorts.get(2), "elementId", SortOrderEnum.ASC);
    assertSort(sorts.get(3), "elementInstanceKey", SortOrderEnum.DESC);
    assertSort(sorts.get(4), "messageKey", SortOrderEnum.ASC);
    assertSort(sorts.get(5), "messageName", SortOrderEnum.DESC);
    assertSort(sorts.get(6), "partitionId", SortOrderEnum.ASC);
    assertSort(sorts.get(7), "processDefinitionId", SortOrderEnum.DESC);
    assertSort(sorts.get(8), "processDefinitionKey", SortOrderEnum.ASC);
    assertSort(sorts.get(9), "processInstanceKey", SortOrderEnum.DESC);
    assertSort(sorts.get(10), "subscriptionKey", SortOrderEnum.ASC);
    assertSort(sorts.get(11), "tenantId", SortOrderEnum.DESC);
  }
}
