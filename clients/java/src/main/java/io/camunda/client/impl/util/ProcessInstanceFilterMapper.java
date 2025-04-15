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
package io.camunda.client.impl.util;

import io.camunda.client.protocol.rest.ProcessInstanceFilter;
import io.camunda.client.protocol.rest.ProcessInstanceFilterFields;

/**
 * Utility class for mapping {@link ProcessInstanceFilter} to {@link ProcessInstanceFilterFields}.
 */
public final class ProcessInstanceFilterMapper {

  private ProcessInstanceFilterMapper() {
    // Prevent instantiation
  }

  public static ProcessInstanceFilterFields from(final ProcessInstanceFilter filter) {
    if (filter == null) {
      return null;
    }

    final ProcessInstanceFilterFields target = new ProcessInstanceFilterFields();

    target.setProcessDefinitionId(filter.getProcessDefinitionId());
    target.setProcessDefinitionName(filter.getProcessDefinitionName());
    target.setProcessDefinitionVersion(filter.getProcessDefinitionVersion());
    target.setProcessDefinitionVersionTag(filter.getProcessDefinitionVersionTag());
    target.setProcessDefinitionKey(filter.getProcessDefinitionKey());
    target.setBatchOperationId(filter.getBatchOperationId());
    target.setErrorMessage(filter.getErrorMessage());
    target.setStartDate(filter.getStartDate());
    target.setEndDate(filter.getEndDate());
    target.setState(filter.getState());
    target.setHasIncident(filter.getHasIncident());
    target.setTenantId(filter.getTenantId());
    target.setVariables(filter.getVariables());
    target.setProcessInstanceKey(filter.getProcessInstanceKey());
    target.setParentProcessInstanceKey(filter.getParentProcessInstanceKey());
    target.setParentElementInstanceKey(filter.getParentElementInstanceKey());

    return target;
  }
}
