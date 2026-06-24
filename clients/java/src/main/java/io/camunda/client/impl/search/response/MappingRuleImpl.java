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

import io.camunda.client.api.search.response.MappingRule;

public class MappingRuleImpl implements MappingRule {

  private final String mappingRuleId;
  private final String claimName;
  private final String claimValue;
  private final String name;

  public MappingRuleImpl(
      final String mappingRuleId,
      final String claimName,
      final String claimValue,
      final String name) {
    this.mappingRuleId = mappingRuleId;
    this.claimName = claimName;
    this.claimValue = claimValue;
    this.name = name;
  }

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
}
