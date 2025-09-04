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

import io.camunda.zeebe.client.api.search.response.ProcessInstanceReference;
import io.camunda.zeebe.client.protocol.rest.ProcessInstanceReferenceItem;

/**
 * @deprecated since 8.8 for removal in 8.10, replaced by the new Camunda Client Java.
 * Please see the migration guide: https://docs.camunda.io/docs/8.8/apis-tools/migration-manuals/migrate-to-camunda-java-client/
 */
@Deprecated
public class ProcessInstanceReferenceImpl implements ProcessInstanceReference {

  private final String instanceId;
  private final String processDefinitionId;
  private final String processDefinitionName;

  public ProcessInstanceReferenceImpl(final ProcessInstanceReferenceItem item) {
    instanceId = item.getInstanceId();
    processDefinitionId = item.getProcessDefinitionId();
    processDefinitionName = item.getProcessDefinitionName();
  }

  @Override
  public String getInstanceId() {
    return instanceId;
  }

  @Override
  public String getProcessDefinitionId() {
    return processDefinitionId;
  }

  @Override
  public String processDefinitionName() {
    return processDefinitionName;
  }
}
