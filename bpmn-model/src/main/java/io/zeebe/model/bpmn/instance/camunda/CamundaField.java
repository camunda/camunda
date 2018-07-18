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
 * The BPMN field camunda extension element
 *
 * @author Sebastian Menski
 */
public interface CamundaField extends BpmnModelElementInstance {

  String getCamundaName();

  void setCamundaName(String camundaName);

  String getCamundaExpression();

  void setCamundaExpression(String camundaExpression);

  String getCamundaStringValue();

  void setCamundaStringValue(String camundaStringValue);

  CamundaString getCamundaString();

  void setCamundaString(CamundaString camundaString);

  CamundaExpression getCamundaExpressionChild();

  void setCamundaExpressionChild(CamundaExpression camundaExpression);
}
