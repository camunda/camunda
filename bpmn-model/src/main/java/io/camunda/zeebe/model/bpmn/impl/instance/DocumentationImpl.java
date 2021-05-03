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
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.BPMN_ATTRIBUTE_ID;
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.BPMN_ATTRIBUTE_TEXT_FORMAT;
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.BPMN_ELEMENT_DOCUMENTATION;

import io.zeebe.model.bpmn.instance.Documentation;
import org.camunda.bpm.model.xml.ModelBuilder;
import org.camunda.bpm.model.xml.impl.instance.ModelTypeInstanceContext;
import org.camunda.bpm.model.xml.type.ModelElementTypeBuilder;
import org.camunda.bpm.model.xml.type.ModelElementTypeBuilder.ModelTypeInstanceProvider;
import org.camunda.bpm.model.xml.type.attribute.Attribute;

/**
 * The BPMN documentation element
 *
 * @author Daniel Meyer
 * @author Sebastian Menski
 */
public class DocumentationImpl extends BpmnModelElementInstanceImpl implements Documentation {

  protected static Attribute<String> idAttribute;
  protected static Attribute<String> textFormatAttribute;

  public DocumentationImpl(final ModelTypeInstanceContext context) {
    super(context);
  }

  public static void registerType(final ModelBuilder modelBuilder) {

    final ModelElementTypeBuilder typeBuilder =
        modelBuilder
            .defineType(Documentation.class, BPMN_ELEMENT_DOCUMENTATION)
            .namespaceUri(BPMN20_NS)
            .instanceProvider(
                new ModelTypeInstanceProvider<Documentation>() {
                  @Override
                  public Documentation newInstance(final ModelTypeInstanceContext instanceContext) {
                    return new DocumentationImpl(instanceContext);
                  }
                });

    idAttribute = typeBuilder.stringAttribute(BPMN_ATTRIBUTE_ID).idAttribute().build();

    textFormatAttribute =
        typeBuilder.stringAttribute(BPMN_ATTRIBUTE_TEXT_FORMAT).defaultValue("text/plain").build();

    typeBuilder.build();
  }

  @Override
  public String getId() {
    return idAttribute.getValue(this);
  }

  @Override
  public void setId(final String id) {
    idAttribute.setValue(this, id);
  }

  @Override
  public String getTextFormat() {
    return textFormatAttribute.getValue(this);
  }

  @Override
  public void setTextFormat(final String textFormat) {
    textFormatAttribute.setValue(this, textFormat);
  }
}
