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
import io.camunda.client.api.search.filter.GroupFilterBase;
import io.camunda.client.api.search.filter.GroupUserFilter;
import io.camunda.client.api.search.sort.ClientSort;
import io.camunda.client.api.search.sort.GroupSort;
import io.camunda.client.api.search.sort.GroupUserSort;
import io.camunda.client.api.search.sort.MappingRuleSort;
import io.camunda.client.api.search.sort.RoleSort;
import io.camunda.client.protocol.rest.GroupFilter;
import io.camunda.client.protocol.rest.GroupResult;
import io.camunda.client.protocol.rest.GroupSearchQueryRequest;
import io.camunda.client.util.ClientRestTest;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import org.instancio.Instancio;
import org.junit.jupiter.api.Test;

public class GroupSearchTest extends ClientRestTest {

  public static final String GROUP_ID = "groupId";

  @Test
  public void testGroupSearch() {
    // given
    gatewayService.onGroupRequest(GROUP_ID, Instancio.create(GroupResult.class));

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

  @Test
  void shouldSearchGroupsWithOrFilters() {
    // when
    client
        .newGroupsSearchRequest()
        .filter(
            fn ->
                fn.orFilters(
                    Arrays.asList(f1 -> f1.groupId("group-1"), f2 -> f2.groupId("group-2"))))
        .send()
        .join();

    // then
    final GroupSearchQueryRequest request =
        gatewayService.getLastRequest(GroupSearchQueryRequest.class);
    final GroupFilter filter = request.getFilter();
    assertThat(filter).isNotNull();
    assertThat(filter.get$Or()).hasSize(2);
    assertThat(filter.get$Or().get(0).getGroupId().get$Eq()).isEqualTo("group-1");
    assertThat(filter.get$Or().get(1).getGroupId().get$Eq()).isEqualTo("group-2");
  }

  @Test
  void shouldSearchGroupsByNameLike() {
    // when
    client.newGroupsSearchRequest().filter(f -> f.name(b -> b.like("Group*"))).send().join();

    // then
    final GroupSearchQueryRequest request =
        gatewayService.getLastRequest(GroupSearchQueryRequest.class);
    final GroupFilter filter = request.getFilter();
    assertThat(filter).isNotNull();
    assertThat(filter.getName().get$Like()).isEqualTo("Group*");
  }

  @Test
  void shouldHaveMatchingFilterMethodsInBaseAndFullInterfaces() {
    final Set<String> baseMethods = publicMethodSignatures(GroupFilterBase.class);
    final Set<String> fullMethods =
        publicMethodSignatures(io.camunda.client.api.search.filter.GroupFilter.class);

    assertThat(fullMethods)
        .withFailMessage("Full interface is missing methods from base interface")
        .containsAll(baseMethods);

    final Set<String> expectedExtras = new HashSet<>();
    expectedExtras.add("orFilters[interface java.util.List]");
    final Set<String> actualExtras = new HashSet<>(fullMethods);
    actualExtras.removeAll(baseMethods);

    assertThat(actualExtras)
        .withFailMessage("Unexpected methods in full interface: %s", actualExtras)
        .isEqualTo(expectedExtras);
  }

  private static Set<String> publicMethodSignatures(final Class<?> clazz) {
    return Arrays.stream(clazz.getMethods())
        .filter(m -> Modifier.isPublic(m.getModifiers()) && !m.isSynthetic())
        .map(m -> m.getName() + Arrays.toString(m.getParameterTypes()))
        .collect(Collectors.toSet());
  }
}
