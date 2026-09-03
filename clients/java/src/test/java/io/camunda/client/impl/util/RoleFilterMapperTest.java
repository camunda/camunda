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

import io.camunda.client.protocol.rest.RoleFilter;
import io.camunda.client.protocol.rest.RoleFilterFields;
import io.camunda.client.protocol.rest.StringFilterProperty;
import java.util.Collections;
import org.junit.jupiter.api.Test;

final class RoleFilterMapperTest {

  @Test
  void shouldMapFilterFields() {
    // given
    final RoleFilter filter =
        new RoleFilter()
            .roleId(new StringFilterProperty().$eq("roleId"))
            .name(new StringFilterProperty().$eq("name"));

    // when
    final RoleFilterFields result = RoleFilterMapper.from(filter);

    // then
    assertThat(result.getRoleId().get$Eq()).isEqualTo("roleId");
    assertThat(result.getName().get$Eq()).isEqualTo("name");
  }

  @Test
  void shouldRejectNestedOrFilters() {
    // given
    final RoleFilterFields branch =
        new RoleFilterFields().roleId(new StringFilterProperty().$eq("roleId"));
    final RoleFilter filter = new RoleFilter();
    filter.set$Or(Collections.singletonList(branch));

    // when/then
    assertThatThrownBy(() -> RoleFilterMapper.from(filter))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
