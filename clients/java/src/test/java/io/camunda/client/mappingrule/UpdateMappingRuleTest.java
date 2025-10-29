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

import io.camunda.client.protocol.rest.MappingRuleUpdateRequest;
import io.camunda.client.protocol.rest.MappingRuleUpdateResult;
import io.camunda.client.util.ClientRestTest;
import org.instancio.Instancio;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;

public class UpdateMappingRuleTest extends ClientRestTest {
  private static final String MAPPING_RULE_ID = "mappingRuleId";
  private static final String UPDATED_NAME = "Updated Mapping Rule Name";
  private static final String UPDATED_CLAIM_NAME = "Updated Claim Name";
  private static final String UPDATED_CLAIM_VALUE = "Updated Claim Name";

  @Test
  void shouldUpdateMappingRule() {
    // given
    gatewayService.onUpdateMappingRuleRequest(
        MAPPING_RULE_ID, Instancio.create(MappingRuleUpdateResult.class));

    // when
    client
        .newUpdateMappingRuleCommand(MAPPING_RULE_ID)
        .name(UPDATED_NAME)
        .claimName(UPDATED_CLAIM_NAME)
        .claimValue(UPDATED_CLAIM_VALUE)
        .send()
        .join();

    // then
    final MappingRuleUpdateRequest request =
        gatewayService.getLastRequest(MappingRuleUpdateRequest.class);
    assertThat(request.getName()).isEqualTo(UPDATED_NAME);
    assertThat(request.getClaimName()).isEqualTo(UPDATED_CLAIM_NAME);
    assertThat(request.getClaimValue()).isEqualTo(UPDATED_CLAIM_VALUE);
  }

  @ParameterizedTest
  @NullAndEmptySource
  void shouldRaiseExceptionInvalidName(final String invalidName) {
    // when / then
    assertThatThrownBy(
            () ->
                client
                    .newUpdateMappingRuleCommand(MAPPING_RULE_ID)
                    .name(invalidName)
                    .claimName(UPDATED_CLAIM_NAME)
                    .claimValue(UPDATED_CLAIM_VALUE)
                    .send()
                    .join())
        .isInstanceOf(IllegalArgumentException.class);
  }

  @ParameterizedTest
  @NullAndEmptySource
  void shouldRaiseExceptionInvalidClaimName(final String invalidClaimName) {
    // when / then
    assertThatThrownBy(
            () ->
                client
                    .newUpdateMappingRuleCommand(MAPPING_RULE_ID)
                    .name(UPDATED_NAME)
                    .claimName(invalidClaimName)
                    .claimValue(UPDATED_CLAIM_VALUE)
                    .send()
                    .join())
        .isInstanceOf(IllegalArgumentException.class);
  }

  @ParameterizedTest
  @NullAndEmptySource
  void shouldRaiseExceptionInvalidClaimValue(final String invalidClaimValue) {
    // when / then
    assertThatThrownBy(
            () ->
                client
                    .newUpdateMappingRuleCommand(MAPPING_RULE_ID)
                    .name(UPDATED_NAME)
                    .claimName(UPDATED_CLAIM_NAME)
                    .claimValue(invalidClaimValue)
                    .send()
                    .join())
        .isInstanceOf(IllegalArgumentException.class);
  }
}
