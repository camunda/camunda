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
package io.camunda.zeebe.client.api.search.response;

/**
 * @deprecated since 8.8 for removal in 8.9, replaced by {@link
 *     io.camunda.client.api.search.response.FlowNodeInstance}
 */
@Deprecated
public interface FlowNodeInstance {

  /** key */
  Long getFlowNodeInstanceKey();

  /** process definition key for flow node instance */
  Long getProcessDefinitionKey();

  /** process instance key for flow node instance */
  Long getProcessInstanceKey();

  /** flow node id for flow node instance */
  String getFlowNodeId();

  /** flow node name for flow node instance */
  String getFlowNodeName();

  /** start date of flow node instance */
  String getStartDate();

  /** end date of flow node instance */
  String getEndDate();

  /** whether flow node instance has an incident */
  Boolean getIncident();

  /** incident key for flow node instance */
  Long getIncidentKey();

  /** state of flow node instance */
  String getState();

  /** tenant id for flow node instance */
  String getTenantId();

  /** tree path of flow node instance */
  String getTreePath();

  /** type of flow node instance */
  String getType();
}
