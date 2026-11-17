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

import io.camunda.client.protocol.rest.RoleFilter;
import io.camunda.client.protocol.rest.RoleFilterFields;

/** Utility class for mapping {@link RoleFilter} to {@link RoleFilterFields}. */
public final class RoleFilterMapper {

  private RoleFilterMapper() {
    // Prevent instantiation
  }

  public static RoleFilterFields from(final RoleFilter filter) {
    if (filter == null) {
      return null;
    }
    if (filter.get$Or() != null && !filter.get$Or().isEmpty()) {
      throw new IllegalArgumentException("Nesting $or filters is not supported");
    }

    final RoleFilterFields target = new RoleFilterFields();
    target.setRoleId(filter.getRoleId());
    target.setName(filter.getName());
    return target;
  }
}
