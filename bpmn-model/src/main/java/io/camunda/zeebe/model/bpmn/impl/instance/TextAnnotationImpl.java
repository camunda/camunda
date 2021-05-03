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
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.BPMN_ATTRIBUTE_TEXT_FORMAT;
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.BPMN_ELEMENT_TEXT_ANNOTATION;

import io.zeebe.model.bpmn.instance.Artifact;
import io.zeebe.model.bpmn.instance.Text;
import io.zeebe.model.bpmn.instance.TextAnnotation;
import org.camunda.bpm.model.xml.ModelBuilder;
import org.camunda.bpm.model.xml.impl.instance.ModelTypeInstanceContext;
import org.camunda.bpm.model.xml.type.ModelElementTypeBuilder;
import org.camunda.bpm.model.xml.type.ModelElementTypeBuilder.ModelTypeInstanceProvider;
import org.camunda.bpm.model.xml.type.attribute.Attribute;
import org.camunda.bpm.model.xml.type.child.ChildElement;
import org.camunda.bpm.model.xml.type.child.SequenceBuilder;

/**
 * The BPMN 2.0 textAnnotation element
 *
 * @author Filip Hrisafov
 */
public class TextAnnotationImpl extends ArtifactImpl implements TextAnnotation {

  protected static Attribute<String> textFormatAttribute;
  protected static ChildElement<Text> textChild;

  public TextAnnotationImpl(final ModelTypeInstanceContext context) {
    super(context);
  }

  public static void registerType(final ModelBuilder modelBuilder) {
    final ModelElementTypeBuilder typeBuilder =
        modelBuilder
            .defineType(TextAnnotation.class, BPMN_ELEMENT_TEXT_ANNOTATION)
            .namespaceUri(BPMN20_NS)
            .extendsType(Artifact.class)
            .instanceProvider(
                new ModelTypeInstanceProvider<TextAnnotation>() {
                  @Override
                  public TextAnnotation newInstance(final ModelTypeInstanceContext context) {
                    return new TextAnnotationImpl(context);
                  }
                });

    textFormatAttribute =
        typeBuilder.stringAttribute(BPMN_ATTRIBUTE_TEXT_FORMAT).defaultValue("text/plain").build();

    final SequenceBuilder sequenceBuilder = typeBuilder.sequence();

    textChild = sequenceBuilder.element(Text.class).build();

    typeBuilder.build();
  }

  @Override
  public String getTextFormat() {
    return textFormatAttribute.getValue(this);
  }

  @Override
  public void setTextFormat(final String textFormat) {
    textFormatAttribute.setValue(this, textFormat);
  }

  @Override
  public Text getText() {
    return textChild.getChild(this);
  }

  @Override
  public void setText(final Text text) {
    textChild.setChild(this, text);
  }
}
