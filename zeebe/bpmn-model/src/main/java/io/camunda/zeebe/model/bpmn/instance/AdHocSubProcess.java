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
package io.camunda.zeebe.model.bpmn.instance;

public interface AdHocSubProcess extends SubProcess {

  /**
   * @return the completion condition to determine if the ad-hoc subprocess is completed.
   */
  CompletionCondition getCompletionCondition();

  /**
   * Sets the completion condition to determine if the ad-hoc subprocess is completed.
   *
   * @param completionCondition the completion condition to evaluate
   */
  void setCompletionCondition(CompletionCondition completionCondition);

  /**
   * @return whether the ad-hoc subprocess should cancel remaining instances when completion
   *     condition evaluates to true.
   */
  boolean isCancelRemainingInstancesEnabled();

  /**
   * Defines if the ad-hoc subprocess should cancel remaining instances when completion condition
   * evaluates to true.
   *
   * @param cancelRemainingInstances whether to cancel remaining instances on completion
   */
  void setCancelRemainingInstancesEnabled(boolean cancelRemainingInstances);
}
