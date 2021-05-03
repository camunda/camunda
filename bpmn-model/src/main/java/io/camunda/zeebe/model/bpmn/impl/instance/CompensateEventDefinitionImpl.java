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
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.BPMN_ATTRIBUTE_ACTIVITY_REF;
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.BPMN_ATTRIBUTE_WAIT_FOR_COMPLETION;
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.BPMN_ELEMENT_COMPENSATE_EVENT_DEFINITION;

import io.zeebe.model.bpmn.instance.Activity;
import io.zeebe.model.bpmn.instance.CompensateEventDefinition;
import io.zeebe.model.bpmn.instance.EventDefinition;
import org.camunda.bpm.model.xml.ModelBuilder;
import org.camunda.bpm.model.xml.impl.instance.ModelTypeInstanceContext;
import org.camunda.bpm.model.xml.type.ModelElementTypeBuilder;
import org.camunda.bpm.model.xml.type.ModelElementTypeBuilder.ModelTypeInstanceProvider;
import org.camunda.bpm.model.xml.type.attribute.Attribute;
import org.camunda.bpm.model.xml.type.reference.AttributeReference;

/**
 * The BPMN compensateEventDefinition element
 *
 * @author Sebastian Menski
 */
public class CompensateEventDefinitionImpl extends EventDefinitionImpl
    implements CompensateEventDefinition {

  protected static Attribute<Boolean> waitForCompletionAttribute;
  protected static AttributeReference<Activity> activityRefAttribute;

  public CompensateEventDefinitionImpl(final ModelTypeInstanceContext context) {
    super(context);
  }

  public static void registerType(final ModelBuilder modelBuilder) {
    final ModelElementTypeBuilder typeBuilder =
        modelBuilder
            .defineType(CompensateEventDefinition.class, BPMN_ELEMENT_COMPENSATE_EVENT_DEFINITION)
            .namespaceUri(BPMN20_NS)
            .extendsType(EventDefinition.class)
            .instanceProvider(
                new ModelTypeInstanceProvider<CompensateEventDefinition>() {
                  @Override
                  public CompensateEventDefinition newInstance(
                      final ModelTypeInstanceContext instanceContext) {
                    return new CompensateEventDefinitionImpl(instanceContext);
                  }
                });

    waitForCompletionAttribute =
        typeBuilder.booleanAttribute(BPMN_ATTRIBUTE_WAIT_FOR_COMPLETION).build();

    activityRefAttribute =
        typeBuilder
            .stringAttribute(BPMN_ATTRIBUTE_ACTIVITY_REF)
            .qNameAttributeReference(Activity.class)
            .build();

    typeBuilder.build();
  }

  @Override
  public boolean isWaitForCompletion() {
    return waitForCompletionAttribute.getValue(this);
  }

  @Override
  public void setWaitForCompletion(final boolean isWaitForCompletion) {
    waitForCompletionAttribute.setValue(this, isWaitForCompletion);
  }

  @Override
  public Activity getActivity() {
    return activityRefAttribute.getReferenceTargetElement(this);
  }

  @Override
  public void setActivity(final Activity activity) {
    activityRefAttribute.setReferenceTargetElement(this, activity);
  }
}
