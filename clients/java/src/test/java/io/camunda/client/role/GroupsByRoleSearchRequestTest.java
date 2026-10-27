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
package io.camunda.client.role;

import static io.camunda.client.impl.http.HttpClientFactory.REST_API_PATH;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.http.RequestMethod;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import io.camunda.client.api.search.filter.RoleGroupFilter;
import io.camunda.client.util.ClientRestTest;
import org.junit.jupiter.api.Test;

public class GroupsByRoleSearchRequestTest extends ClientRestTest {

  private static final String ROLE_ID = "roleId";

  @Test
  void shouldSendSearchGroupsByRoleRequest() {
    client.newGroupsByRoleSearchRequest(ROLE_ID).send().join();

    final LoggedRequest request = gatewayService.getLastRequest();
    assertThat(request.getUrl()).startsWith(REST_API_PATH + "/roles/" + ROLE_ID + "/groups/search");
    assertThat(request.getMethod()).isEqualTo(RequestMethod.POST);
  }

  @Test
  void shouldIncludedSortInSearchRequestBody() {
    // when
    client.newGroupsByRoleSearchRequest(ROLE_ID).sort(s -> s.groupId().desc()).send().join();

    // then
    final LoggedRequest lastRequest = gatewayService.getLastRequest();
    final String requestBody = lastRequest.getBodyAsString();

    assertThat(requestBody).contains("\"sort\":[{\"field\":\"groupId\",\"order\":\"DESC\"}]");
  }

  @Test
  void shouldFailOnEmptyRoleId() {
    assertThatThrownBy(() -> client.newGroupsByRoleSearchRequest("").send().join())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("roleId must not be empty");
  }

  @Test
  void shouldFailOnNullRoleId() {
    assertThatThrownBy(() -> client.newGroupsByRoleSearchRequest(null).send().join())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("roleId must not be null");
  }

  @Test
  void shouldRaiseExceptionWhenFilteringFunctionIsPresentWhenSearchingGroupsByRole() {
    assertThatThrownBy(
            () -> client.newGroupsByRoleSearchRequest(ROLE_ID).filter(fn -> {}).send().join())
        .isInstanceOf(UnsupportedOperationException.class)
        .hasMessageContaining("This command does not support filtering");
  }

  @Test
  void shouldRaiseExceptionWhenFilteringIsPresentWhenSearchingGroupsByRole() {
    assertThatThrownBy(
            () ->
                client
                    .newGroupsByRoleSearchRequest(ROLE_ID)
                    .filter(new RoleGroupFilter() {})
                    .send()
                    .join())
        .isInstanceOf(UnsupportedOperationException.class)
        .hasMessageContaining("This command does not support filtering");
  }
}
