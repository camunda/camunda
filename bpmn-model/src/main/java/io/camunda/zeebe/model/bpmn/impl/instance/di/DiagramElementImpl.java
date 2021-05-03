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

package io.zeebe.model.bpmn.impl.instance.di;

import static io.zeebe.model.bpmn.impl.BpmnModelConstants.DI_ATTRIBUTE_ID;
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.DI_ELEMENT_DIAGRAM_ELEMENT;
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.DI_NS;

import io.zeebe.model.bpmn.impl.instance.BpmnModelElementInstanceImpl;
import io.zeebe.model.bpmn.instance.di.DiagramElement;
import io.zeebe.model.bpmn.instance.di.Extension;
import org.camunda.bpm.model.xml.ModelBuilder;
import org.camunda.bpm.model.xml.impl.instance.ModelTypeInstanceContext;
import org.camunda.bpm.model.xml.type.ModelElementTypeBuilder;
import org.camunda.bpm.model.xml.type.attribute.Attribute;
import org.camunda.bpm.model.xml.type.child.ChildElement;
import org.camunda.bpm.model.xml.type.child.SequenceBuilder;

/**
 * The DI DiagramElement element
 *
 * @author Sebastian Menski
 */
public abstract class DiagramElementImpl extends BpmnModelElementInstanceImpl
    implements DiagramElement {

  protected static Attribute<String> idAttribute;
  protected static ChildElement<Extension> extensionChild;

  public DiagramElementImpl(final ModelTypeInstanceContext instanceContext) {
    super(instanceContext);
  }

  public static void registerType(final ModelBuilder modelBuilder) {
    final ModelElementTypeBuilder typeBuilder =
        modelBuilder
            .defineType(DiagramElement.class, DI_ELEMENT_DIAGRAM_ELEMENT)
            .namespaceUri(DI_NS)
            .abstractType();

    idAttribute = typeBuilder.stringAttribute(DI_ATTRIBUTE_ID).idAttribute().build();

    final SequenceBuilder sequenceBuilder = typeBuilder.sequence();

    extensionChild = sequenceBuilder.element(Extension.class).build();

    typeBuilder.build();
  }

  @Override
  public String getId() {
    return idAttribute.getValue(this);
  }

  @Override
  public void setId(final String id) {
    idAttribute.setValue(this, id);
  }

  @Override
  public Extension getExtension() {
    return extensionChild.getChild(this);
  }

  @Override
  public void setExtension(final Extension extension) {
    extensionChild.setChild(this, extension);
  }
}
