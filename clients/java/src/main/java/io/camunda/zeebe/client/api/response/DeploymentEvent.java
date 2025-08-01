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
package io.camunda.zeebe.client.api.response;

import java.util.List;

/**
 * @deprecated since 8.8 for removal in 8.10, replaced by {@link
 *     io.camunda.client.api.response.DeploymentEvent}
 */
@Deprecated
public interface DeploymentEvent {
  /**
   * @return the unique key of the deployment
   */
  long getKey();

  /**
   * @return the processes which are deployed
   */
  List<Process> getProcesses();

  /**
   * @return the decisions which are deployed
   */
  List<Decision> getDecisions();

  /**
   * @return the decision requirements which are deployed
   */
  List<DecisionRequirements> getDecisionRequirements();

  /**
   * @return the deployed form metadata
   */
  List<Form> getForm();

  /**
   * @return the deployed resource metadata
   */
  List<Resource> getResource();

  /**
   * @return the tenant identifier that owns this deployment
   */
  String getTenantId();
}
