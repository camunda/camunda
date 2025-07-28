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
package io.camunda.client.group;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.http.RequestMethod;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import io.camunda.client.api.search.filter.ClientFilter;
import io.camunda.client.api.search.filter.GroupUserFilter;
import io.camunda.client.api.search.sort.ClientSort;
import io.camunda.client.api.search.sort.GroupSort;
import io.camunda.client.api.search.sort.GroupUserSort;
import io.camunda.client.api.search.sort.MappingRuleSort;
import io.camunda.client.api.search.sort.RoleSort;
import io.camunda.client.util.ClientRestTest;
import org.junit.jupiter.api.Test;

public class GroupSearchTest extends ClientRestTest {

  public static final String GROUP_ID = "groupId";

  @Test
  public void testGroupSearch() {
    // when
    client.newGroupGetRequest(GROUP_ID).send().join();

    // then
    final LoggedRequest request = gatewayService.getLastRequest();
    assertThat(request.getUrl()).isEqualTo("/v2/groups/" + GROUP_ID);
    assertThat(request.getMethod()).isEqualTo(RequestMethod.GET);
  }

  @Test
  public void testGroupsSearch() {
    // when
    client
        .newGroupsSearchRequest()
        .filter(fn -> fn.name("groupName"))
        .sort(GroupSort::name)
        .page(fn -> fn.limit(5))
        .send()
        .join();

    // then
    final LoggedRequest request = gatewayService.getLastRequest();
    assertThat(request.getUrl()).isEqualTo("/v2/groups/search");
    assertThat(request.getMethod()).isEqualTo(RequestMethod.POST);
  }

  @Test
  public void testGroupsSearchByIds() {
    // when
    client
        .newGroupsSearchRequest()
        .filter(fn -> fn.groupId(b -> b.in("group1", "group2")))
        .sort(GroupSort::name)
        .page(fn -> fn.limit(5))
        .send()
        .join();

    // then
    final LoggedRequest request = gatewayService.getLastRequest();
    assertThat(request.getUrl()).isEqualTo("/v2/groups/search");
    assertThat(request.getMethod()).isEqualTo(RequestMethod.POST);
  }

  @Test
  public void testUsersSearchByGroup() {
    // when
    client
        .newUsersByGroupSearchRequest(GROUP_ID)
        .sort(GroupUserSort::username)
        .page(fn -> fn.limit(5))
        .send()
        .join();

    // then
    final LoggedRequest request = gatewayService.getLastRequest();
    assertThat(request.getUrl()).isEqualTo("/v2/groups/groupId/users/search");
    assertThat(request.getMethod()).isEqualTo(RequestMethod.POST);
  }

  @Test
  void shouldRaiseExceptionWhenFilteringFunctionIsPresentWhenSearchingUsersByGroup() {
    assertThatThrownBy(
            () -> client.newUsersByGroupSearchRequest(GROUP_ID).filter(fn -> {}).send().join())
        .isInstanceOf(UnsupportedOperationException.class)
        .hasMessageContaining("This command does not support filtering");
  }

  @Test
  void shouldRaiseExceptionWhenFilteringIsPresentWhenSearchingUsersByGroup() {
    assertThatThrownBy(
            () ->
                client
                    .newUsersByGroupSearchRequest(GROUP_ID)
                    .filter(new GroupUserFilter() {})
                    .send()
                    .join())
        .isInstanceOf(UnsupportedOperationException.class)
        .hasMessageContaining("This command does not support filtering");
  }

  @Test
  public void testMappingsByGroupSearch() {
    // when
    client
        .newMappingRulesByGroupSearchRequest(GROUP_ID)
        .filter(fn -> fn.mappingRuleId("mappingRuleId"))
        .sort(MappingRuleSort::name)
        .page(fn -> fn.limit(5))
        .send()
        .join();

    // then
    final LoggedRequest request = gatewayService.getLastRequest();
    assertThat(request.getUrl()).isEqualTo("/v2/groups/groupId/mapping-rules/search");
    assertThat(request.getMethod()).isEqualTo(RequestMethod.POST);
  }

  @Test
  public void testRolesSearchByGroup() {
    // when
    client
        .newRolesByGroupSearchRequest(GROUP_ID)
        .filter(fn -> fn.name("roleName"))
        .sort(RoleSort::name)
        .page(fn -> fn.limit(5))
        .send()
        .join();

    // then
    final LoggedRequest request = gatewayService.getLastRequest();
    assertThat(request.getUrl()).isEqualTo("/v2/groups/groupId/roles/search");
    assertThat(request.getMethod()).isEqualTo(RequestMethod.POST);
  }

  @Test
  public void testClientsSearchByGroup() {
    // when
    client
        .newClientsByGroupSearchRequest(GROUP_ID)
        .sort(ClientSort::clientId)
        .page(fn -> fn.limit(5))
        .send()
        .join();

    // then
    final LoggedRequest request = gatewayService.getLastRequest();
    assertThat(request.getUrl()).isEqualTo("/v2/groups/groupId/clients/search");
    assertThat(request.getMethod()).isEqualTo(RequestMethod.POST);
  }

  @Test
  void shouldRaiseExceptionOnNullGroupIdWhenSearchingClientsByGroupId() {
    // when / then
    assertThatThrownBy(() -> client.newClientsByGroupSearchRequest(null).send().join())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("groupId must not be null");
  }

  @Test
  void shouldRaiseExceptionOnEmptyGroupIdWhenSearchingClientsByGroupId() {
    // when / then
    assertThatThrownBy(() -> client.newClientsByGroupSearchRequest("").send().join())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("groupId must not be empty");
  }

  @Test
  void shouldIncludeSortInClientsByGroupSearchRequestBody() {
    // when
    client
        .newClientsByGroupSearchRequest(GROUP_ID)
        .sort(s -> s.clientId().desc())
        .page(fn -> fn.limit(5))
        .send()
        .join();

    // then
    final LoggedRequest lastRequest = gatewayService.getLastRequest();
    final String requestBody = lastRequest.getBodyAsString();

    assertThat(requestBody).contains("\"sort\":[{\"field\":\"clientId\",\"order\":\"DESC\"}]");
  }

  @Test
  void shouldRaiseExceptionWhenFilteringFunctionIsPresentWhenSearchingClientsByGroup() {
    assertThatThrownBy(
            () -> client.newClientsByGroupSearchRequest(GROUP_ID).filter(fn -> {}).send().join())
        .isInstanceOf(UnsupportedOperationException.class)
        .hasMessageContaining("This command does not support filtering");
  }

  @Test
  void shouldRaiseExceptionWhenFilteringIsPresentWhenSearchingClientsByGroup() {
    assertThatThrownBy(
            () ->
                client
                    .newClientsByGroupSearchRequest(GROUP_ID)
                    .filter(new ClientFilter() {})
                    .send()
                    .join())
        .isInstanceOf(UnsupportedOperationException.class)
        .hasMessageContaining("This command does not support filtering");
  }
}
