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
package io.camunda.client.impl.response;

import io.camunda.client.api.response.CreateTenantResponse;
import io.camunda.client.protocol.rest.TenantCreateResult;

public class CreateTenantResponseImpl implements CreateTenantResponse {
  private String tenantId;
  private String name;
  private String description;

  @Override
  public String getTenantId() {
    return tenantId;
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public String getDescription() {
    return description;
  }

  public CreateTenantResponseImpl setResponse(final TenantCreateResult response) {
    tenantId = response.getTenantId();
    name = response.getName();
    description = response.getDescription();
    return this;
  }
}
