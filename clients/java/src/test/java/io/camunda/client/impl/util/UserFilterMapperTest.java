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

import io.camunda.client.protocol.rest.StringFilterProperty;
import io.camunda.client.protocol.rest.UserFilter;
import io.camunda.client.protocol.rest.UserFilterFields;
import java.util.Collections;
import org.junit.jupiter.api.Test;

final class UserFilterMapperTest {

  @Test
  void shouldMapFilterFields() {
    // given
    final UserFilter filter =
        new UserFilter()
            .username(new StringFilterProperty().$eq("username"))
            .name(new StringFilterProperty().$eq("name"))
            .email(new StringFilterProperty().$eq("email"));

    // when
    final UserFilterFields result = UserFilterMapper.from(filter);

    // then
    assertThat(result.getUsername().get$Eq()).isEqualTo("username");
    assertThat(result.getName().get$Eq()).isEqualTo("name");
    assertThat(result.getEmail().get$Eq()).isEqualTo("email");
  }

  @Test
  void shouldRejectNestedOrFilters() {
    // given
    final UserFilterFields branch =
        new UserFilterFields().username(new StringFilterProperty().$eq("username"));
    final UserFilter filter = new UserFilter();
    filter.set$Or(Collections.singletonList(branch));

    // when/then
    assertThatThrownBy(() -> UserFilterMapper.from(filter))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
