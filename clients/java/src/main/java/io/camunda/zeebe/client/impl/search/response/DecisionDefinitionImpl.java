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
package io.camunda.zeebe.client.impl.search.response;

import io.camunda.zeebe.client.api.search.response.DecisionDefinition;
import io.camunda.zeebe.client.protocol.rest.DecisionDefinitionItem;

public final class DecisionDefinitionImpl implements DecisionDefinition {

  private final String dmnDecisionId;
  private final String dmnDecisionName;
  private final int version;
  private final long decisionKey;
  private final String dmnDecisionRequirementsId;
  private final long decisionRequirementsKey;
  private final String tenantId;

  public DecisionDefinitionImpl(final DecisionDefinitionItem item) {
    this.dmnDecisionId = item.getDmnDecisionId();
    this.dmnDecisionName = item.getDmnDecisionName();
    this.version = item.getVersion();
    this.decisionKey = item.getDecisionKey();
    this.dmnDecisionRequirementsId = item.getDmnDecisionRequirementsId();
    this.decisionRequirementsKey = item.getDecisionRequirementsKey();
    this.tenantId = item.getTenantId();
  }

  @Override
  public String getDmnDecisionId() {
    return dmnDecisionId;
  }

  @Override
  public String getDmnDecisionName() {
    return dmnDecisionName;
  }

  @Override
  public int getVersion() {
    return version;
  }

  @Override
  public long getDecisionKey() {
    return decisionKey;
  }

  @Override
  public String getDmnDecisionRequirementsId() {
    return dmnDecisionRequirementsId;
  }

  @Override
  public long getDecisionRequirementsKey() {
    return decisionRequirementsKey;
  }

  @Override
  public String getTenantId() {
    return tenantId;
  }
}
