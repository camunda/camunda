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
package io.camunda.client.correlatedmessage;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.http.RequestMethod;
import io.camunda.client.protocol.rest.CorrelatedMessageSearchQuery;
import io.camunda.client.util.ClientRestTest;
import io.camunda.client.util.RestGatewayService;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

public class SearchCorrelatedMessageTest extends ClientRestTest {

  @Test
  void shouldSearchWithEmptyQuery() {
    // When
    client.newCorrelatedMessageSearchRequest().send().join();

    // Then
    assertThat(RestGatewayService.getLastRequest()).isNotNull();
    assertThat(RestGatewayService.getLastRequest().getMethod()).isEqualTo(RequestMethod.POST);
    assertThat(RestGatewayService.getLastRequest().getUrl())
        .isEqualTo("/v2/correlated-messages/search");
    final CorrelatedMessageSearchQuery request =
        gatewayService.getLastRequest(CorrelatedMessageSearchQuery.class);
    assertThat(request.getFilter()).isNull();
    assertThat(request.getSort()).isNullOrEmpty();
    assertThat(request.getPage()).isNull();
  }

  @Test
  void shouldSearchWithPagedResults() {
    // When
    client.newCorrelatedMessageSearchRequest().page(p -> p.from(1).limit(2)).send().join();

    // Then
    final CorrelatedMessageSearchQuery request =
        gatewayService.getLastRequest(CorrelatedMessageSearchQuery.class);
    assertThat(request.getPage()).isNotNull();
    assertThat(request.getPage().getFrom()).isEqualTo(1);
    assertThat(request.getPage().getLimit()).isEqualTo(2);
  }

  @Test
  void shouldSearchWithBasicFilters() {
    final OffsetDateTime correlationTime =
        OffsetDateTime.ofInstant(Instant.ofEpochMilli(12345), ZoneOffset.UTC);

    // When
    client
        .newCorrelatedMessageSearchRequest()
        .filter(
            f ->
                f.correlationKey("correlation-123")
                    .correlationTime(correlationTime)
                    .messageName("order-processed")
                    .tenantId("tenant-222"))
        .send()
        .join();

    // Then
    final CorrelatedMessageSearchQuery request =
        gatewayService.getLastRequest(CorrelatedMessageSearchQuery.class);
    assertThat(request.getFilter()).isNotNull();
    assertThat(request.getFilter().getCorrelationKey()).isNotNull();
    assertThat(request.getFilter().getCorrelationKey().get$Eq()).isEqualTo("correlation-123");
    assertThat(request.getFilter().getCorrelationTime()).isNotNull();
    assertThat(request.getFilter().getCorrelationTime().get$Eq())
        .isEqualTo("1970-01-01T00:00:12.345Z");
    assertThat(request.getFilter().getMessageName()).isNotNull();
    assertThat(request.getFilter().getMessageName().get$Eq()).isEqualTo("order-processed");
    assertThat(request.getFilter().getTenantId()).isNotNull();
    assertThat(request.getFilter().getTenantId().get$Eq()).isEqualTo("tenant-222");
  }

  @Test
  void shouldSearchWithSorting() {
    // When
    client
        .newCorrelatedMessageSearchRequest()
        .sort(s -> s.correlationKey().asc().correlationTime().desc().messageName().asc())
        .send()
        .join();

    // Then
    final CorrelatedMessageSearchQuery request =
        gatewayService.getLastRequest(CorrelatedMessageSearchQuery.class);
    assertThat(request.getSort()).hasSize(3);
    assertThat(request.getSort().get(0).getField().toString()).isEqualTo("correlationKey");
    assertThat(request.getSort().get(1).getField().toString()).isEqualTo("correlationTime");
    assertThat(request.getSort().get(2).getField().toString()).isEqualTo("messageName");
  }
}
