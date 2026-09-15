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

import io.camunda.client.protocol.rest.GroupFilter;
import io.camunda.client.protocol.rest.GroupFilterFields;
import io.camunda.client.protocol.rest.StringFilterProperty;
import java.util.Collections;
import org.junit.jupiter.api.Test;

final class GroupFilterMapperTest {

  @Test
  void shouldMapFilterFields() {
    // given
    final GroupFilter filter =
        new GroupFilter()
            .groupId(new StringFilterProperty().$eq("groupId"))
            .name(new StringFilterProperty().$eq("name"));

    // when
    final GroupFilterFields result = GroupFilterMapper.from(filter);

    // then
    assertThat(result.getGroupId().get$Eq()).isEqualTo("groupId");
    assertThat(result.getName().get$Eq()).isEqualTo("name");
  }

  @Test
  void shouldRejectNestedOrFilters() {
    // given
    final GroupFilterFields branch =
        new GroupFilterFields().groupId(new StringFilterProperty().$eq("groupId"));
    final GroupFilter filter = new GroupFilter();
    filter.set$Or(Collections.singletonList(branch));

    // when/then
    assertThatThrownBy(() -> GroupFilterMapper.from(filter))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
