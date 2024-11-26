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
package io.camunda.zeebe.client.impl.response;

import io.camunda.zeebe.client.api.JsonMapper;
import io.camunda.zeebe.client.api.response.CreateRoleResponse;
import io.camunda.zeebe.client.protocol.rest.RoleCreateResponse;

public class CreateRoleResponseImpl implements CreateRoleResponse {

  private final JsonMapper jsonMapper;
  private long roleKey;

  public CreateRoleResponseImpl(final JsonMapper jsonMapper) {
    this.jsonMapper = jsonMapper;
  }

  @Override
  public long getRoleKey() {
    return roleKey;
  }

  public CreateRoleResponseImpl setResponse(final RoleCreateResponse response) {
    roleKey = response.getRoleKey();
    return this;
  }
}
