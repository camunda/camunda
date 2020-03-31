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
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.BPMN_ELEMENT_ASSIGNMENT;

import io.zeebe.model.bpmn.instance.Assignment;
import io.zeebe.model.bpmn.instance.BaseElement;
import org.camunda.bpm.model.xml.ModelBuilder;
import org.camunda.bpm.model.xml.impl.instance.ModelTypeInstanceContext;
import org.camunda.bpm.model.xml.type.ModelElementTypeBuilder;
import org.camunda.bpm.model.xml.type.ModelElementTypeBuilder.ModelTypeInstanceProvider;
import org.camunda.bpm.model.xml.type.child.ChildElement;
import org.camunda.bpm.model.xml.type.child.SequenceBuilder;

/**
 * The BPMN assignment element
 *
 * @author Sebastian Menski
 */
public class AssignmentImpl extends BaseElementImpl implements Assignment {

  protected static ChildElement<From> fromChild;
  protected static ChildElement<To> toChild;

  public AssignmentImpl(final ModelTypeInstanceContext instanceContext) {
    super(instanceContext);
  }

  public static void registerType(final ModelBuilder modelBuilder) {
    final ModelElementTypeBuilder typeBuilder =
        modelBuilder
            .defineType(Assignment.class, BPMN_ELEMENT_ASSIGNMENT)
            .namespaceUri(BPMN20_NS)
            .extendsType(BaseElement.class)
            .instanceProvider(
                new ModelTypeInstanceProvider<Assignment>() {
                  @Override
                  public Assignment newInstance(final ModelTypeInstanceContext instanceContext) {
                    return new AssignmentImpl(instanceContext);
                  }
                });

    final SequenceBuilder sequenceBuilder = typeBuilder.sequence();

    fromChild = sequenceBuilder.element(From.class).required().build();

    toChild = sequenceBuilder.element(To.class).required().build();

    typeBuilder.build();
  }

  @Override
  public From getFrom() {
    return fromChild.getChild(this);
  }

  @Override
  public void setFrom(final From from) {
    fromChild.setChild(this, from);
  }

  @Override
  public To getTo() {
    return toChild.getChild(this);
  }

  @Override
  public void setTo(final To to) {
    toChild.setChild(this, to);
  }
}
