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
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.BPMN_ATTRIBUTE_ATTACHED_TO_REF;
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.BPMN_ATTRIBUTE_CANCEL_ACTIVITY;
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.BPMN_ELEMENT_BOUNDARY_EVENT;

import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.model.bpmn.builder.BoundaryEventBuilder;
import io.zeebe.model.bpmn.instance.Activity;
import io.zeebe.model.bpmn.instance.BoundaryEvent;
import io.zeebe.model.bpmn.instance.CatchEvent;
import org.camunda.bpm.model.xml.ModelBuilder;
import org.camunda.bpm.model.xml.impl.instance.ModelTypeInstanceContext;
import org.camunda.bpm.model.xml.type.ModelElementTypeBuilder;
import org.camunda.bpm.model.xml.type.ModelElementTypeBuilder.ModelTypeInstanceProvider;
import org.camunda.bpm.model.xml.type.attribute.Attribute;
import org.camunda.bpm.model.xml.type.reference.AttributeReference;

/**
 * The BPMN boundaryEvent element
 *
 * @author Sebastian Menski
 */
public class BoundaryEventImpl extends CatchEventImpl implements BoundaryEvent {

  protected static Attribute<Boolean> cancelActivityAttribute;
  protected static AttributeReference<Activity> attachedToRefAttribute;

  public BoundaryEventImpl(final ModelTypeInstanceContext context) {
    super(context);
  }

  public static void registerType(final ModelBuilder modelBuilder) {
    final ModelElementTypeBuilder typeBuilder =
        modelBuilder
            .defineType(BoundaryEvent.class, BPMN_ELEMENT_BOUNDARY_EVENT)
            .namespaceUri(BPMN20_NS)
            .extendsType(CatchEvent.class)
            .instanceProvider(
                new ModelTypeInstanceProvider<BoundaryEvent>() {
                  @Override
                  public BoundaryEvent newInstance(final ModelTypeInstanceContext instanceContext) {
                    return new BoundaryEventImpl(instanceContext);
                  }
                });

    cancelActivityAttribute =
        typeBuilder.booleanAttribute(BPMN_ATTRIBUTE_CANCEL_ACTIVITY).defaultValue(true).build();

    attachedToRefAttribute =
        typeBuilder
            .stringAttribute(BPMN_ATTRIBUTE_ATTACHED_TO_REF)
            .required()
            .qNameAttributeReference(Activity.class)
            .build();

    typeBuilder.build();
  }

  @Override
  public BoundaryEventBuilder builder() {
    return new BoundaryEventBuilder((BpmnModelInstance) modelInstance, this);
  }

  @Override
  public boolean cancelActivity() {
    return cancelActivityAttribute.getValue(this);
  }

  @Override
  public void setCancelActivity(final boolean cancelActivity) {
    cancelActivityAttribute.setValue(this, cancelActivity);
  }

  @Override
  public Activity getAttachedTo() {
    return attachedToRefAttribute.getReferenceTargetElement(this);
  }

  @Override
  public void setAttachedTo(final Activity attachedTo) {
    attachedToRefAttribute.setReferenceTargetElement(this, attachedTo);
  }
}
