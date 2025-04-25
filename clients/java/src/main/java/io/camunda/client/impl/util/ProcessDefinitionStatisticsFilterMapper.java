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

import io.camunda.client.protocol.rest.BaseProcessInstanceFilterFields;
import io.camunda.client.protocol.rest.ProcessDefinitionStatisticsFilter;

public final class ProcessDefinitionStatisticsFilterMapper {

  private ProcessDefinitionStatisticsFilterMapper() {
    // Prevent instantiation
  }

  public static BaseProcessInstanceFilterFields from(
      final ProcessDefinitionStatisticsFilter filter) {
    if (filter == null) {
      return null;
    }

    final BaseProcessInstanceFilterFields target = new BaseProcessInstanceFilterFields();

    target.setStartDate(filter.getStartDate());
    target.setEndDate(filter.getEndDate());
    target.setState(filter.getState());
    target.setHasIncident(filter.getHasIncident());
    target.setTenantId(filter.getTenantId());
    target.setVariables(filter.getVariables());
    target.setProcessInstanceKey(filter.getProcessInstanceKey());
    target.setParentProcessInstanceKey(filter.getParentProcessInstanceKey());
    target.setParentElementInstanceKey(filter.getParentElementInstanceKey());
    target.setBatchOperationId(filter.getBatchOperationId());
    target.setErrorMessage(filter.getErrorMessage());
    target.setHasRetriesLeft(filter.getHasRetriesLeft());
    target.setElementInstanceState(filter.getElementInstanceState());
    target.setElementId(filter.getElementId());
    target.setHasElementInstanceIncident(filter.getHasElementInstanceIncident());
    target.setIncidentErrorHashCode(filter.getIncidentErrorHashCode());

    return target;
  }
}
