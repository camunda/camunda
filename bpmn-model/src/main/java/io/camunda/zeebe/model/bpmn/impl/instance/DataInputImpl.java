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
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.BPMN_ATTRIBUTE_IS_COLLECTION;
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.BPMN_ATTRIBUTE_NAME;
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.BPMN_ELEMENT_DATA_INPUT;

import io.zeebe.model.bpmn.instance.DataInput;
import io.zeebe.model.bpmn.instance.ItemAwareElement;
import org.camunda.bpm.model.xml.ModelBuilder;
import org.camunda.bpm.model.xml.impl.instance.ModelTypeInstanceContext;
import org.camunda.bpm.model.xml.type.ModelElementTypeBuilder;
import org.camunda.bpm.model.xml.type.ModelElementTypeBuilder.ModelTypeInstanceProvider;
import org.camunda.bpm.model.xml.type.attribute.Attribute;

/**
 * The BPMN dataInput element
 *
 * @author Sebastian Menski
 */
public class DataInputImpl extends ItemAwareElementImpl implements DataInput {

  protected static Attribute<String> nameAttribute;
  protected static Attribute<Boolean> isCollectionAttribute;

  public DataInputImpl(final ModelTypeInstanceContext instanceContext) {
    super(instanceContext);
  }

  public static void registerType(final ModelBuilder modelBuilder) {
    final ModelElementTypeBuilder typeBuilder =
        modelBuilder
            .defineType(DataInput.class, BPMN_ELEMENT_DATA_INPUT)
            .namespaceUri(BPMN20_NS)
            .extendsType(ItemAwareElement.class)
            .instanceProvider(
                new ModelTypeInstanceProvider<DataInput>() {
                  @Override
                  public DataInput newInstance(final ModelTypeInstanceContext instanceContext) {
                    return new DataInputImpl(instanceContext);
                  }
                });

    nameAttribute = typeBuilder.stringAttribute(BPMN_ATTRIBUTE_NAME).build();

    isCollectionAttribute =
        typeBuilder.booleanAttribute(BPMN_ATTRIBUTE_IS_COLLECTION).defaultValue(false).build();

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
  public boolean isCollection() {
    return isCollectionAttribute.getValue(this);
  }

  @Override
  public void setCollection(final boolean isCollection) {
    isCollectionAttribute.setValue(this, isCollection);
  }
}
