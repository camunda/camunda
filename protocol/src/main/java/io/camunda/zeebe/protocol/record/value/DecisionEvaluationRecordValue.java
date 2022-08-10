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

import io.camunda.zeebe.protocol.record.ImmutableProtocol;
import io.camunda.zeebe.protocol.record.RecordValue;
import java.util.List;
import org.immutables.value.Value;

/** Represents the evaluation of a DMN decision. */
@Value.Immutable
@ImmutableProtocol(builder = ImmutableDecisionEvaluationRecordValue.Builder.class)
public interface DecisionEvaluationRecordValue extends RecordValue {

  /**
   * @return the key of the evaluated decision
   */
  long getDecisionKey();

  /**
   * @return the id of the evaluated decision in the DMN
   */
  String getDecisionId();

  /**
   * @return the name of the evaluated decision in the DMN
   */
  String getDecisionName();

  /**
   * @return the version of the evaluated decision
   */
  int getDecisionVersion();

  /**
   * @return the id of the DRG in the DMN the evaluated decision belongs to
   */
  String getDecisionRequirementsId();

  /**
   * @return the key of the deployed DRG the evaluated decision belongs to
   */
  long getDecisionRequirementsKey();

  /**
   * @return the output of the evaluated decision as JSON string
   */
  String getDecisionOutput();

  /**
   * @return the BPMN process id in which context the decision was evaluated
   */
  String getBpmnProcessId();

  /**
   * @return the key of the process in which context the decision was evaluated
   */
  long getProcessDefinitionKey();

  /**
   * @return the key of the process instance in which context the decision was evaluated
   */
  long getProcessInstanceKey();

  /**
   * @return the id of the element in the BPMN in which context the decision was evaluated
   */
  String getElementId();

  /**
   * @return the key of the element instance in which context the decision was evaluated
   */
  long getElementInstanceKey();

  /**
   * Returns the {@link EvaluatedDecisionValue details} of the evaluated decision and its required
   * decisions. The order depends on the evaluation order, starting from the required decisions.
   *
   * @return details of the evaluated decisions
   */
  List<EvaluatedDecisionValue> getEvaluatedDecisions();

  /**
   * If the evaluation of the decision failed then it returns the reason why the evaluation of the
   * {@link #getFailedDecisionId() failed decision} was not successful. The failure message is not
   * available if the decision was evaluated successfully.
   *
   * @return the failure message why the evaluation failed, or an empty string if the evaluation was
   *     successful
   */
  String getEvaluationFailureMessage();

  /**
   * If the evaluation of the decision failed then it returns the id of the decision where the
   * evaluation failed. It can be the called/root decision or any of its required decisions. The
   * reason of the failure can be retrieved as {@link #getEvaluationFailureMessage() evaluation
   * failure message}. The decision id is not available if the decision was evaluated successfully.
   *
   * @return the id of the decision in the DMN where the evaluation failed, or an empty string if
   *     the evaluation was successful
   */
  String getFailedDecisionId();

  /** Returns: the tenant ID associated with this value. */
  String getTenantId();
}
