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
import io.camunda.client.util.ClientRestTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;

public class MappingRulesByTenantSearchRequestTest extends ClientRestTest {

  @Test
  void shouldSendSearchRequest() {
    // given
    final String tenantId = "testId";

    // when
    client.newMappingRulesByTenantSearchRequest(tenantId).send().join();

    // then
    final LoggedRequest request = gatewayService.getLastRequest();
    assertThat(request.getUrl())
        .startsWith(REST_API_PATH + "/tenants/" + tenantId + "/mapping-rules/search");
    assertThat(request.getMethod()).isEqualTo(RequestMethod.POST);
  }

  @ParameterizedTest
  @NullAndEmptySource
  void shouldFailOnNullOrEmpty(final String tenantId) {
    // when / then
    assertThatThrownBy(() -> client.newMappingRulesByTenantSearchRequest(tenantId).send().join())
        .isInstanceOf(IllegalArgumentException.class);
  }
}
