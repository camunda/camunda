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
package io.camunda.process.test.api.assertions;

import io.camunda.client.api.search.filter.IncidentFilter;
import io.camunda.client.api.search.response.Incident;

/** A collection of predefined {@link IncidentSelector}s. */
public class IncidentSelectors {

  /**
   * Select the incident by the BPMN element ID.
   *
   * @param elementId the ID of the BPMN element
   * @return the selector
   */
  public static IncidentSelector byElementId(final String elementId) {
    return new IncidentElementIdSelector(elementId);
  }

  /**
   * Select the incident by its process definition ID.
   *
   * @param processDefinitionId the process definition ID
   * @return the selector
   */
  public static IncidentSelector byProcessDefinitionId(final String processDefinitionId) {
    return new IncidentProcessDefinitionIdSelector(processDefinitionId);
  }

  /**
   * Select the incident by its process instance key.
   *
   * @param processInstanceKey the process instance key
   * @return the selector
   */
  public static IncidentSelector byProcessInstanceKey(final long processInstanceKey) {
    return new IncidentProcessInstanceKeySelector(processInstanceKey);
  }

  private static final class IncidentElementIdSelector implements IncidentSelector {

    private final String elementId;

    private IncidentElementIdSelector(final String elementId) {
      this.elementId = elementId;
    }

    @Override
    public boolean test(final Incident incident) {
      return elementId.equals(incident.getElementId());
    }

    @Override
    public String describe() {
      return "elementId: " + elementId;
    }

    @Override
    public void applyFilter(final IncidentFilter filter) {
      filter.elementId(elementId);
    }
  }

  private static final class IncidentProcessDefinitionIdSelector implements IncidentSelector {

    private final String processDefinitionId;

    private IncidentProcessDefinitionIdSelector(final String processDefinitionId) {
      this.processDefinitionId = processDefinitionId;
    }

    @Override
    public boolean test(final Incident incident) {
      return processDefinitionId.equals(incident.getProcessDefinitionId());
    }

    @Override
    public String describe() {
      return "processDefinitionId: " + processDefinitionId;
    }

    @Override
    public void applyFilter(final IncidentFilter filter) {
      filter.processDefinitionId(processDefinitionId);
    }
  }

  private static final class IncidentProcessInstanceKeySelector implements IncidentSelector {

    private final long processInstanceKey;

    private IncidentProcessInstanceKeySelector(final long processInstanceKey) {
      this.processInstanceKey = processInstanceKey;
    }

    @Override
    public boolean test(final Incident incident) {
      return incident.getProcessInstanceKey().equals(processInstanceKey);
    }

    @Override
    public String describe() {
      return "processInstanceKey: " + processInstanceKey;
    }

    @Override
    public void applyFilter(final IncidentFilter filter) {
      filter.processInstanceKey(processInstanceKey);
    }
  }
}
