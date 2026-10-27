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

import io.camunda.client.util.ClientRestTest;
import io.camunda.client.util.RestGatewayService;
import org.junit.jupiter.api.Test;

public class AssignRoleToMappingRuleTest extends ClientRestTest {

  public static final String ROLE_ID = "roleId";
  public static final String MAPPING_RULE_ID = "mappingRuleId";

  @Test
  void shouldAssignRoleToMappingRule() {
    // when
    client
        .newAssignRoleToMappingRuleCommand()
        .roleId(ROLE_ID)
        .mappingRuleId(MAPPING_RULE_ID)
        .send()
        .join();

    // then
    final String requestPath = RestGatewayService.getLastRequest().getUrl();
    assertThat(requestPath)
        .isEqualTo(REST_API_PATH + "/roles/" + ROLE_ID + "/mapping-rules/" + MAPPING_RULE_ID);
  }

  @Test
  void shouldRaiseExceptionOnNullRoleId() {
    // when / then
    assertThatThrownBy(
            () ->
                client
                    .newAssignRoleToMappingRuleCommand()
                    .roleId(null)
                    .mappingRuleId(MAPPING_RULE_ID)
                    .send()
                    .join())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("roleId must not be null");
  }

  @Test
  void shouldRaiseExceptionOnEmptyRoleId() {
    // when / then
    assertThatThrownBy(
            () ->
                client
                    .newAssignRoleToMappingRuleCommand()
                    .roleId("")
                    .mappingRuleId(MAPPING_RULE_ID)
                    .send()
                    .join())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("roleId must not be empty");
  }

  @Test
  void shouldRaiseExceptionOnNullMappingRuleId() {
    // when / then
    assertThatThrownBy(
            () ->
                client
                    .newAssignRoleToMappingRuleCommand()
                    .roleId(ROLE_ID)
                    .mappingRuleId(null)
                    .send()
                    .join())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("mappingRuleId must not be null");
  }

  @Test
  void shouldRaiseExceptionOnEmptyMappingRuleId() {
    // when / then
    assertThatThrownBy(
            () ->
                client
                    .newAssignRoleToMappingRuleCommand()
                    .roleId(ROLE_ID)
                    .mappingRuleId("")
                    .send()
                    .join())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("mappingRuleId must not be empty");
  }
}
