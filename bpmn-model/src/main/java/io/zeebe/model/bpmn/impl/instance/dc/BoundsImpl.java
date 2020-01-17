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

import static io.zeebe.model.bpmn.impl.BpmnModelConstants.DC_ATTRIBUTE_HEIGHT;
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.DC_ATTRIBUTE_WIDTH;
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.DC_ATTRIBUTE_X;
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.DC_ATTRIBUTE_Y;
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.DC_ELEMENT_BOUNDS;
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.DC_NS;

import io.zeebe.model.bpmn.impl.instance.BpmnModelElementInstanceImpl;
import io.zeebe.model.bpmn.instance.dc.Bounds;
import org.camunda.bpm.model.xml.ModelBuilder;
import org.camunda.bpm.model.xml.impl.instance.ModelTypeInstanceContext;
import org.camunda.bpm.model.xml.type.ModelElementTypeBuilder;
import org.camunda.bpm.model.xml.type.ModelElementTypeBuilder.ModelTypeInstanceProvider;
import org.camunda.bpm.model.xml.type.attribute.Attribute;

/**
 * The DC bounds element
 *
 * @author Sebastian Menski
 */
public class BoundsImpl extends BpmnModelElementInstanceImpl implements Bounds {

  protected static Attribute<Double> xAttribute;
  protected static Attribute<Double> yAttribute;
  protected static Attribute<Double> widthAttribute;
  protected static Attribute<Double> heightAttribute;

  public BoundsImpl(final ModelTypeInstanceContext instanceContext) {
    super(instanceContext);
  }

  public static void registerType(final ModelBuilder modelBuilder) {
    final ModelElementTypeBuilder typeBuilder =
        modelBuilder
            .defineType(Bounds.class, DC_ELEMENT_BOUNDS)
            .namespaceUri(DC_NS)
            .instanceProvider(
                new ModelTypeInstanceProvider<Bounds>() {
                  @Override
                  public Bounds newInstance(final ModelTypeInstanceContext instanceContext) {
                    return new BoundsImpl(instanceContext);
                  }
                });

    xAttribute = typeBuilder.doubleAttribute(DC_ATTRIBUTE_X).required().build();

    yAttribute = typeBuilder.doubleAttribute(DC_ATTRIBUTE_Y).required().build();

    widthAttribute = typeBuilder.doubleAttribute(DC_ATTRIBUTE_WIDTH).required().build();

    heightAttribute = typeBuilder.doubleAttribute(DC_ATTRIBUTE_HEIGHT).required().build();

    typeBuilder.build();
  }

  @Override
  public Double getX() {
    return xAttribute.getValue(this);
  }

  @Override
  public void setX(final double x) {
    xAttribute.setValue(this, x);
  }

  @Override
  public Double getY() {
    return yAttribute.getValue(this);
  }

  @Override
  public void setY(final double y) {
    yAttribute.setValue(this, y);
  }

  @Override
  public Double getWidth() {
    return widthAttribute.getValue(this);
  }

  @Override
  public void setWidth(final double width) {
    widthAttribute.setValue(this, width);
  }

  @Override
  public Double getHeight() {
    return heightAttribute.getValue(this);
  }

  @Override
  public void setHeight(final double height) {
    heightAttribute.setValue(this, height);
  }
}
