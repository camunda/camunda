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
package io.camunda.zeebe.model.bpmn.instance.zeebe;

import io.camunda.zeebe.model.bpmn.instance.BpmnModelElementInstance;

/**
 * Marks a top-level sub-process of a case process as a stage of the case. A stage is alternative
 * when it is not part of the main (happy) path of the case, e.g. an escalation or a withdrawal.
 */
public interface ZeebeStage extends BpmnModelElementInstance {

  boolean isAlternative();

  void setAlternative(boolean alternative);
}
