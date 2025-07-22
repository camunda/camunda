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
import io.camunda.client.api.search.sort.ClientSort;
import io.camunda.client.api.search.sort.GroupSort;
import io.camunda.client.api.search.sort.GroupUserSort;
import io.camunda.client.api.search.sort.MappingRuleSort;
import io.camunda.client.api.search.sort.RoleSort;
import io.camunda.client.util.ClientRestTest;
import org.junit.jupiter.api.Test;

public class GroupSearchTest extends ClientRestTest {

  @Test
  public void testGroupSearch() {
    // when
    final String groupId = "groupId";
    client.newGroupGetRequest(groupId).send().join();

    // then
    final LoggedRequest request = gatewayService.getLastRequest();
    assertThat(request.getUrl()).isEqualTo("/v2/groups/" + groupId);
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
    final String groupId = "groupId";
    client
        .newUsersByGroupSearchRequest(groupId)
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
  public void testMappingsByGroupSearch() {
    // when
    final String groupId = "groupId";
    client
        .newMappingRulesByGroupSearchRequest(groupId)
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
    final String groupId = "groupId";
    client
        .newRolesByGroupSearchRequest(groupId)
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
    final String groupId = "groupId";
    client
        .newClientsByGroupSearchRequest(groupId)
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
        .newClientsByGroupSearchRequest("groupId")
        .sort(s -> s.clientId().desc())
        .page(fn -> fn.limit(5))
        .send()
        .join();

    // then
    final LoggedRequest lastRequest = gatewayService.getLastRequest();
    final String requestBody = lastRequest.getBodyAsString();

    assertThat(requestBody).contains("\"sort\":[{\"field\":\"clientId\",\"order\":\"DESC\"}]");
  }
}
