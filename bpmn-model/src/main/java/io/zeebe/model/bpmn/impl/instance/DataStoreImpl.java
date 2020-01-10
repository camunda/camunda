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
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.BPMN_ATTRIBUTE_CAPACITY;
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.BPMN_ATTRIBUTE_IS_UNLIMITED;
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.BPMN_ATTRIBUTE_ITEM_SUBJECT_REF;
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.BPMN_ATTRIBUTE_NAME;
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.BPMN_ELEMENT_DATA_STORE;

import io.zeebe.model.bpmn.instance.DataState;
import io.zeebe.model.bpmn.instance.DataStore;
import io.zeebe.model.bpmn.instance.ItemDefinition;
import io.zeebe.model.bpmn.instance.RootElement;
import org.camunda.bpm.model.xml.ModelBuilder;
import org.camunda.bpm.model.xml.impl.instance.ModelTypeInstanceContext;
import org.camunda.bpm.model.xml.type.ModelElementTypeBuilder;
import org.camunda.bpm.model.xml.type.ModelElementTypeBuilder.ModelTypeInstanceProvider;
import org.camunda.bpm.model.xml.type.attribute.Attribute;
import org.camunda.bpm.model.xml.type.child.ChildElement;
import org.camunda.bpm.model.xml.type.child.SequenceBuilder;
import org.camunda.bpm.model.xml.type.reference.AttributeReference;

/**
 * The BPMN dataStore element
 *
 * @author Falko Menge
 */
public class DataStoreImpl extends RootElementImpl implements DataStore {

  protected static Attribute<String> nameAttribute;
  protected static Attribute<Integer> capacityAttribute;
  protected static Attribute<Boolean> isUnlimitedAttribute;
  protected static AttributeReference<ItemDefinition> itemSubjectRefAttribute;
  protected static ChildElement<DataState> dataStateChild;

  public DataStoreImpl(final ModelTypeInstanceContext instanceContext) {
    super(instanceContext);
  }

  public static void registerType(final ModelBuilder modelBuilder) {
    final ModelElementTypeBuilder typeBuilder =
        modelBuilder
            .defineType(DataStore.class, BPMN_ELEMENT_DATA_STORE)
            .namespaceUri(BPMN20_NS)
            .extendsType(RootElement.class)
            .instanceProvider(
                new ModelTypeInstanceProvider<DataStore>() {
                  @Override
                  public DataStore newInstance(final ModelTypeInstanceContext instanceContext) {
                    return new DataStoreImpl(instanceContext);
                  }
                });

    nameAttribute = typeBuilder.stringAttribute(BPMN_ATTRIBUTE_NAME).build();

    capacityAttribute = typeBuilder.integerAttribute(BPMN_ATTRIBUTE_CAPACITY).build();

    isUnlimitedAttribute =
        typeBuilder.booleanAttribute(BPMN_ATTRIBUTE_IS_UNLIMITED).defaultValue(true).build();

    itemSubjectRefAttribute =
        typeBuilder
            .stringAttribute(BPMN_ATTRIBUTE_ITEM_SUBJECT_REF)
            .qNameAttributeReference(ItemDefinition.class)
            .build();

    final SequenceBuilder sequenceBuilder = typeBuilder.sequence();

    dataStateChild = sequenceBuilder.element(DataState.class).build();

    typeBuilder.build();
  }

  @Override
  public String getName() {
    return nameAttribute.getValue(this);
  }

  @Override
  public void setName(final String name) {
    nameAttribute.setValue(this, name);
  }

  @Override
  public Integer getCapacity() {
    return capacityAttribute.getValue(this);
  }

  @Override
  public void setCapacity(final Integer capacity) {
    capacityAttribute.setValue(this, capacity);
  }

  @Override
  public Boolean isUnlimited() {
    return isUnlimitedAttribute.getValue(this);
  }

  @Override
  public void setUnlimited(final Boolean isUnlimited) {
    isUnlimitedAttribute.setValue(this, isUnlimited);
  }

  @Override
  public ItemDefinition getItemSubject() {
    return itemSubjectRefAttribute.getReferenceTargetElement(this);
  }

  @Override
  public void setItemSubject(final ItemDefinition itemSubject) {
    itemSubjectRefAttribute.setReferenceTargetElement(this, itemSubject);
  }

  @Override
  public DataState getDataState() {
    return dataStateChild.getChild(this);
  }

  @Override
  public void setDataState(final DataState dataState) {
    dataStateChild.setChild(this, dataState);
  }
}
