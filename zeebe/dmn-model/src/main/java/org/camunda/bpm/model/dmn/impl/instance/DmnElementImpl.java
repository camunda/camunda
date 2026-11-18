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
import static org.camunda.bpm.model.dmn.impl.DmnModelConstants.DMN_ATTRIBUTE_ID;
import static org.camunda.bpm.model.dmn.impl.DmnModelConstants.DMN_ATTRIBUTE_LABEL;
import static org.camunda.bpm.model.dmn.impl.DmnModelConstants.DMN_ELEMENT;

import org.camunda.bpm.model.dmn.instance.Description;
import org.camunda.bpm.model.dmn.instance.DmnElement;
import org.camunda.bpm.model.dmn.instance.ExtensionElements;
import org.camunda.bpm.model.xml.ModelBuilder;
import org.camunda.bpm.model.xml.impl.instance.ModelTypeInstanceContext;
import org.camunda.bpm.model.xml.type.ModelElementTypeBuilder;
import org.camunda.bpm.model.xml.type.attribute.Attribute;
import org.camunda.bpm.model.xml.type.child.ChildElement;
import org.camunda.bpm.model.xml.type.child.SequenceBuilder;

public abstract class DmnElementImpl extends DmnModelElementInstanceImpl implements DmnElement {

  protected static Attribute<String> idAttribute;
  protected static Attribute<String> labelAttribute;

  protected static ChildElement<Description> descriptionChild;
  protected static ChildElement<ExtensionElements> extensionElementsChild;

  public DmnElementImpl (ModelTypeInstanceContext instanceContext) {
    super(instanceContext);
  }

  public String getId() {
    return idAttribute.getValue(this);
  }

  public void setId(String id) {
    idAttribute.setValue(this, id);
  }

  public String getLabel() {
    return labelAttribute.getValue(this);
  }

  public void setLabel(String label) {
    labelAttribute.setValue(this, label);
  }

  public Description getDescription() {
    return descriptionChild.getChild(this);
  }

  public void setDescription(Description description) {
    descriptionChild.setChild(this, description);
  }

  public ExtensionElements getExtensionElements() {
    return extensionElementsChild.getChild(this);
  }

  public void setExtensionElements(ExtensionElements extensionElements) {
    extensionElementsChild.setChild(this, extensionElements);
  }

  public static void registerType(ModelBuilder modelBuilder) {
    ModelElementTypeBuilder typeBuilder = modelBuilder.defineType(DmnElement.class, DMN_ELEMENT)
      .namespaceUri(LATEST_DMN_NS)
      .abstractType();

    idAttribute = typeBuilder.stringAttribute(DMN_ATTRIBUTE_ID)
      .idAttribute()
      .build();

    labelAttribute = typeBuilder.stringAttribute(DMN_ATTRIBUTE_LABEL)
      .build();

    SequenceBuilder sequenceBuilder = typeBuilder.sequence();

    descriptionChild = sequenceBuilder.element(Description.class)
      .build();

    extensionElementsChild = sequenceBuilder.element(ExtensionElements.class)
      .build();

    typeBuilder.build();
  }

}
