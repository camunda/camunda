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
import io.camunda.zeebe.client.api.search.response.UserTask;
import io.camunda.zeebe.client.protocol.rest.DecisionRequirementItem;
import io.camunda.zeebe.client.protocol.rest.UserTaskItem;
import java.util.List;
import java.util.Map;

public class DecisionRequirementImpl implements DecisionRequirements {
  private final Long key;
  private final String id;
  private final String resourceName;
  private final String tenantId;
  private final String decisionRequirementsId;
  private final String name;
  private final int version;

  public DecisionRequirementImpl(final DecisionRequirementItem item) {
    key = item.getKey();
    id = item.getId();
    resourceName = item.getResourceName();
    tenantId = item.getTenantId();
    decisionRequirementsId = item.getDecisionRequirementsId();
    name = item.getName();
    version = item.getVersion();
  }

  @Override
  public String getDecisionRequirementsId() {
    return decisionRequirementsId;
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public int getVersion() {
    return version;
  }

  @Override
  public long getKey() {
    return key;
  }

  @Override
  public String getId() {
    return id;
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
