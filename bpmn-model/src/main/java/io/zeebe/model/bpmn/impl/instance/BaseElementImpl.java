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
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.BPMN_ATTRIBUTE_ID;
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.BPMN_ELEMENT_BASE_ELEMENT;

import io.zeebe.model.bpmn.Query;
import io.zeebe.model.bpmn.instance.BaseElement;
import io.zeebe.model.bpmn.instance.BpmnModelElementInstance;
import io.zeebe.model.bpmn.instance.Documentation;
import io.zeebe.model.bpmn.instance.ExtensionElements;
import io.zeebe.model.bpmn.instance.di.DiagramElement;
import java.util.ArrayList;
import java.util.Collection;
import org.camunda.bpm.model.xml.ModelBuilder;
import org.camunda.bpm.model.xml.impl.instance.ModelTypeInstanceContext;
import org.camunda.bpm.model.xml.instance.ModelElementInstance;
import org.camunda.bpm.model.xml.type.ModelElementType;
import org.camunda.bpm.model.xml.type.ModelElementTypeBuilder;
import org.camunda.bpm.model.xml.type.attribute.Attribute;
import org.camunda.bpm.model.xml.type.child.ChildElement;
import org.camunda.bpm.model.xml.type.child.ChildElementCollection;
import org.camunda.bpm.model.xml.type.child.SequenceBuilder;
import org.camunda.bpm.model.xml.type.reference.Reference;

/**
 * The BPMN baseElement element
 *
 * @author Daniel Meyer
 * @author Sebastian Menski
 */
public abstract class BaseElementImpl extends BpmnModelElementInstanceImpl implements BaseElement {

  protected static Attribute<String> idAttribute;
  protected static ChildElementCollection<Documentation> documentationCollection;
  protected static ChildElement<ExtensionElements> extensionElementsChild;

  public BaseElementImpl(final ModelTypeInstanceContext instanceContext) {
    super(instanceContext);
  }

  public static void registerType(final ModelBuilder bpmnModelBuilder) {
    final ModelElementTypeBuilder typeBuilder =
        bpmnModelBuilder
            .defineType(BaseElement.class, BPMN_ELEMENT_BASE_ELEMENT)
            .namespaceUri(BPMN20_NS)
            .abstractType();

    idAttribute = typeBuilder.stringAttribute(BPMN_ATTRIBUTE_ID).idAttribute().build();

    final SequenceBuilder sequenceBuilder = typeBuilder.sequence();

    documentationCollection = sequenceBuilder.elementCollection(Documentation.class).build();

    extensionElementsChild = sequenceBuilder.element(ExtensionElements.class).build();

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
  public Collection<Documentation> getDocumentations() {
    return documentationCollection.get(this);
  }

  @Override
  public ExtensionElements getExtensionElements() {
    return extensionElementsChild.getChild(this);
  }

  @Override
  public void setExtensionElements(final ExtensionElements extensionElements) {
    extensionElementsChild.setChild(this, extensionElements);
  }

  @Override
  public <T extends BpmnModelElementInstance> T getSingleExtensionElement(final Class<T> type) {
    final ExtensionElements extensionElements = getExtensionElements();
    if (extensionElements != null) {
      final Query<T> query = extensionElements.getElementsQuery().filterByType(type);

      if (query.count() < 1) {
        return null;
      } else {
        return query.singleResult();
      }
    } else {
      return null;
    }
  }

  @Override
  @SuppressWarnings("rawtypes")
  public DiagramElement getDiagramElement() {
    final Collection<Reference> incomingReferences =
        getIncomingReferencesByType(DiagramElement.class);
    for (final Reference<?> reference : incomingReferences) {
      for (final ModelElementInstance sourceElement : reference.findReferenceSourceElements(this)) {
        final String referenceIdentifier = reference.getReferenceIdentifier(sourceElement);
        if (referenceIdentifier != null && referenceIdentifier.equals(getId())) {
          return (DiagramElement) sourceElement;
        }
      }
    }
    return null;
  }

  @SuppressWarnings("rawtypes")
  public Collection<Reference> getIncomingReferencesByType(
      final Class<? extends ModelElementInstance> referenceSourceTypeClass) {
    final Collection<Reference> references = new ArrayList<>();
    // we traverse all incoming references in reverse direction
    for (final Reference<?> reference : idAttribute.getIncomingReferences()) {

      final ModelElementType sourceElementType = reference.getReferenceSourceElementType();
      final Class<? extends ModelElementInstance> sourceInstanceType =
          sourceElementType.getInstanceType();

      // if the referencing element (source element) is a BPMNDI element, dig deeper
      if (referenceSourceTypeClass.isAssignableFrom(sourceInstanceType)) {
        references.add(reference);
      }
    }
    return references;
  }
}
