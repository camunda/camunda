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

package io.zeebe.model.bpmn.impl.instance.di;

import static io.zeebe.model.bpmn.impl.BpmnModelConstants.DI_ATTRIBUTE_DOCUMENTATION;
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.DI_ATTRIBUTE_ID;
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.DI_ATTRIBUTE_NAME;
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.DI_ATTRIBUTE_RESOLUTION;
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.DI_ELEMENT_DIAGRAM;
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.DI_NS;

import io.zeebe.model.bpmn.impl.instance.BpmnModelElementInstanceImpl;
import io.zeebe.model.bpmn.instance.di.Diagram;
import org.camunda.bpm.model.xml.ModelBuilder;
import org.camunda.bpm.model.xml.impl.instance.ModelTypeInstanceContext;
import org.camunda.bpm.model.xml.type.ModelElementTypeBuilder;
import org.camunda.bpm.model.xml.type.attribute.Attribute;

/**
 * The DI Diagram element
 *
 * @author Sebastian Menski
 */
public abstract class DiagramImpl extends BpmnModelElementInstanceImpl implements Diagram {

  protected static Attribute<String> nameAttribute;
  protected static Attribute<String> documentationAttribute;
  protected static Attribute<Double> resolutionAttribute;
  protected static Attribute<String> idAttribute;

  public DiagramImpl(final ModelTypeInstanceContext instanceContext) {
    super(instanceContext);
  }

  public static void registerType(final ModelBuilder modelBuilder) {
    final ModelElementTypeBuilder typeBuilder =
        modelBuilder
            .defineType(Diagram.class, DI_ELEMENT_DIAGRAM)
            .namespaceUri(DI_NS)
            .abstractType();

    nameAttribute = typeBuilder.stringAttribute(DI_ATTRIBUTE_NAME).build();

    documentationAttribute = typeBuilder.stringAttribute(DI_ATTRIBUTE_DOCUMENTATION).build();

    resolutionAttribute = typeBuilder.doubleAttribute(DI_ATTRIBUTE_RESOLUTION).build();

    idAttribute = typeBuilder.stringAttribute(DI_ATTRIBUTE_ID).idAttribute().build();

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
  public String getDocumentation() {
    return documentationAttribute.getValue(this);
  }

  @Override
  public void setDocumentation(final String documentation) {
    documentationAttribute.setValue(this, documentation);
  }

  @Override
  public double getResolution() {
    return resolutionAttribute.getValue(this);
  }

  @Override
  public void setResolution(final double resolution) {
    resolutionAttribute.setValue(this, resolution);
  }

  @Override
  public String getId() {
    return idAttribute.getValue(this);
  }

  @Override
  public void setId(final String id) {
    idAttribute.setValue(this, id);
  }
}
