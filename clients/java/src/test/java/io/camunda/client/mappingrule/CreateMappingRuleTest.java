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
package io.camunda.client.mappingrule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.client.protocol.rest.MappingRuleCreateRequest;
import io.camunda.client.util.ClientRestTest;
import org.junit.jupiter.api.Test;

public class CreateMappingRuleTest extends ClientRestTest {

  public static final String CLAIM_NAME = "claimName";
  public static final String CLAIM_VALUE = "claimValue";
  public static final String NAME = "mappingRuleName";
  public static final String MAPPING_RULE_ID = "mappingRuleId";

  @Test
  void shouldCreateMappingRule() {
    // when
    client
        .newCreateMappingRuleCommand()
        .claimName(CLAIM_NAME)
        .claimValue(CLAIM_VALUE)
        .name(NAME)
        .mappingRuleId(MAPPING_RULE_ID)
        .send()
        .join();

    // then
    final MappingRuleCreateRequest request =
        gatewayService.getLastRequest(MappingRuleCreateRequest.class);
    assertThat(request.getClaimName()).isEqualTo(CLAIM_NAME);
    assertThat(request.getClaimValue()).isEqualTo(CLAIM_VALUE);
    assertThat(request.getName()).isEqualTo(NAME);
    assertThat(request.getMappingRuleId()).isEqualTo(MAPPING_RULE_ID);
  }

  @Test
  void shouldRaiseExceptionOnNullClaimName() {
    // when / then
    assertThatThrownBy(
            () ->
                client
                    .newCreateMappingRuleCommand()
                    .claimValue(CLAIM_VALUE)
                    .name(NAME)
                    .send()
                    .join())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("claimName");
  }

  @Test
  void shouldRaiseExceptionOnNullClaimValue() {
    // when / then
    assertThatThrownBy(
            () ->
                client.newCreateMappingRuleCommand().claimName(CLAIM_NAME).name(NAME).send().join())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("claimValue");
  }

  @Test
  void shouldRaiseExceptionOnNullName() {
    // when / then
    assertThatThrownBy(
            () ->
                client
                    .newCreateMappingRuleCommand()
                    .claimName(CLAIM_NAME)
                    .claimValue(CLAIM_VALUE)
                    .send()
                    .join())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("name");
  }
}
