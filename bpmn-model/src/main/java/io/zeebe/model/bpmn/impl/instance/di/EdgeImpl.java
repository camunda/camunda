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

import static io.zeebe.model.bpmn.impl.BpmnModelConstants.DI_ELEMENT_EDGE;
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.DI_NS;

import io.zeebe.model.bpmn.instance.di.DiagramElement;
import io.zeebe.model.bpmn.instance.di.Edge;
import io.zeebe.model.bpmn.instance.di.Waypoint;
import java.util.Collection;
import org.camunda.bpm.model.xml.ModelBuilder;
import org.camunda.bpm.model.xml.impl.instance.ModelTypeInstanceContext;
import org.camunda.bpm.model.xml.type.ModelElementTypeBuilder;
import org.camunda.bpm.model.xml.type.child.ChildElementCollection;
import org.camunda.bpm.model.xml.type.child.SequenceBuilder;

/** @author Sebastian Menski */
public abstract class EdgeImpl extends DiagramElementImpl implements Edge {

  protected static ChildElementCollection<Waypoint> waypointCollection;

  public EdgeImpl(final ModelTypeInstanceContext instanceContext) {
    super(instanceContext);
  }

  public static void registerType(final ModelBuilder modelBuilder) {
    final ModelElementTypeBuilder typeBuilder =
        modelBuilder
            .defineType(Edge.class, DI_ELEMENT_EDGE)
            .namespaceUri(DI_NS)
            .extendsType(DiagramElement.class)
            .abstractType();

    final SequenceBuilder sequenceBuilder = typeBuilder.sequence();

    waypointCollection = sequenceBuilder.elementCollection(Waypoint.class).minOccurs(2).build();

    typeBuilder.build();
  }

  @Override
  public Collection<Waypoint> getWaypoints() {
    return waypointCollection.get(this);
  }
}
