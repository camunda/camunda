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
package org.camunda.bpm.model.xml.testmodel.instance;

import org.camunda.bpm.model.xml.ModelBuilder;
import org.camunda.bpm.model.xml.impl.instance.ModelElementInstanceImpl;
import org.camunda.bpm.model.xml.impl.instance.ModelTypeInstanceContext;
import org.camunda.bpm.model.xml.type.ModelElementTypeBuilder;
import org.camunda.bpm.model.xml.type.attribute.Attribute;
import org.camunda.bpm.model.xml.type.child.SequenceBuilder;
import org.camunda.bpm.model.xml.type.reference.ElementReference;
import org.camunda.bpm.model.xml.type.reference.ElementReferenceCollection;

import static org.camunda.bpm.model.xml.testmodel.TestModelConstants.*;

import java.util.Collection;

/**
 * @author Sebastian Menski
 */
public class Egg extends ModelElementInstanceImpl {

  protected static Attribute<String> idAttr;
  protected static ElementReference<Animal, Mother> motherRefChild;
  protected static ElementReferenceCollection<Animal, Guardian> guardianRefCollection;

  public static void registerType(ModelBuilder modelBuilder) {
    ModelElementTypeBuilder typeBuilder = modelBuilder.defineType(Egg.class, ELEMENT_NAME_EGG)
      .namespaceUri(MODEL_NAMESPACE)
      .instanceProvider(new ModelElementTypeBuilder.ModelTypeInstanceProvider<Egg>() {
        public Egg newInstance(ModelTypeInstanceContext instanceContext) {
          return new Egg(instanceContext);
        }
      });

    idAttr = typeBuilder.stringAttribute(ATTRIBUTE_NAME_ID)
      .idAttribute()
      .build();

    SequenceBuilder sequenceBuilder = typeBuilder.sequence();

    motherRefChild = sequenceBuilder.element(Mother.class)
      .uriElementReference(Animal.class)
      .build();

    guardianRefCollection = sequenceBuilder.elementCollection(Guardian.class)
      .uriElementReferenceCollection(Animal.class)
      .build();

    typeBuilder.build();
  }

  public Egg(ModelTypeInstanceContext instanceContext) {
    super(instanceContext);
  }

  public String getId() {
    return idAttr.getValue(this);
  }

  public void setId(String id) {
    idAttr.setValue(this, id);
  }

  public Animal getMother() {
    return motherRefChild.getReferenceTargetElement(this);
  }

  public void removeMother() {
    motherRefChild.clearReferenceTargetElement(this);
  }

  public void setMother(Animal mother) {
    motherRefChild.setReferenceTargetElement(this, mother);
  }

  public Mother getMotherRef() {
    return motherRefChild.getReferenceSource(this);
  }

  public Collection<Animal> getGuardians() {
    return guardianRefCollection.getReferenceTargetElements(this);
  }

  public Collection<Guardian> getGuardianRefs() {
    return guardianRefCollection.getReferenceSourceCollection().get(this);
  }

}
