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
import static org.camunda.bpm.model.dmn.impl.DmnModelConstants.DMN_ATTRIBUTE_IS_COLLECTION;
import static org.camunda.bpm.model.dmn.impl.DmnModelConstants.DMN_ATTRIBUTE_TYPE_LANGUAGE;
import static org.camunda.bpm.model.dmn.impl.DmnModelConstants.DMN_ELEMENT_ITEM_DEFINITION;

import java.util.Collection;

import org.camunda.bpm.model.dmn.instance.AllowedValues;
import org.camunda.bpm.model.dmn.instance.ItemComponent;
import org.camunda.bpm.model.dmn.instance.ItemDefinition;
import org.camunda.bpm.model.dmn.instance.NamedElement;
import org.camunda.bpm.model.dmn.instance.TypeRef;
import org.camunda.bpm.model.xml.ModelBuilder;
import org.camunda.bpm.model.xml.impl.instance.ModelTypeInstanceContext;
import org.camunda.bpm.model.xml.type.ModelElementTypeBuilder;
import org.camunda.bpm.model.xml.type.ModelElementTypeBuilder.ModelTypeInstanceProvider;
import org.camunda.bpm.model.xml.type.attribute.Attribute;
import org.camunda.bpm.model.xml.type.child.ChildElement;
import org.camunda.bpm.model.xml.type.child.ChildElementCollection;
import org.camunda.bpm.model.xml.type.child.SequenceBuilder;

public class ItemDefinitionImpl extends NamedElementImpl implements ItemDefinition {

  protected static Attribute<String> typeLanguageAttribute;
  protected static Attribute<Boolean> isCollectionAttribute;

  protected static ChildElement<TypeRef> typeRefChild;
  protected static ChildElement<AllowedValues> allowedValuesChild;
  protected static ChildElementCollection<ItemComponent> itemComponentCollection;

  public ItemDefinitionImpl(ModelTypeInstanceContext instanceContext) {
    super(instanceContext);
  }

  public String getTypeLanguage() {
    return typeLanguageAttribute.getValue(this);
  }

  public void setTypeLanguage(String typeLanguage) {
    typeLanguageAttribute.setValue(this, typeLanguage);
  }

  public boolean isCollection() {
    return isCollectionAttribute.getValue(this);
  }

  public void setCollection(boolean isCollection) {
    isCollectionAttribute.setValue(this, isCollection);
  }

  public TypeRef getTypeRef() {
    return typeRefChild.getChild(this);
  }

  public void setTypeRef(TypeRef typeRef) {
    typeRefChild.setChild(this, typeRef);
  }

  public AllowedValues getAllowedValues() {
    return allowedValuesChild.getChild(this);
  }

  public void setAllowedValues(AllowedValues allowedValues) {
    allowedValuesChild.setChild(this, allowedValues);
  }

  public Collection<ItemComponent> getItemComponents() {
    return itemComponentCollection.get(this);
  }

  public static void registerType(ModelBuilder modelBuilder) {
    ModelElementTypeBuilder typeBuilder = modelBuilder.defineType(ItemDefinition.class, DMN_ELEMENT_ITEM_DEFINITION)
      .namespaceUri(LATEST_DMN_NS)
      .extendsType(NamedElement.class)
      .instanceProvider(new ModelTypeInstanceProvider<ItemDefinition>() {
        public ItemDefinition newInstance(ModelTypeInstanceContext instanceContext) {
          return new ItemDefinitionImpl(instanceContext);
        }
      });

    typeLanguageAttribute = typeBuilder.stringAttribute(DMN_ATTRIBUTE_TYPE_LANGUAGE)
      .build();

    isCollectionAttribute = typeBuilder.booleanAttribute(DMN_ATTRIBUTE_IS_COLLECTION)
      .defaultValue(false)
      .build();

    SequenceBuilder sequenceBuilder = typeBuilder.sequence();

    typeRefChild = sequenceBuilder.element(TypeRef.class)
      .build();

    allowedValuesChild = sequenceBuilder.element(AllowedValues.class)
      .build();

    itemComponentCollection = sequenceBuilder.elementCollection(ItemComponent.class)
      .build();

    typeBuilder.build();
  }

}
