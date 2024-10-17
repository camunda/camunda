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

import io.camunda.zeebe.client.api.search.response.DecisionRequirements;
import io.camunda.zeebe.client.protocol.rest.DecisionRequirementsItem;

public class DecisionRequirementsImpl implements DecisionRequirements {
  private final Long decisionRequirementsKey;
  private final String resourceName;
  private final String tenantId;
  private final String dmnDecisionRequirementsId;
  private final String dmnDecisionRequirementsName;
  private final Integer version;

  public DecisionRequirementsImpl(final DecisionRequirementsItem item) {
    decisionRequirementsKey = item.getDecisionRequirementsKey();
    resourceName = item.getResourceName();
    tenantId = item.getTenantId();
    dmnDecisionRequirementsId = item.getDecisionRequirementsId();
    dmnDecisionRequirementsName = item.getName();
    version = item.getVersion();
  }

  @Override
  public String getDmnDecisionRequirementsId() {
    return dmnDecisionRequirementsId;
  }

  @Override
  public String getDmnDecisionRequirementsName() {
    return dmnDecisionRequirementsName;
  }

  @Override
  public int getVersion() {
    return version;
  }

  @Override
  public long getDecisionRequirementsKey() {
    return decisionRequirementsKey;
  }

  @Override
  public String getResourceName() {
    return resourceName;
  }

  @Override
  public String getTenantId() {
    return tenantId;
  }
}
