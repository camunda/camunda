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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.http.RequestMethod;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import io.camunda.client.api.search.sort.TenantSort;
import io.camunda.client.protocol.rest.TenantResult;
import io.camunda.client.util.ClientRestTest;
import org.instancio.Instancio;
import org.junit.jupiter.api.Test;

public class SearchTenantTest extends ClientRestTest {

  public static final String TENANT_ID = "tenantId";

  @Test
  public void shouldSearchTenantByTenantId() {
    // given
    gatewayService.onTenantRequest(TENANT_ID, Instancio.create(TenantResult.class));

    // when
    client.newTenantGetRequest(TENANT_ID).send().join();

    // then
    final LoggedRequest request = gatewayService.getLastRequest();
    assertThat(request.getUrl()).isEqualTo("/v2/tenants/" + TENANT_ID);
    assertThat(request.getMethod()).isEqualTo(RequestMethod.GET);
  }

  @Test
  void shouldRaiseExceptionOnNullTenantId() {
    // when / then
    assertThatThrownBy(() -> client.newTenantGetRequest(null).send().join())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("tenantId must not be null");
  }

  @Test
  void shouldRaiseExceptionOnEmptyTenantId() {
    // when / then
    assertThatThrownBy(() -> client.newTenantGetRequest("").send().join())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("tenantId must not be empty");
  }

  @Test
  public void shouldSearchTenants() {
    // when
    client
        .newTenantsSearchRequest()
        .filter(fn -> fn.name("tenantName"))
        .sort(TenantSort::name)
        .page(fn -> fn.limit(5))
        .send()
        .join();

    // then
    final LoggedRequest request = gatewayService.getLastRequest();
    assertThat(request.getUrl()).isEqualTo("/v2/tenants/search");
    assertThat(request.getMethod()).isEqualTo(RequestMethod.POST);
  }

  @Test
  void shouldIncludeSortAndFilterInTenantsSearchRequestBody() {
    // when
    client
        .newTenantsSearchRequest()
        .filter(fn -> fn.name("tenantName"))
        .sort(s -> s.name().desc())
        .page(fn -> fn.limit(5))
        .send()
        .join();

    // then
    final LoggedRequest lastRequest = gatewayService.getLastRequest();
    final String requestBody = lastRequest.getBodyAsString();

    assertThat(requestBody).contains("\"sort\":[{\"field\":\"name\",\"order\":\"DESC\"}]");
    assertThat(requestBody).contains("\"filter\":{\"name\"");
  }
}
