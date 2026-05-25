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

import io.camunda.client.protocol.rest.ElementInstanceFilter;
import io.camunda.client.protocol.rest.ElementInstanceFilterFields;

/**
 * Utility class for mapping {@link ElementInstanceFilter} to {@link ElementInstanceFilterFields}.
 */
public final class ElementInstanceFilterMapper {

  private ElementInstanceFilterMapper() {
    // Prevent instantiation
  }

  public static ElementInstanceFilterFields from(final ElementInstanceFilter filter) {
    if (filter == null) {
      return null;
    }

    final ElementInstanceFilterFields target = new ElementInstanceFilterFields();

    target.setProcessDefinitionId(filter.getProcessDefinitionId());
    target.setState(filter.getState());
    target.setType(
        filter.getType() == null
            ? null
            : ElementInstanceFilterFields.TypeEnum.fromValue(filter.getType().getValue()));
    target.setElementId(filter.getElementId());
    target.setElementName(filter.getElementName());
    target.setHasIncident(filter.getHasIncident());
    target.setTenantId(filter.getTenantId());
    target.setElementInstanceKey(filter.getElementInstanceKey());
    target.setProcessInstanceKey(filter.getProcessInstanceKey());
    target.setProcessDefinitionKey(filter.getProcessDefinitionKey());
    target.setIncidentKey(filter.getIncidentKey());
    target.setStartDate(filter.getStartDate());
    target.setEndDate(filter.getEndDate());
    target.setElementInstanceScopeKey(filter.getElementInstanceScopeKey());

    return target;
  }
}
