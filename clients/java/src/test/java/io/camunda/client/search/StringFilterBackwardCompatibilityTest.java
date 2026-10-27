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
package io.camunda.client.search;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.client.protocol.rest.GroupSearchQueryRequest;
import io.camunda.client.protocol.rest.MappingRuleSearchQueryRequest;
import io.camunda.client.protocol.rest.RoleSearchQueryRequest;
import io.camunda.client.util.ClientRestTest;
import java.io.IOException;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

/**
 * The {@code role.roleId}, {@code mappingRule.mappingRuleId} and {@code group.name} filters were a
 * plain {@code string} before 8.10 and became a {@code StringFilterProperty} (oneOf string |
 * advanced) in 8.10. An exact-match convenience call must still serialize as a bare string so an
 * 8.10 client keeps working against a pre-8.10 cluster; advanced operators may use the 8.10 object
 * form. See {@code io.camunda.client.impl.search.filter.StringFilterPropertyModule}.
 */
public class StringFilterBackwardCompatibilityTest extends ClientRestTest {

  private static final ObjectMapper JSON = new ObjectMapper();

  @Test
  void shouldSerializeExactMatchRoleIdAsBareString() throws IOException {
    // when
    client.newRolesSearchRequest().filter(f -> f.roleId("admin")).send().join();

    // then
    final JsonNode roleId = filterNode().get("roleId");
    assertThat(roleId.isTextual()).isTrue();
    assertThat(roleId.asText()).isEqualTo("admin");
  }

  @Test
  void shouldSerializeAdvancedRoleIdAsObject() throws IOException {
    // when
    client.newRolesSearchRequest().filter(f -> f.roleId(b -> b.like("adm*"))).send().join();

    // then
    final JsonNode roleId = filterNode().get("roleId");
    assertThat(roleId.isObject()).isTrue();
    assertThat(roleId.get("$like").asText()).isEqualTo("adm*");
  }

  @Test
  void shouldRoundTripExactMatchRoleId() {
    // when
    client.newRolesSearchRequest().filter(f -> f.roleId("admin")).send().join();

    // then
    final RoleSearchQueryRequest request =
        gatewayService.getLastRequest(RoleSearchQueryRequest.class);
    assertThat(request.getFilter().getRoleId().get$Eq()).isEqualTo("admin");
  }

  @Test
  void shouldSerializeExactMatchMappingRuleIdAsBareString() throws IOException {
    // when
    client.newMappingRulesSearchRequest().filter(f -> f.mappingRuleId("rule-1")).send().join();

    // then
    final JsonNode mappingRuleId = filterNode().get("mappingRuleId");
    assertThat(mappingRuleId.isTextual()).isTrue();
    assertThat(mappingRuleId.asText()).isEqualTo("rule-1");
  }

  @Test
  void shouldRoundTripExactMatchMappingRuleId() {
    // when
    client.newMappingRulesSearchRequest().filter(f -> f.mappingRuleId("rule-1")).send().join();

    // then
    final MappingRuleSearchQueryRequest request =
        gatewayService.getLastRequest(MappingRuleSearchQueryRequest.class);
    assertThat(request.getFilter().getMappingRuleId().get$Eq()).isEqualTo("rule-1");
  }

  @Test
  void shouldSerializeExactMatchGroupNameAsBareString() throws IOException {
    // when
    client.newGroupsSearchRequest().filter(f -> f.name("Admins")).send().join();

    // then
    final JsonNode name = filterNode().get("name");
    assertThat(name.isTextual()).isTrue();
    assertThat(name.asText()).isEqualTo("Admins");
  }

  @Test
  void shouldRoundTripExactMatchGroupName() {
    // when
    client.newGroupsSearchRequest().filter(f -> f.name("Admins")).send().join();

    // then
    final GroupSearchQueryRequest request =
        gatewayService.getLastRequest(GroupSearchQueryRequest.class);
    assertThat(request.getFilter().getName().get$Eq()).isEqualTo("Admins");
  }

  @Test
  void shouldSerializeExactMatchRoleIdInOrFilterAsBareString() throws IOException {
    // when
    client
        .newRolesSearchRequest()
        .filter(f -> f.orFilters(Arrays.asList(o -> o.roleId("role-1"))))
        .send()
        .join();

    // then
    final JsonNode orRoleId = filterNode().get("$or").get(0).get("roleId");
    assertThat(orRoleId.isTextual()).isTrue();
    assertThat(orRoleId.asText()).isEqualTo("role-1");
  }

  private JsonNode filterNode() throws IOException {
    final String body = gatewayService.getLastRequest().getBodyAsString();
    return JSON.readTree(body).get("filter");
  }
}
