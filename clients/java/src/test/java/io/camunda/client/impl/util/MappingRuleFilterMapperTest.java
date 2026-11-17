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
package io.camunda.client.impl.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.client.protocol.rest.MappingRuleFilter;
import io.camunda.client.protocol.rest.MappingRuleFilterFields;
import java.util.Collections;
import org.junit.jupiter.api.Test;

final class MappingRuleFilterMapperTest {

  @Test
  void shouldMapFilterFields() {
    // given
    final MappingRuleFilter filter =
        new MappingRuleFilter().claimName("claimName").claimValue("claimValue");

    // when
    final MappingRuleFilterFields result = MappingRuleFilterMapper.from(filter);

    // then
    assertThat(result.getClaimName()).isEqualTo("claimName");
    assertThat(result.getClaimValue()).isEqualTo("claimValue");
  }

  @Test
  void shouldRejectNestedOrFilters() {
    // given
    final MappingRuleFilterFields branch = new MappingRuleFilterFields().claimName("claimName");
    final MappingRuleFilter filter = new MappingRuleFilter();
    filter.set$Or(Collections.singletonList(branch));

    // when/then
    assertThatThrownBy(() -> MappingRuleFilterMapper.from(filter))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
