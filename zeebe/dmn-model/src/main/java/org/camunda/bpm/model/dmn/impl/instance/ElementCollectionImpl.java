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
package org.camunda.bpm.model.dmn.impl.instance;

import static org.camunda.bpm.model.dmn.impl.DmnModelConstants.LATEST_DMN_NS;
import static org.camunda.bpm.model.dmn.impl.DmnModelConstants.DMN_ELEMENT_ELEMENT_COLLECTION;

import java.util.Collection;

import org.camunda.bpm.model.dmn.instance.DrgElement;
import org.camunda.bpm.model.dmn.instance.DrgElementReference;
import org.camunda.bpm.model.dmn.instance.ElementCollection;
import org.camunda.bpm.model.dmn.instance.NamedElement;
import org.camunda.bpm.model.xml.ModelBuilder;
import org.camunda.bpm.model.xml.impl.instance.ModelTypeInstanceContext;
import org.camunda.bpm.model.xml.type.ModelElementTypeBuilder;
import org.camunda.bpm.model.xml.type.ModelElementTypeBuilder.ModelTypeInstanceProvider;
import org.camunda.bpm.model.xml.type.child.SequenceBuilder;
import org.camunda.bpm.model.xml.type.reference.ElementReferenceCollection;

public class ElementCollectionImpl extends NamedElementImpl implements ElementCollection {

  protected static ElementReferenceCollection<DrgElement, DrgElementReference> drgElementRefCollection;

  public ElementCollectionImpl(ModelTypeInstanceContext instanceContext) {
    super(instanceContext);
  }

  public Collection<DrgElement> getDrgElements() {
    return drgElementRefCollection.getReferenceTargetElements(this);
  }

  public static void registerType(ModelBuilder modelBuilder) {
    ModelElementTypeBuilder typeBuilder = modelBuilder.defineType(ElementCollection.class, DMN_ELEMENT_ELEMENT_COLLECTION)
      .namespaceUri(LATEST_DMN_NS)
      .extendsType(NamedElement.class)
      .instanceProvider(new ModelTypeInstanceProvider<ElementCollection>() {
        public ElementCollection newInstance(ModelTypeInstanceContext instanceContext) {
          return new ElementCollectionImpl(instanceContext);
        }
      });

    SequenceBuilder sequenceBuilder = typeBuilder.sequence();

    drgElementRefCollection = sequenceBuilder.elementCollection(DrgElementReference.class)
      .uriElementReferenceCollection(DrgElement.class)
      .build();

    typeBuilder.build();
  }

}
