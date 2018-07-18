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

package io.zeebe.model.bpmn.instance.camunda;

import io.zeebe.model.bpmn.instance.BpmnModelElementInstance;

/**
 * The BPMN in camunda extension element
 *
 * @author Sebastian Menski
 */
public interface CamundaIn extends BpmnModelElementInstance {

  String getCamundaSource();

  void setCamundaSource(String camundaSource);

  String getCamundaSourceExpression();

  void setCamundaSourceExpression(String camundaSourceExpression);

  String getCamundaVariables();

  void setCamundaVariables(String camundaVariables);

  String getCamundaTarget();

  void setCamundaTarget(String camundaTarget);

  String getCamundaBusinessKey();

  void setCamundaBusinessKey(String camundaBusinessKey);

  boolean getCamundaLocal();

  void setCamundaLocal(boolean camundaLocal);
}
