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

package io.zeebe.model.bpmn.instance;

import io.zeebe.model.bpmn.builder.BusinessRuleTaskBuilder;

/**
 * The BPMN businessRuleTask element
 *
 * @author Sebastian Menski
 */
public interface BusinessRuleTask extends Task {

  @Override
  BusinessRuleTaskBuilder builder();

  String getImplementation();

  void setImplementation(String implementation);

  /** camunda extensions */
  String getCamundaClass();

  void setCamundaClass(String camundaClass);

  String getCamundaDelegateExpression();

  void setCamundaDelegateExpression(String camundaExpression);

  String getCamundaExpression();

  void setCamundaExpression(String camundaExpression);

  String getCamundaResultVariable();

  void setCamundaResultVariable(String camundaResultVariable);

  String getCamundaType();

  void setCamundaType(String camundaType);

  String getCamundaTopic();

  void setCamundaTopic(String camundaTopic);

  String getCamundaDecisionRef();

  void setCamundaDecisionRef(String camundaDecisionRef);

  String getCamundaDecisionRefBinding();

  void setCamundaDecisionRefBinding(String camundaDecisionRefBinding);

  String getCamundaDecisionRefVersion();

  void setCamundaDecisionRefVersion(String camundaDecisionRefVersion);

  String getCamundaDecisionRefVersionTag();

  void setCamundaDecisionRefVersionTag(String camundaDecisionRefVersionTag);

  String getCamundaDecisionRefTenantId();

  void setCamundaDecisionRefTenantId(String tenantId);

  String getCamundaMapDecisionResult();

  void setCamundaMapDecisionResult(String camundaMapDecisionResult);

  String getCamundaTaskPriority();

  void setCamundaTaskPriority(String taskPriority);
}
