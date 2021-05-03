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
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.BPMN_ATTRIBUTE_IS_INTERRUPTING;
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.BPMN_ELEMENT_START_EVENT;

import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.model.bpmn.builder.StartEventBuilder;
import io.zeebe.model.bpmn.instance.CatchEvent;
import io.zeebe.model.bpmn.instance.StartEvent;
import org.camunda.bpm.model.xml.ModelBuilder;
import org.camunda.bpm.model.xml.impl.instance.ModelTypeInstanceContext;
import org.camunda.bpm.model.xml.type.ModelElementTypeBuilder;
import org.camunda.bpm.model.xml.type.attribute.Attribute;

/**
 * The BPMN startEvent element
 *
 * @author Sebastian Menski
 */
public class StartEventImpl extends CatchEventImpl implements StartEvent {

  protected static Attribute<Boolean> isInterruptingAttribute;

  public StartEventImpl(final ModelTypeInstanceContext context) {
    super(context);
  }

  public static void registerType(final ModelBuilder modelBuilder) {

    final ModelElementTypeBuilder typeBuilder =
        modelBuilder
            .defineType(StartEvent.class, BPMN_ELEMENT_START_EVENT)
            .namespaceUri(BPMN20_NS)
            .extendsType(CatchEvent.class)
            .instanceProvider(
                new ModelElementTypeBuilder.ModelTypeInstanceProvider<StartEvent>() {
                  @Override
                  public StartEvent newInstance(final ModelTypeInstanceContext instanceContext) {
                    return new StartEventImpl(instanceContext);
                  }
                });

    isInterruptingAttribute =
        typeBuilder.booleanAttribute(BPMN_ATTRIBUTE_IS_INTERRUPTING).defaultValue(true).build();

    typeBuilder.build();
  }

  @Override
  public StartEventBuilder builder() {
    return new StartEventBuilder((BpmnModelInstance) modelInstance, this);
  }

  @Override
  public boolean isInterrupting() {
    return isInterruptingAttribute.getValue(this);
  }

  @Override
  public void setInterrupting(final boolean isInterrupting) {
    isInterruptingAttribute.setValue(this, isInterrupting);
  }
}
