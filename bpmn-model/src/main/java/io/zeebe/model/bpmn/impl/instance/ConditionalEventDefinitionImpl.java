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

package io.zeebe.model.bpmn.impl.instance;

import static io.zeebe.model.bpmn.impl.BpmnModelConstants.BPMN20_NS;
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.BPMN_ELEMENT_CONDITIONAL_EVENT_DEFINITION;
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.CAMUNDA_ATTRIBUTE_VARIABLE_EVENTS;
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.CAMUNDA_ATTRIBUTE_VARIABLE_NAME;
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.CAMUNDA_NS;

import io.zeebe.model.bpmn.instance.Condition;
import io.zeebe.model.bpmn.instance.ConditionalEventDefinition;
import io.zeebe.model.bpmn.instance.EventDefinition;
import java.util.List;
import org.camunda.bpm.model.xml.ModelBuilder;
import org.camunda.bpm.model.xml.impl.instance.ModelTypeInstanceContext;
import org.camunda.bpm.model.xml.impl.util.StringUtil;
import org.camunda.bpm.model.xml.type.ModelElementTypeBuilder;
import org.camunda.bpm.model.xml.type.ModelElementTypeBuilder.ModelTypeInstanceProvider;
import org.camunda.bpm.model.xml.type.attribute.Attribute;
import org.camunda.bpm.model.xml.type.child.ChildElement;
import org.camunda.bpm.model.xml.type.child.SequenceBuilder;

/**
 * The BPMN conditionalEventDefinition element
 *
 * @author Sebastian Menski
 */
public class ConditionalEventDefinitionImpl extends EventDefinitionImpl
    implements ConditionalEventDefinition {

  protected static ChildElement<Condition> conditionChild;
  protected static Attribute<String> camundaVariableName;
  protected static Attribute<String> camundaVariableEvents;

  public static void registerType(ModelBuilder modelBuilder) {
    final ModelElementTypeBuilder typeBuilder =
        modelBuilder
            .defineType(ConditionalEventDefinition.class, BPMN_ELEMENT_CONDITIONAL_EVENT_DEFINITION)
            .namespaceUri(BPMN20_NS)
            .extendsType(EventDefinition.class)
            .instanceProvider(
                new ModelTypeInstanceProvider<ConditionalEventDefinition>() {

                  @Override
                  public ConditionalEventDefinition newInstance(
                      ModelTypeInstanceContext instanceContext) {
                    return new ConditionalEventDefinitionImpl(instanceContext);
                  }
                });

    final SequenceBuilder sequenceBuilder = typeBuilder.sequence();

    conditionChild = sequenceBuilder.element(Condition.class).required().build();

    /** camunda extensions */
    camundaVariableName =
        typeBuilder.stringAttribute(CAMUNDA_ATTRIBUTE_VARIABLE_NAME).namespace(CAMUNDA_NS).build();

    camundaVariableEvents =
        typeBuilder
            .stringAttribute(CAMUNDA_ATTRIBUTE_VARIABLE_EVENTS)
            .namespace(CAMUNDA_NS)
            .build();

    typeBuilder.build();
  }

  public ConditionalEventDefinitionImpl(ModelTypeInstanceContext context) {
    super(context);
  }

  @Override
  public Condition getCondition() {
    return conditionChild.getChild(this);
  }

  @Override
  public void setCondition(Condition condition) {
    conditionChild.setChild(this, condition);
  }

  @Override
  public String getCamundaVariableName() {
    return camundaVariableName.getValue(this);
  }

  @Override
  public void setCamundaVariableName(String variableName) {
    camundaVariableName.setValue(this, variableName);
  }

  @Override
  public String getCamundaVariableEvents() {
    return camundaVariableEvents.getValue(this);
  }

  @Override
  public void setCamundaVariableEvents(String variableEvents) {
    camundaVariableEvents.setValue(this, variableEvents);
  }

  @Override
  public List<String> getCamundaVariableEventsList() {
    final String variableEvents = camundaVariableEvents.getValue(this);
    return StringUtil.splitCommaSeparatedList(variableEvents);
  }

  @Override
  public void setCamundaVariableEventsList(List<String> variableEventsList) {
    final String variableEvents = StringUtil.joinCommaSeparatedList(variableEventsList);
    camundaVariableEvents.setValue(this, variableEvents);
  }
}
