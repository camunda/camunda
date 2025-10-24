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
import io.camunda.client.util.ClientRestTest;
import io.camunda.client.util.RestGatewayService;
import org.junit.jupiter.api.Test;

public class UnassignMappingRuleFromTenantTest extends ClientRestTest {

  public static final String MAPPING_RULE_ID = "mappingRuleId";
  public static final String TENANT_ID = "tenantId";

  @Test
  void shouldUnassignMappingRuleFromTenant() {
    // when
    client
        .newUnassignMappingRuleFromTenantCommand()
        .mappingRuleId(MAPPING_RULE_ID)
        .tenantId(TENANT_ID)
        .send()
        .join();

    // then
    final String requestPath = RestGatewayService.getLastRequest().getUrl();
    final RequestMethod method = RestGatewayService.getLastRequest().getMethod();
    assertThat(requestPath)
        .isEqualTo(REST_API_PATH + "/tenants/" + TENANT_ID + "/mapping-rules/" + MAPPING_RULE_ID);
    assertThat(method).isEqualTo(RequestMethod.DELETE);
  }

  @Test
  void shouldRaiseExceptionOnNullMappingRuleIdWhenUnassigningMappingRuleFromTenant() {
    // when / then
    assertThatThrownBy(
            () ->
                client
                    .newUnassignMappingRuleFromTenantCommand()
                    .mappingRuleId(null)
                    .tenantId(TENANT_ID)
                    .send()
                    .join())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("mappingRuleId must not be null");
  }

  @Test
  void shouldRaiseExceptionOnEmptyMappingRuleIdWhenUnassigningMappingRuleFromTenant() {
    // when / then
    assertThatThrownBy(
            () ->
                client
                    .newUnassignMappingRuleFromTenantCommand()
                    .mappingRuleId("")
                    .tenantId(TENANT_ID)
                    .send()
                    .join())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("mappingRuleId must not be empty");
  }

  @Test
  void shouldRaiseExceptionOnNullTenantIdWhenUnassigningMappingRuleFromTenant() {
    // when / then
    assertThatThrownBy(
            () ->
                client
                    .newUnassignMappingRuleFromTenantCommand()
                    .mappingRuleId(MAPPING_RULE_ID)
                    .tenantId(null)
                    .send()
                    .join())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("tenantId must not be null");
  }

  @Test
  void shouldRaiseExceptionOnEmptyTenantIdWhenUnassigningMappingRuleFromTenant() {
    // when / then
    assertThatThrownBy(
            () ->
                client
                    .newUnassignMappingRuleFromTenantCommand()
                    .mappingRuleId(MAPPING_RULE_ID)
                    .tenantId("")
                    .send()
                    .join())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("tenantId must not be empty");
  }
}
