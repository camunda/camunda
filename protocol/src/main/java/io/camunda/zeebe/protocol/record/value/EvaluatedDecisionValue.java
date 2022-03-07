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
package io.camunda.zeebe.protocol.record.value;

import io.camunda.zeebe.protocol.record.RecordValue;
import java.util.List;

/**
 * An evaluated DMN decision. It contains details of the evaluation depending on the decision type.
 */
public interface EvaluatedDecisionValue extends RecordValue {

  /** @return the id of the evaluated decision */
  String getDecisionId();

  /** @return the name of the evaluated decision */
  String getDecisionName();

  /** @return the key of the evaluated decision */
  long getDecisionKey();

  /** @return the version of the evaluated decision */
  long getDecisionVersion();

  /** @return the type of the evaluated decision */
  String getDecisionType();

  /** @return the output of the evaluated decision as JSON string */
  String getDecisionOutput();

  /**
   * If the decision is a decision table then it returns the {@link EvaluatedInputValue evaluated
   * inputs}. The inputs are not available for other types of decision.
   *
   * @return the evaluated inputs, or an empty list if the decision is not a decision table
   */
  List<EvaluatedInputValue> getEvaluatedInputs();

  /**
   * If the decision is a decision table then it returns the matched rules. The {@link
   * MatchedRuleValue matched rules} are not available for other types of decision.
   *
   * @return the matched rules, or an empty list if the decision is not a decision table
   */
  List<MatchedRuleValue> getMatchedRules();
}
