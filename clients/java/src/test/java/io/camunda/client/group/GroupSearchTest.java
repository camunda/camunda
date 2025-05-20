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

import com.github.tomakehurst.wiremock.http.RequestMethod;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import io.camunda.client.api.search.sort.GroupSort;
import io.camunda.client.api.search.sort.GroupUserSort;
import io.camunda.client.api.search.sort.MappingSort;
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
        .newMappingsByGroupSearchRequest(groupId)
        .filter(fn -> fn.mappingId("mappingId"))
        .sort(MappingSort::name)
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
}
