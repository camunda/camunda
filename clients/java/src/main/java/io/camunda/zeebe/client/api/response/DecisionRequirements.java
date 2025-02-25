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

/**
 * @deprecated since 8.8 for removal in 8.9, replaced by {@link
 *     io.camunda.client.api.response.DecisionRequirements}
 */
@Deprecated
public interface DecisionRequirements {

  /**
   * @return the dmn decision requirements ID, as parsed during deployment; together with the
   *     versions forms a unique identifier for a specific decision
   */
  String getDmnDecisionRequirementsId();

  /**
   * @return the dmn name of the decision requirements, as parsed during deployment
   */
  String getDmnDecisionRequirementsName();

  /**
   * @return the assigned decision requirements version
   */
  int getVersion();

  /**
   * @return the assigned decision requirements key, which acts as a unique identifier for this
   *     decision requirements
   */
  long getDecisionRequirementsKey();

  /**
   * @return the resource name (i.e. filename) from which this decision requirements was parsed
   */
  String getResourceName();

  /**
   * @return the tenant identifier that owns this decision requirements
   */
  String getTenantId();
}
