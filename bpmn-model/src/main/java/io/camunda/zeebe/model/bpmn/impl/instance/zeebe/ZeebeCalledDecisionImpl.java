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
package io.camunda.zeebe.model.bpmn.impl.instance.zeebe;

import io.camunda.zeebe.model.bpmn.impl.BpmnModelConstants;
import io.camunda.zeebe.model.bpmn.impl.ZeebeConstants;
import io.camunda.zeebe.model.bpmn.impl.instance.BpmnModelElementInstanceImpl;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeCalledDecision;
import org.camunda.bpm.model.xml.ModelBuilder;
import org.camunda.bpm.model.xml.impl.instance.ModelTypeInstanceContext;
import org.camunda.bpm.model.xml.type.ModelElementTypeBuilder;
import org.camunda.bpm.model.xml.type.attribute.Attribute;

public class ZeebeCalledDecisionImpl extends BpmnModelElementInstanceImpl
    implements ZeebeCalledDecision {

  private static Attribute<String> decisionIdAttribute;
  private static Attribute<String> resultVariableAttribute;

  public ZeebeCalledDecisionImpl(final ModelTypeInstanceContext instanceContext) {
    super(instanceContext);
  }

  public static void registerType(final ModelBuilder modelBuilder) {
    final ModelElementTypeBuilder typeBuilder =
        modelBuilder
            .defineType(ZeebeCalledDecision.class, ZeebeConstants.ELEMENT_CALLED_DECISION)
            .namespaceUri(BpmnModelConstants.ZEEBE_NS)
            .instanceProvider(ZeebeCalledDecisionImpl::new);

    decisionIdAttribute =
        typeBuilder
            .stringAttribute(ZeebeConstants.ATTRIBUTE_DECISION_ID)
            .namespace(BpmnModelConstants.ZEEBE_NS)
            .required()
            .build();

    resultVariableAttribute =
        typeBuilder
            .stringAttribute(ZeebeConstants.ATTRIBUTE_RESULT_VARIABLE)
            .namespace(BpmnModelConstants.ZEEBE_NS)
            .required()
            .build();

    typeBuilder.build();
  }

  @Override
  public String getDecisionId() {
    return decisionIdAttribute.getValue(this);
  }

  @Override
  public void setDecisionId(final String decisionId) {
    decisionIdAttribute.setValue(this, decisionId);
  }

  @Override
  public String getResultVariable() {
    return resultVariableAttribute.getValue(this);
  }

  @Override
  public void setResultVariable(final String resultVariable) {
    resultVariableAttribute.setValue(this, resultVariable);
  }
}
