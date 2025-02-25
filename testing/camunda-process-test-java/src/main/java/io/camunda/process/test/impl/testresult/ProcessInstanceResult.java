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
package io.camunda.process.test.impl.testresult;

import io.camunda.client.api.search.response.FlowNodeInstance;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ProcessInstanceResult {

  private long processInstanceKey;
  private String processId;

  private Map<String, String> variables = new HashMap<>();

  private List<OpenIncident> openIncidents = new ArrayList<>();

  private List<FlowNodeInstance> activeFlowNodeInstances = new ArrayList<>();

  public long getProcessInstanceKey() {
    return processInstanceKey;
  }

  public void setProcessInstanceKey(final long processInstanceKey) {
    this.processInstanceKey = processInstanceKey;
  }

  public String getProcessId() {
    return processId;
  }

  public void setProcessId(final String processId) {
    this.processId = processId;
  }

  public Map<String, String> getVariables() {
    return variables;
  }

  public void setVariables(final Map<String, String> variables) {
    this.variables = variables;
  }

  public List<OpenIncident> getOpenIncidents() {
    return openIncidents;
  }

  public void setOpenIncidents(final List<OpenIncident> openIncidents) {
    this.openIncidents = openIncidents;
  }

  public List<FlowNodeInstance> getActiveFlowNodeInstances() {
    return activeFlowNodeInstances;
  }

  public void setActiveFlowNodeInstances(final List<FlowNodeInstance> activeFlowNodeInstances) {
    this.activeFlowNodeInstances = activeFlowNodeInstances;
  }
}
