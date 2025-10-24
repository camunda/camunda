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
package io.camunda.client.tenant;

import static io.camunda.client.impl.http.HttpClientFactory.REST_API_PATH;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.http.RequestMethod;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import io.camunda.client.api.search.filter.ClientFilter;
import io.camunda.client.util.ClientRestTest;
import io.camunda.client.util.RestGatewayService;
import org.junit.jupiter.api.Test;

public class SearchClientByTenantTest extends ClientRestTest {

  private static final String TENANT_ID = "tenant-123";

  @Test
  void shouldSendSearchRequestForClientsByTenant() {
    // when
    client.newClientsByTenantSearchRequest(TENANT_ID).send().join();

    // then
    final String requestPath = RestGatewayService.getLastRequest().getUrl();
    assertThat(requestPath).isEqualTo(REST_API_PATH + "/tenants/" + TENANT_ID + "/clients/search");
    assertThat(RestGatewayService.getLastRequest().getMethod()).isEqualTo(RequestMethod.POST);
  }

  @Test
  void shouldIncludeSortAndPaginationInSearchRequestBody() {
    // when
    client
        .newClientsByTenantSearchRequest(TENANT_ID)
        .sort(s -> s.clientId().desc())
        .page(p -> p.limit(2))
        .send()
        .join();

    // then
    final LoggedRequest lastRequest = RestGatewayService.getLastRequest();
    final String requestBody = lastRequest.getBodyAsString();

    assertThat(requestBody).contains("\"sort\":[{\"field\":\"clientId\",\"order\":\"DESC\"}]");
    assertThat(requestBody).contains("\"page\":{\"limit\":2,\"after\":null,\"before\":null}}");
  }

  @Test
  void shouldRaiseExceptionOnNullTenantId() {
    assertThatThrownBy(() -> client.newClientsByTenantSearchRequest(null).send().join())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("tenantId must not be null");
  }

  @Test
  void shouldRaiseExceptionOnEmptyTenantId() {
    assertThatThrownBy(() -> client.newClientsByTenantSearchRequest("").send().join())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("tenantId must not be empty");
  }

  @Test
  void shouldRaiseExceptionWhenSettingFilter() {
    assertThatThrownBy(
            () -> client.newClientsByTenantSearchRequest(TENANT_ID).filter(fn -> {}).send().join())
        .isInstanceOf(UnsupportedOperationException.class)
        .hasMessageContaining("This command does not support filtering");
  }

  @Test
  void shouldRaiseExceptionWhenSettingFiltering() {
    assertThatThrownBy(
            () ->
                client
                    .newClientsByTenantSearchRequest(TENANT_ID)
                    .filter(new ClientFilter() {})
                    .send()
                    .join())
        .isInstanceOf(UnsupportedOperationException.class)
        .hasMessageContaining("This command does not support filtering");
  }
}
