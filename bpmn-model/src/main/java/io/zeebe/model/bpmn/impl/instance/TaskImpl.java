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
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.BPMN_ELEMENT_TASK;

import io.zeebe.model.bpmn.builder.AbstractTaskBuilder;
import io.zeebe.model.bpmn.instance.Activity;
import io.zeebe.model.bpmn.instance.Task;
import io.zeebe.model.bpmn.instance.bpmndi.BpmnShape;
import org.camunda.bpm.model.xml.ModelBuilder;
import org.camunda.bpm.model.xml.impl.instance.ModelTypeInstanceContext;
import org.camunda.bpm.model.xml.impl.util.ModelTypeException;
import org.camunda.bpm.model.xml.type.ModelElementTypeBuilder;
import org.camunda.bpm.model.xml.type.ModelElementTypeBuilder.ModelTypeInstanceProvider;
import org.camunda.bpm.model.xml.type.attribute.Attribute;

/**
 * The BPMN task element
 *
 * @author Sebastian Menski
 */
public class TaskImpl extends ActivityImpl implements Task {

  /** camunda extensions */
  protected static Attribute<Boolean> camundaAsyncAttribute;

  public TaskImpl(final ModelTypeInstanceContext context) {
    super(context);
  }

  public static void registerType(final ModelBuilder modelBuilder) {
    final ModelElementTypeBuilder typeBuilder =
        modelBuilder
            .defineType(Task.class, BPMN_ELEMENT_TASK)
            .namespaceUri(BPMN20_NS)
            .extendsType(Activity.class)
            .instanceProvider(
                new ModelTypeInstanceProvider<Task>() {
                  @Override
                  public Task newInstance(final ModelTypeInstanceContext instanceContext) {
                    return new TaskImpl(instanceContext);
                  }
                });

    typeBuilder.build();
  }

  @Override
  @SuppressWarnings("rawtypes")
  public AbstractTaskBuilder builder() {
    throw new ModelTypeException("No builder implemented.");
  }

  @Override
  public BpmnShape getDiagramElement() {
    return (BpmnShape) super.getDiagramElement();
  }
}
