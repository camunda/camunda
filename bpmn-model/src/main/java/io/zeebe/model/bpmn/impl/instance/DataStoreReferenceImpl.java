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
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.BPMN_ATTRIBUTE_DATA_STORE_REF;
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.BPMN_ATTRIBUTE_ITEM_SUBJECT_REF;
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.BPMN_ELEMENT_DATA_STORE_REFERENCE;

import io.zeebe.model.bpmn.instance.DataState;
import io.zeebe.model.bpmn.instance.DataStore;
import io.zeebe.model.bpmn.instance.DataStoreReference;
import io.zeebe.model.bpmn.instance.FlowElement;
import io.zeebe.model.bpmn.instance.ItemDefinition;
import org.camunda.bpm.model.xml.ModelBuilder;
import org.camunda.bpm.model.xml.impl.instance.ModelTypeInstanceContext;
import org.camunda.bpm.model.xml.type.ModelElementTypeBuilder;
import org.camunda.bpm.model.xml.type.child.ChildElement;
import org.camunda.bpm.model.xml.type.child.SequenceBuilder;
import org.camunda.bpm.model.xml.type.reference.AttributeReference;

/** @author Giulio Piccinin */
public class DataStoreReferenceImpl extends FlowElementImpl implements DataStoreReference {

  protected static AttributeReference<ItemDefinition> itemSubjectRefAttribute;
  protected static AttributeReference<DataStore> dataStoreRefAttribute;
  protected static ChildElement<DataState> dataStateChild;

  public DataStoreReferenceImpl(final ModelTypeInstanceContext instanceContext) {
    super(instanceContext);
  }

  public static void registerType(final ModelBuilder modelBuilder) {
    final ModelElementTypeBuilder typeBuilder =
        modelBuilder
            .defineType(DataStoreReference.class, BPMN_ELEMENT_DATA_STORE_REFERENCE)
            .namespaceUri(BPMN20_NS)
            .extendsType(FlowElement.class)
            .instanceProvider(
                new ModelElementTypeBuilder.ModelTypeInstanceProvider<DataStoreReference>() {
                  @Override
                  public DataStoreReference newInstance(
                      final ModelTypeInstanceContext instanceContext) {
                    return new DataStoreReferenceImpl(instanceContext);
                  }
                });

    itemSubjectRefAttribute =
        typeBuilder
            .stringAttribute(BPMN_ATTRIBUTE_ITEM_SUBJECT_REF)
            .qNameAttributeReference(ItemDefinition.class)
            .build();

    dataStoreRefAttribute =
        typeBuilder
            .stringAttribute(BPMN_ATTRIBUTE_DATA_STORE_REF)
            .idAttributeReference(DataStore.class)
            .build();

    final SequenceBuilder sequenceBuilder = typeBuilder.sequence();

    dataStateChild = sequenceBuilder.element(DataState.class).build();

    typeBuilder.build();
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

  @Override
  public DataStore getDataStore() {
    return dataStoreRefAttribute.getReferenceTargetElement(this);
  }

  @Override
  public void setDataStore(final DataStore dataStore) {
    dataStoreRefAttribute.setReferenceTargetElement(this, dataStore);
  }
}
