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

import static io.zeebe.model.bpmn.impl.BpmnModelConstants.BPMN_ATTRIBUTE_IS_COLLECTION;
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.BPMN_ATTRIBUTE_ITEM_KIND;
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.BPMN_ATTRIBUTE_STRUCTURE_REF;

import io.zeebe.model.bpmn.ItemKind;
import io.zeebe.model.bpmn.impl.BpmnModelConstants;
import io.zeebe.model.bpmn.instance.ItemDefinition;
import io.zeebe.model.bpmn.instance.RootElement;
import org.camunda.bpm.model.xml.ModelBuilder;
import org.camunda.bpm.model.xml.impl.instance.ModelTypeInstanceContext;
import org.camunda.bpm.model.xml.type.ModelElementTypeBuilder;
import org.camunda.bpm.model.xml.type.ModelElementTypeBuilder.ModelTypeInstanceProvider;
import org.camunda.bpm.model.xml.type.attribute.Attribute;

/** @author Sebastian Menski */
public class ItemDefinitionImpl extends RootElementImpl implements ItemDefinition {

  protected static Attribute<String> structureRefAttribute;
  protected static Attribute<Boolean> isCollectionAttribute;
  protected static Attribute<ItemKind> itemKindAttribute;

  public ItemDefinitionImpl(final ModelTypeInstanceContext context) {
    super(context);
  }

  public static void registerType(final ModelBuilder modelBuilder) {
    final ModelElementTypeBuilder typeBuilder =
        modelBuilder
            .defineType(ItemDefinition.class, BpmnModelConstants.BPMN_ELEMENT_ITEM_DEFINITION)
            .namespaceUri(BpmnModelConstants.BPMN20_NS)
            .extendsType(RootElement.class)
            .instanceProvider(
                new ModelTypeInstanceProvider<ItemDefinition>() {
                  @Override
                  public ItemDefinition newInstance(
                      final ModelTypeInstanceContext instanceContext) {
                    return new ItemDefinitionImpl(instanceContext);
                  }
                });

    structureRefAttribute = typeBuilder.stringAttribute(BPMN_ATTRIBUTE_STRUCTURE_REF).build();

    isCollectionAttribute =
        typeBuilder.booleanAttribute(BPMN_ATTRIBUTE_IS_COLLECTION).defaultValue(false).build();

    itemKindAttribute =
        typeBuilder
            .enumAttribute(BPMN_ATTRIBUTE_ITEM_KIND, ItemKind.class)
            .defaultValue(ItemKind.Information)
            .build();

    typeBuilder.build();
  }

  @Override
  public String getStructureRef() {
    return structureRefAttribute.getValue(this);
  }

  @Override
  public void setStructureRef(final String structureRef) {
    structureRefAttribute.setValue(this, structureRef);
  }

  @Override
  public boolean isCollection() {
    return isCollectionAttribute.getValue(this);
  }

  @Override
  public void setCollection(final boolean isCollection) {
    isCollectionAttribute.setValue(this, isCollection);
  }

  @Override
  public ItemKind getItemKind() {
    return itemKindAttribute.getValue(this);
  }

  @Override
  public void setItemKind(final ItemKind itemKind) {
    itemKindAttribute.setValue(this, itemKind);
  }
}
