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

package io.camunda.zeebe.model.bpmn.impl.instance;

import static io.camunda.zeebe.model.bpmn.impl.BpmnModelConstants.BPMN20_NS;
import static io.camunda.zeebe.model.bpmn.impl.BpmnModelConstants.BPMN_ELEMENT_CONDITION;
import static io.camunda.zeebe.model.bpmn.impl.BpmnModelConstants.XSI_ATTRIBUTE_TYPE;
import static io.camunda.zeebe.model.bpmn.impl.BpmnModelConstants.XSI_NS;

import io.camunda.zeebe.model.bpmn.instance.Condition;
import io.camunda.zeebe.model.bpmn.instance.FormalExpression;
import org.camunda.bpm.model.xml.ModelBuilder;
import org.camunda.bpm.model.xml.impl.instance.ModelTypeInstanceContext;
import org.camunda.bpm.model.xml.type.ModelElementTypeBuilder;
import org.camunda.bpm.model.xml.type.ModelElementTypeBuilder.ModelTypeInstanceProvider;
import org.camunda.bpm.model.xml.type.attribute.Attribute;

/**
 * The BPMN condition element of the BPMN tConditionalEventDefinition type
 *
 * @author Sebastian Menski
 */
public class ConditionImpl extends FormalExpressionImpl implements Condition {

  protected static Attribute<String> typeAttribute;

  public ConditionImpl(final ModelTypeInstanceContext instanceContext) {
    super(instanceContext);
  }

  public static void registerType(final ModelBuilder modelBuilder) {
    final ModelElementTypeBuilder typeBuilder =
        modelBuilder
            .defineType(Condition.class, BPMN_ELEMENT_CONDITION)
            .namespaceUri(BPMN20_NS)
            .extendsType(FormalExpression.class)
            .instanceProvider((ModelTypeInstanceProvider<Condition>) ConditionImpl::new);

    typeAttribute =
        typeBuilder
            .stringAttribute(XSI_ATTRIBUTE_TYPE)
            .namespace(XSI_NS)
            .defaultValue("tFormalExpression")
            .build();

    typeBuilder.build();
  }

  @Override
  public String getType() {
    return typeAttribute.getValue(this);
  }

  @Override
  public void setType(final String type) {
    typeAttribute.setValue(this, type);
  }
}
