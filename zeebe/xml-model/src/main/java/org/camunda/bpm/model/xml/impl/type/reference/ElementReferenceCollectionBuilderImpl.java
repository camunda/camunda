/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information regarding copyright
 * ownership. Camunda licenses this file to you under the Apache License,
 * Version 2.0; you may not use this file except in compliance with the License.
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
package org.camunda.bpm.model.xml.impl.type.reference;

import org.camunda.bpm.model.xml.Model;
import org.camunda.bpm.model.xml.ModelException;
import org.camunda.bpm.model.xml.impl.type.ModelElementTypeImpl;
import org.camunda.bpm.model.xml.impl.type.attribute.AttributeImpl;
import org.camunda.bpm.model.xml.impl.type.child.ChildElementCollectionImpl;
import org.camunda.bpm.model.xml.instance.ModelElementInstance;
import org.camunda.bpm.model.xml.type.reference.ElementReferenceCollection;
import org.camunda.bpm.model.xml.type.reference.ElementReferenceCollectionBuilder;

/**
 * @author Sebastian Menski
 */
public class ElementReferenceCollectionBuilderImpl<Target extends ModelElementInstance, Source extends ModelElementInstance> implements ElementReferenceCollectionBuilder<Target, Source> {

  private final Class<Source> childElementType;
  private final Class<Target> referenceTargetClass;
  protected ElementReferenceCollectionImpl<Target, Source> elementReferenceCollectionImpl;

  public ElementReferenceCollectionBuilderImpl(Class<Source> childElementType, Class<Target> referenceTargetClass, ChildElementCollectionImpl<Source> collection) {
    this.childElementType = childElementType;
    this.referenceTargetClass = referenceTargetClass;
    this.elementReferenceCollectionImpl = new ElementReferenceCollectionImpl<Target, Source>(collection);
  }

  public ElementReferenceCollection<Target, Source> build() {
    return elementReferenceCollectionImpl;
  }

  @SuppressWarnings("unchecked")
  public void performModelBuild(Model model) {
    ModelElementTypeImpl referenceTargetType = (ModelElementTypeImpl) model.getType(referenceTargetClass);
    ModelElementTypeImpl referenceSourceType = (ModelElementTypeImpl) model.getType(childElementType);
    elementReferenceCollectionImpl.setReferenceTargetElementType(referenceTargetType);
    elementReferenceCollectionImpl.setReferenceSourceElementType(referenceSourceType);

    // the referenced attribute may be declared on a base type of the referenced type.
    AttributeImpl<String> idAttribute = (AttributeImpl<String>) referenceTargetType.getAttribute("id");
    if (idAttribute != null) {
      idAttribute.registerIncoming(elementReferenceCollectionImpl);
      elementReferenceCollectionImpl.setReferenceTargetAttribute(idAttribute);
    } else {
      throw new ModelException("Unable to find id attribute of " + referenceTargetClass);
    }
  }
}
