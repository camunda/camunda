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

import static io.zeebe.model.bpmn.impl.BpmnModelConstants.BPMNDI_ELEMENT_BPMN_DIAGRAM;
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.BPMNDI_NS;

import io.zeebe.model.bpmn.impl.instance.di.DiagramImpl;
import io.zeebe.model.bpmn.instance.bpmndi.BpmnDiagram;
import io.zeebe.model.bpmn.instance.bpmndi.BpmnLabelStyle;
import io.zeebe.model.bpmn.instance.bpmndi.BpmnPlane;
import io.zeebe.model.bpmn.instance.di.Diagram;
import java.util.Collection;
import org.camunda.bpm.model.xml.ModelBuilder;
import org.camunda.bpm.model.xml.impl.instance.ModelTypeInstanceContext;
import org.camunda.bpm.model.xml.type.ModelElementTypeBuilder;
import org.camunda.bpm.model.xml.type.ModelElementTypeBuilder.ModelTypeInstanceProvider;
import org.camunda.bpm.model.xml.type.child.ChildElement;
import org.camunda.bpm.model.xml.type.child.ChildElementCollection;
import org.camunda.bpm.model.xml.type.child.SequenceBuilder;

/**
 * The BPMNDI BPMNDiagram element
 *
 * @author Sebastian Menski
 */
public class BpmnDiagramImpl extends DiagramImpl implements BpmnDiagram {

  protected static ChildElement<BpmnPlane> bpmnPlaneChild;
  protected static ChildElementCollection<BpmnLabelStyle> bpmnLabelStyleCollection;

  public BpmnDiagramImpl(final ModelTypeInstanceContext instanceContext) {
    super(instanceContext);
  }

  public static void registerType(final ModelBuilder modelBuilder) {
    final ModelElementTypeBuilder typeBuilder =
        modelBuilder
            .defineType(BpmnDiagram.class, BPMNDI_ELEMENT_BPMN_DIAGRAM)
            .namespaceUri(BPMNDI_NS)
            .extendsType(Diagram.class)
            .instanceProvider(
                new ModelTypeInstanceProvider<BpmnDiagram>() {
                  @Override
                  public BpmnDiagram newInstance(final ModelTypeInstanceContext instanceContext) {
                    return new BpmnDiagramImpl(instanceContext);
                  }
                });

    final SequenceBuilder sequenceBuilder = typeBuilder.sequence();

    bpmnPlaneChild = sequenceBuilder.element(BpmnPlane.class).required().build();

    bpmnLabelStyleCollection = sequenceBuilder.elementCollection(BpmnLabelStyle.class).build();

    typeBuilder.build();
  }

  @Override
  public BpmnPlane getBpmnPlane() {
    return bpmnPlaneChild.getChild(this);
  }

  @Override
  public void setBpmnPlane(final BpmnPlane bpmnPlane) {
    bpmnPlaneChild.setChild(this, bpmnPlane);
  }

  @Override
  public Collection<BpmnLabelStyle> getBpmnLabelStyles() {
    return bpmnLabelStyleCollection.get(this);
  }
}
