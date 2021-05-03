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

import static io.zeebe.model.bpmn.impl.BpmnModelConstants.BPMN_ELEMENT_INTERMEDIATE_THROW_EVENT;

import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.model.bpmn.builder.IntermediateThrowEventBuilder;
import io.zeebe.model.bpmn.impl.BpmnModelConstants;
import io.zeebe.model.bpmn.instance.IntermediateThrowEvent;
import io.zeebe.model.bpmn.instance.ThrowEvent;
import org.camunda.bpm.model.xml.ModelBuilder;
import org.camunda.bpm.model.xml.impl.instance.ModelTypeInstanceContext;
import org.camunda.bpm.model.xml.type.ModelElementTypeBuilder;
import org.camunda.bpm.model.xml.type.ModelElementTypeBuilder.ModelTypeInstanceProvider;

/**
 * The BPMN intermediateThrowEvent element
 *
 * @author Sebastian Menski
 */
public class IntermediateThrowEventImpl extends ThrowEventImpl implements IntermediateThrowEvent {

  public IntermediateThrowEventImpl(final ModelTypeInstanceContext context) {
    super(context);
  }

  public static void registerType(final ModelBuilder modelBuilder) {
    final ModelElementTypeBuilder typeBuilder =
        modelBuilder
            .defineType(IntermediateThrowEvent.class, BPMN_ELEMENT_INTERMEDIATE_THROW_EVENT)
            .namespaceUri(BpmnModelConstants.BPMN20_NS)
            .extendsType(ThrowEvent.class)
            .instanceProvider(
                new ModelTypeInstanceProvider<IntermediateThrowEvent>() {
                  @Override
                  public IntermediateThrowEvent newInstance(
                      final ModelTypeInstanceContext instanceContext) {
                    return new IntermediateThrowEventImpl(instanceContext);
                  }
                });

    typeBuilder.build();
  }

  @Override
  public IntermediateThrowEventBuilder builder() {
    return new IntermediateThrowEventBuilder((BpmnModelInstance) modelInstance, this);
  }
}
