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

import io.camunda.zeebe.client.api.response.Process;
import io.camunda.zeebe.client.api.search.response.ProcessDefinition;
import io.camunda.zeebe.client.protocol.rest.ProcessDefinitionItem;

public class ProcessDefinitionImpl implements ProcessDefinition, Process {

  private final Long processDefinitionKey;
  private final String name;
  private final String resourceName;
  private final Integer version;
  private final String versionTag;
  private final String processDefinitionId;
  private final String tenantId;

  public ProcessDefinitionImpl(final ProcessDefinitionItem item) {
    processDefinitionKey = item.getProcessDefinitionKey();
    name = item.getName();
    resourceName = item.getResourceName();
    version = item.getVersion();
    versionTag = item.getVersionTag();
    processDefinitionId = item.getProcessDefinitionId();
    tenantId = item.getTenantId();
  }

  @Override
  public long getProcessDefinitionKey() {
    return processDefinitionKey;
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public String getResourceName() {
    return resourceName;
  }

  @Override
  public int getVersion() {
    return version;
  }

  @Override
  public String getVersionTag() {
    return versionTag;
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
  public String getBpmnProcessId() {
    return processDefinitionId;
  }
}
