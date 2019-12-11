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

package io.zeebe.model.bpmn.impl.instance.dc;

import static io.zeebe.model.bpmn.impl.BpmnModelConstants.DC_ATTRIBUTE_IS_BOLD;
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.DC_ATTRIBUTE_IS_ITALIC;
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.DC_ATTRIBUTE_IS_STRIKE_THROUGH;
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.DC_ATTRIBUTE_IS_UNDERLINE;
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.DC_ATTRIBUTE_NAME;
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.DC_ATTRIBUTE_SIZE;
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.DC_ELEMENT_FONT;
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.DC_NS;

import io.zeebe.model.bpmn.impl.instance.BpmnModelElementInstanceImpl;
import io.zeebe.model.bpmn.instance.dc.Font;
import org.camunda.bpm.model.xml.ModelBuilder;
import org.camunda.bpm.model.xml.impl.instance.ModelTypeInstanceContext;
import org.camunda.bpm.model.xml.type.ModelElementTypeBuilder;
import org.camunda.bpm.model.xml.type.ModelElementTypeBuilder.ModelTypeInstanceProvider;
import org.camunda.bpm.model.xml.type.attribute.Attribute;

/**
 * The DC font element
 *
 * @author Sebastian Menski
 */
public class FontImpl extends BpmnModelElementInstanceImpl implements Font {

  protected static Attribute<String> nameAttribute;
  protected static Attribute<Double> sizeAttribute;
  protected static Attribute<Boolean> isBoldAttribute;
  protected static Attribute<Boolean> isItalicAttribute;
  protected static Attribute<Boolean> isUnderlineAttribute;
  protected static Attribute<Boolean> isStrikeTroughAttribute;

  public FontImpl(final ModelTypeInstanceContext instanceContext) {
    super(instanceContext);
  }

  public static void registerType(final ModelBuilder modelBuilder) {
    final ModelElementTypeBuilder typeBuilder =
        modelBuilder
            .defineType(Font.class, DC_ELEMENT_FONT)
            .namespaceUri(DC_NS)
            .instanceProvider(
                new ModelTypeInstanceProvider<Font>() {
                  @Override
                  public Font newInstance(final ModelTypeInstanceContext instanceContext) {
                    return new FontImpl(instanceContext);
                  }
                });

    nameAttribute = typeBuilder.stringAttribute(DC_ATTRIBUTE_NAME).build();

    sizeAttribute = typeBuilder.doubleAttribute(DC_ATTRIBUTE_SIZE).build();

    isBoldAttribute = typeBuilder.booleanAttribute(DC_ATTRIBUTE_IS_BOLD).build();

    isItalicAttribute = typeBuilder.booleanAttribute(DC_ATTRIBUTE_IS_ITALIC).build();

    isUnderlineAttribute = typeBuilder.booleanAttribute(DC_ATTRIBUTE_IS_UNDERLINE).build();

    isStrikeTroughAttribute = typeBuilder.booleanAttribute(DC_ATTRIBUTE_IS_STRIKE_THROUGH).build();

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
  public Double getSize() {
    return sizeAttribute.getValue(this);
  }

  @Override
  public void setSize(final Double size) {
    sizeAttribute.setValue(this, size);
  }

  @Override
  public Boolean isBold() {
    return isBoldAttribute.getValue(this);
  }

  @Override
  public void setBold(final boolean isBold) {
    isBoldAttribute.setValue(this, isBold);
  }

  @Override
  public Boolean isItalic() {
    return isItalicAttribute.getValue(this);
  }

  @Override
  public void setItalic(final boolean isItalic) {
    isItalicAttribute.setValue(this, isItalic);
  }

  @Override
  public Boolean isUnderline() {
    return isUnderlineAttribute.getValue(this);
  }

  @Override
  public void setUnderline(final boolean isUnderline) {
    isUnderlineAttribute.setValue(this, isUnderline);
  }

  @Override
  public Boolean isStrikeThrough() {
    return isStrikeTroughAttribute.getValue(this);
  }

  @Override
  public void setStrikeTrough(final boolean isStrikeTrough) {
    isStrikeTroughAttribute.setValue(this, isStrikeTrough);
  }
}
