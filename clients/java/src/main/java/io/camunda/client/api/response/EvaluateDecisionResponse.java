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
package io.camunda.client.api.response;

import java.util.List;

public interface EvaluateDecisionResponse {

  /**
   * @return the decision ID, as parsed during deployment; together with the versions forms a unique
   *     identifier for a specific decision
   */
  String getDecisionId();

  /**
   * @return the assigned decision version
   */
  int getDecisionVersion();

  /**
   * @return the assigned decision key, which acts as a unique identifier for this decision
   */
  long getDecisionKey();

  /**
   * @return the name of the decision, as parsed during deployment
   */
  String getDecisionName();

  /**
   * @return the ID of the decision requirements graph that this decision is part of, as parsed
   *     during deployment
   */
  String getDecisionRequirementsId();

  /**
   * @return the assigned key of the decision requirements graph that this decision is part of
   */
  long getDecisionRequirementsKey();

  /**
   * @return the output of the evaluated decision
   */
  String getDecisionOutput();

  /**
   * @return a list of decisions that were evaluated within the requested decision evaluation
   */
  List<EvaluatedDecision> getEvaluatedDecisions();

  /**
   * @return a string indicating the ID of the decision which failed during evaluation
   */
  String getFailedDecisionId();

  /**
   * @return a message describing why the decision which was evaluated failed
   */
  String getFailureMessage();

  /**
   * @return the tenant identifier that owns this decision evaluation result
   */
  String getTenantId();

  /** Deprecated, please use {@link #getDecisionEvaluationKey()} instead. */
  @Deprecated
  long getDecisionInstanceKey();

  /**
   * @return the unique key identifying this decision evaluation
   */
  long getDecisionEvaluationKey();
}
