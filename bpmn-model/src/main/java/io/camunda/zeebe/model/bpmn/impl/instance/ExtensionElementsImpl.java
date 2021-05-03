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
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.BPMN_ELEMENT_EXTENSION_ELEMENTS;

import io.zeebe.model.bpmn.Query;
import io.zeebe.model.bpmn.impl.QueryImpl;
import io.zeebe.model.bpmn.instance.ExtensionElements;
import java.util.Collection;
import org.camunda.bpm.model.xml.ModelBuilder;
import org.camunda.bpm.model.xml.impl.instance.ModelTypeInstanceContext;
import org.camunda.bpm.model.xml.impl.util.ModelUtil;
import org.camunda.bpm.model.xml.instance.ModelElementInstance;
import org.camunda.bpm.model.xml.type.ModelElementType;
import org.camunda.bpm.model.xml.type.ModelElementTypeBuilder;

/**
 * The BPMN extensionElements element
 *
 * @author Daniel Meyer
 * @author Sebastian Menski
 */
public class ExtensionElementsImpl extends BpmnModelElementInstanceImpl
    implements ExtensionElements {

  public ExtensionElementsImpl(final ModelTypeInstanceContext context) {
    super(context);
  }

  public static void registerType(final ModelBuilder modelBuilder) {

    final ModelElementTypeBuilder typeBuilder =
        modelBuilder
            .defineType(ExtensionElements.class, BPMN_ELEMENT_EXTENSION_ELEMENTS)
            .namespaceUri(BPMN20_NS)
            .instanceProvider(
                new ModelElementTypeBuilder.ModelTypeInstanceProvider<ExtensionElements>() {
                  @Override
                  public ExtensionElements newInstance(
                      final ModelTypeInstanceContext instanceContext) {
                    return new ExtensionElementsImpl(instanceContext);
                  }
                });

    typeBuilder.build();
  }

  @Override
  public Collection<ModelElementInstance> getElements() {
    return ModelUtil.getModelElementCollection(getDomElement().getChildElements(), modelInstance);
  }

  @Override
  public Query<ModelElementInstance> getElementsQuery() {
    return new QueryImpl<ModelElementInstance>(getElements());
  }

  @Override
  public ModelElementInstance addExtensionElement(
      final String namespaceUri, final String localName) {
    final ModelElementType extensionElementType =
        modelInstance.registerGenericType(namespaceUri, localName);
    final ModelElementInstance extensionElement = extensionElementType.newInstance(modelInstance);
    addChildElement(extensionElement);
    return extensionElement;
  }

  @Override
  public <T extends ModelElementInstance> T addExtensionElement(
      final Class<T> extensionElementClass) {
    final ModelElementInstance extensionElement = modelInstance.newInstance(extensionElementClass);
    addChildElement(extensionElement);
    return extensionElementClass.cast(extensionElement);
  }

  @Override
  public void addChildElement(final ModelElementInstance extensionElement) {
    getDomElement().appendChild(extensionElement.getDomElement());
  }
}
