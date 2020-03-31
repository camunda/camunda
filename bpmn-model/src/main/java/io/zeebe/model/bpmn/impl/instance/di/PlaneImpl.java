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

import static io.zeebe.model.bpmn.impl.BpmnModelConstants.DI_ELEMENT_PLANE;
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.DI_NS;

import io.zeebe.model.bpmn.instance.di.DiagramElement;
import io.zeebe.model.bpmn.instance.di.Node;
import io.zeebe.model.bpmn.instance.di.Plane;
import java.util.Collection;
import org.camunda.bpm.model.xml.ModelBuilder;
import org.camunda.bpm.model.xml.impl.instance.ModelTypeInstanceContext;
import org.camunda.bpm.model.xml.type.ModelElementTypeBuilder;
import org.camunda.bpm.model.xml.type.child.ChildElementCollection;
import org.camunda.bpm.model.xml.type.child.SequenceBuilder;

/**
 * The DI Plane element
 *
 * @author Sebastian Menski
 */
public abstract class PlaneImpl extends NodeImpl implements Plane {

  protected static ChildElementCollection<DiagramElement> diagramElementCollection;

  public PlaneImpl(final ModelTypeInstanceContext instanceContext) {
    super(instanceContext);
  }

  public static void registerType(final ModelBuilder modelBuilder) {
    final ModelElementTypeBuilder typeBuilder =
        modelBuilder
            .defineType(Plane.class, DI_ELEMENT_PLANE)
            .namespaceUri(DI_NS)
            .extendsType(Node.class)
            .abstractType();

    final SequenceBuilder sequenceBuilder = typeBuilder.sequence();

    diagramElementCollection = sequenceBuilder.elementCollection(DiagramElement.class).build();

    typeBuilder.build();
  }

  @Override
  public Collection<DiagramElement> getDiagramElements() {
    return diagramElementCollection.get(this);
  }
}
