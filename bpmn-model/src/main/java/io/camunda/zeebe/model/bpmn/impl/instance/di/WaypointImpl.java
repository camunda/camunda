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

import static io.zeebe.model.bpmn.impl.BpmnModelConstants.DI_ELEMENT_WAYPOINT;
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.DI_NS;

import io.zeebe.model.bpmn.impl.instance.dc.PointImpl;
import io.zeebe.model.bpmn.instance.dc.Point;
import io.zeebe.model.bpmn.instance.di.Waypoint;
import org.camunda.bpm.model.xml.ModelBuilder;
import org.camunda.bpm.model.xml.impl.instance.ModelTypeInstanceContext;
import org.camunda.bpm.model.xml.type.ModelElementTypeBuilder;
import org.camunda.bpm.model.xml.type.ModelElementTypeBuilder.ModelTypeInstanceProvider;

/**
 * The DI waypoint element of the DI Edge type
 *
 * @author Sebastian Menski
 */
public class WaypointImpl extends PointImpl implements Waypoint {

  public WaypointImpl(final ModelTypeInstanceContext instanceContext) {
    super(instanceContext);
  }

  public static void registerType(final ModelBuilder modelBuilder) {
    final ModelElementTypeBuilder typeBuilder =
        modelBuilder
            .defineType(Waypoint.class, DI_ELEMENT_WAYPOINT)
            .namespaceUri(DI_NS)
            .extendsType(Point.class)
            .instanceProvider(
                new ModelTypeInstanceProvider<Waypoint>() {
                  @Override
                  public Waypoint newInstance(final ModelTypeInstanceContext instanceContext) {
                    return new WaypointImpl(instanceContext);
                  }
                });

    typeBuilder.build();
  }
}
