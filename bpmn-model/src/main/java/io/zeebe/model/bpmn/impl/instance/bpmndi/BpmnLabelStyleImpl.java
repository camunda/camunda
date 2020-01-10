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

package io.zeebe.model.bpmn.impl.instance.bpmndi;

import static io.zeebe.model.bpmn.impl.BpmnModelConstants.BPMNDI_ELEMENT_BPMN_LABEL_STYLE;
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.BPMNDI_NS;

import io.zeebe.model.bpmn.impl.instance.di.StyleImpl;
import io.zeebe.model.bpmn.instance.bpmndi.BpmnLabelStyle;
import io.zeebe.model.bpmn.instance.dc.Font;
import io.zeebe.model.bpmn.instance.di.Style;
import org.camunda.bpm.model.xml.ModelBuilder;
import org.camunda.bpm.model.xml.impl.instance.ModelTypeInstanceContext;
import org.camunda.bpm.model.xml.type.ModelElementTypeBuilder;
import org.camunda.bpm.model.xml.type.ModelElementTypeBuilder.ModelTypeInstanceProvider;
import org.camunda.bpm.model.xml.type.child.ChildElement;
import org.camunda.bpm.model.xml.type.child.SequenceBuilder;

/**
 * The BPMNDI BPMNLabelStyle element
 *
 * @author Sebastian Menski
 */
public class BpmnLabelStyleImpl extends StyleImpl implements BpmnLabelStyle {

  protected static ChildElement<Font> fontChild;

  public BpmnLabelStyleImpl(final ModelTypeInstanceContext instanceContext) {
    super(instanceContext);
  }

  public static void registerType(final ModelBuilder modelBuilder) {
    final ModelElementTypeBuilder typeBuilder =
        modelBuilder
            .defineType(BpmnLabelStyle.class, BPMNDI_ELEMENT_BPMN_LABEL_STYLE)
            .namespaceUri(BPMNDI_NS)
            .extendsType(Style.class)
            .instanceProvider(
                new ModelTypeInstanceProvider<BpmnLabelStyle>() {
                  @Override
                  public BpmnLabelStyle newInstance(
                      final ModelTypeInstanceContext instanceContext) {
                    return new BpmnLabelStyleImpl(instanceContext);
                  }
                });

    final SequenceBuilder sequenceBuilder = typeBuilder.sequence();

    fontChild = sequenceBuilder.element(Font.class).required().build();

    typeBuilder.build();
  }

  @Override
  public Font getFont() {
    return fontChild.getChild(this);
  }

  @Override
  public void setFont(final Font font) {
    fontChild.setChild(this, font);
  }
}
