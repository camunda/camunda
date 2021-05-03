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
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.BPMN_ELEMENT_EVENT;

import io.zeebe.model.bpmn.instance.Event;
import io.zeebe.model.bpmn.instance.FlowNode;
import io.zeebe.model.bpmn.instance.Property;
import io.zeebe.model.bpmn.instance.bpmndi.BpmnShape;
import java.util.Collection;
import org.camunda.bpm.model.xml.ModelBuilder;
import org.camunda.bpm.model.xml.impl.instance.ModelTypeInstanceContext;
import org.camunda.bpm.model.xml.type.ModelElementTypeBuilder;
import org.camunda.bpm.model.xml.type.child.ChildElementCollection;
import org.camunda.bpm.model.xml.type.child.SequenceBuilder;

/**
 * The BPMN event element
 *
 * @author Sebastian Menski
 */
public abstract class EventImpl extends FlowNodeImpl implements Event {

  protected static ChildElementCollection<Property> propertyCollection;

  public EventImpl(final ModelTypeInstanceContext context) {
    super(context);
  }

  public static void registerType(final ModelBuilder modelBuilder) {
    final ModelElementTypeBuilder typeBuilder =
        modelBuilder
            .defineType(Event.class, BPMN_ELEMENT_EVENT)
            .namespaceUri(BPMN20_NS)
            .extendsType(FlowNode.class)
            .abstractType();

    final SequenceBuilder sequence = typeBuilder.sequence();

    propertyCollection = sequence.elementCollection(Property.class).build();

    typeBuilder.build();
  }

  @Override
  public Collection<Property> getProperties() {
    return propertyCollection.get(this);
  }

  @Override
  public BpmnShape getDiagramElement() {
    return (BpmnShape) super.getDiagramElement();
  }
}
