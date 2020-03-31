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
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.BPMN_ATTRIBUTE_VALUE;
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.BPMN_ELEMENT_CATEGORY_VALUE;

import io.zeebe.model.bpmn.instance.BaseElement;
import io.zeebe.model.bpmn.instance.CategoryValue;
import org.camunda.bpm.model.xml.ModelBuilder;
import org.camunda.bpm.model.xml.impl.instance.ModelTypeInstanceContext;
import org.camunda.bpm.model.xml.type.ModelElementTypeBuilder;
import org.camunda.bpm.model.xml.type.ModelElementTypeBuilder.ModelTypeInstanceProvider;
import org.camunda.bpm.model.xml.type.attribute.Attribute;

/**
 * The BPMN categoryValue element
 *
 * @author Sebastian Menski
 */
public class CategoryValueImpl extends BaseElementImpl implements CategoryValue {

  protected static Attribute<String> valueAttribute;

  public CategoryValueImpl(final ModelTypeInstanceContext instanceContext) {
    super(instanceContext);
  }

  public static void registerType(final ModelBuilder modelBuilder) {
    final ModelElementTypeBuilder typeBuilder =
        modelBuilder
            .defineType(CategoryValue.class, BPMN_ELEMENT_CATEGORY_VALUE)
            .namespaceUri(BPMN20_NS)
            .extendsType(BaseElement.class)
            .instanceProvider(
                new ModelTypeInstanceProvider<CategoryValue>() {
                  @Override
                  public CategoryValue newInstance(final ModelTypeInstanceContext instanceContext) {
                    return new CategoryValueImpl(instanceContext);
                  }
                });

    valueAttribute = typeBuilder.stringAttribute(BPMN_ATTRIBUTE_VALUE).build();

    typeBuilder.build();
  }

  @Override
  public String getValue() {
    return valueAttribute.getValue(this);
  }

  @Override
  public void setValue(final String name) {
    valueAttribute.setValue(this, name);
  }
}
