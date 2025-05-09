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
package io.camunda.client.impl.search.response;

import io.camunda.client.api.search.response.Role;

public class RoleImpl implements Role {

  private final long roleKey;
  private final String roleId;
  private final String name;
  private final String description;

  public RoleImpl(
      final long roleKey, final String roleId, final String name, final String description) {
    this.roleKey = roleKey;
    this.roleId = roleId;
    this.name = name;
    this.description = description;
  }

  @Override
  public Long getRoleKey() {
    return roleKey;
  }

  @Override
  public String getRoleId() {
    return roleId;
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public String getDescription() {
    return description;
  }
}
