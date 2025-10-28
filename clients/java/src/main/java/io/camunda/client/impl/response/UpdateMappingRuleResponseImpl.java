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

import io.camunda.client.api.response.UpdateMappingRuleResponse;
import io.camunda.client.protocol.rest.MappingRuleUpdateResult;

public class UpdateMappingRuleResponseImpl implements UpdateMappingRuleResponse {
  private String mappingRuleId;
  private String name;
  private String claimName;
  private String claimValue;

  @Override
  public String getMappingRuleId() {
    return mappingRuleId;
  }

  @Override
  public String getClaimName() {
    return claimName;
  }

  @Override
  public String getClaimValue() {
    return claimValue;
  }

  @Override
  public String getName() {
    return name;
  }

  public UpdateMappingRuleResponseImpl setResponse(final MappingRuleUpdateResult result) {
    this.mappingRuleId = result.getMappingRuleId();
    this.name = result.getName();
    this.claimName = result.getClaimName();
    this.claimValue = result.getClaimValue();
    return this;
  }
}
