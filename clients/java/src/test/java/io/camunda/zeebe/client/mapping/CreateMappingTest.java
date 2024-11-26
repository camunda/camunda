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
package io.camunda.zeebe.client.mapping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.client.protocol.rest.MappingRuleCreateRequest;
import io.camunda.zeebe.client.util.ClientRestTest;
import org.junit.jupiter.api.Test;

public class CreateMappingTest extends ClientRestTest {

  public static final String CLAIM_NAME = "claimName";
  public static final String CLAIM_VALUE = "claimValue";

  @Test
  void shouldCreateMapping() {
    // when
    client.newCreateMappingCommand().claimName(CLAIM_NAME).claimValue(CLAIM_VALUE).send().join();

    // then
    final MappingRuleCreateRequest request =
        gatewayService.getLastRequest(MappingRuleCreateRequest.class);
    assertThat(request.getClaimName()).isEqualTo(CLAIM_NAME);
    assertThat(request.getClaimValue()).isEqualTo(CLAIM_VALUE);
  }

  @Test
  void shouldRaiseExceptionOnNullClaimName() {
    // when / then
    assertThatThrownBy(() -> client.newCreateMappingCommand().claimValue(CLAIM_VALUE).send().join())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("claimName");
  }

  @Test
  void shouldRaiseExceptionOnNullClaimValue() {
    // when / then
    assertThatThrownBy(() -> client.newCreateMappingCommand().claimName(CLAIM_NAME).send().join())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("claimValue");
  }
}