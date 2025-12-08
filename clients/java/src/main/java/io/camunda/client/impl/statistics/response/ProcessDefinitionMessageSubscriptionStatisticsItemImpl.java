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
package io.camunda.client.impl.statistics.response;

import io.camunda.client.api.statistics.response.ProcessDefinitionMessageSubscriptionStatisticsItem;

public class ProcessDefinitionMessageSubscriptionStatisticsItemImpl
    implements ProcessDefinitionMessageSubscriptionStatisticsItem {

  protected final String processDefinitionId;
  protected final String processDefinitionKey;
  protected final String tenantId;
  protected final long processInstancesWithActiveSubscriptions;
  protected final long activeSubscriptions;

  public ProcessDefinitionMessageSubscriptionStatisticsItemImpl(
      final String processDefinitionId,
      final String processDefinitionKey,
      final String tenantId,
      final long processInstancesWithActiveSubscriptions,
      final long activeSubscriptions) {
    this.processDefinitionId = processDefinitionId;
    this.processDefinitionKey = processDefinitionKey;
    this.tenantId = tenantId;
    this.processInstancesWithActiveSubscriptions = processInstancesWithActiveSubscriptions;
    this.activeSubscriptions = activeSubscriptions;
  }

  @Override
  public String getProcessDefinitionId() {
    return processDefinitionId;
  }

  @Override
  public String getTenantId() {
    return tenantId;
  }

  @Override
  public String getProcessDefinitionKey() {
    return processDefinitionKey;
  }

  @Override
  public Long getProcessInstancesWithActiveSubscriptions() {
    return processInstancesWithActiveSubscriptions;
  }

  @Override
  public Long getActiveSubscriptions() {
    return activeSubscriptions;
  }
}
