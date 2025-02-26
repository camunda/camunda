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
package io.camunda.zeebe.model.bpmn.impl.instance;

import static io.camunda.zeebe.model.bpmn.impl.BpmnModelConstants.BPMN20_NS;
import static io.camunda.zeebe.model.bpmn.impl.BpmnModelConstants.BPMN_ATTRIBUTE_CANCEL_REMAINING_INSTANCES;
import static io.camunda.zeebe.model.bpmn.impl.BpmnModelConstants.BPMN_ELEMENT_AD_HOC_SUB_PROCESS;

import io.camunda.zeebe.model.bpmn.instance.AdHocSubProcess;
import io.camunda.zeebe.model.bpmn.instance.CompletionCondition;
import io.camunda.zeebe.model.bpmn.instance.SubProcess;
import org.camunda.bpm.model.xml.ModelBuilder;
import org.camunda.bpm.model.xml.impl.instance.ModelTypeInstanceContext;
import org.camunda.bpm.model.xml.type.ModelElementTypeBuilder;
import org.camunda.bpm.model.xml.type.ModelElementTypeBuilder.ModelTypeInstanceProvider;
import org.camunda.bpm.model.xml.type.attribute.Attribute;
import org.camunda.bpm.model.xml.type.child.ChildElement;
import org.camunda.bpm.model.xml.type.child.SequenceBuilder;

public class AdHocSubProcessImpl extends SubProcessImpl implements AdHocSubProcess {

  protected static Attribute<Boolean> cancelRemainingInstancesAttribute;
  protected static ChildElement<CompletionCondition> completionConditionChild;

  public AdHocSubProcessImpl(final ModelTypeInstanceContext context) {
    super(context);
  }

  public static void registerType(final ModelBuilder modelBuilder) {
    final ModelElementTypeBuilder typeBuilder =
        modelBuilder
            .defineType(AdHocSubProcess.class, BPMN_ELEMENT_AD_HOC_SUB_PROCESS)
            .namespaceUri(BPMN20_NS)
            .extendsType(SubProcess.class)
            .instanceProvider(
                new ModelTypeInstanceProvider<AdHocSubProcess>() {
                  @Override
                  public AdHocSubProcess newInstance(
                      final ModelTypeInstanceContext instanceContext) {
                    return new AdHocSubProcessImpl(instanceContext);
                  }
                });

    cancelRemainingInstancesAttribute =
        typeBuilder
            .booleanAttribute(BPMN_ATTRIBUTE_CANCEL_REMAINING_INSTANCES)
            .defaultValue(false)
            .build();

    final SequenceBuilder sequenceBuilder = typeBuilder.sequence();
    completionConditionChild = sequenceBuilder.element(CompletionCondition.class).build();

    typeBuilder.build();
  }

  @Override
  public CompletionCondition getCompletionCondition() {
    return completionConditionChild.getChild(this);
  }

  @Override
  public void setCompletionCondition(final CompletionCondition completionCondition) {
    completionConditionChild.setChild(this, completionCondition);
  }

  @Override
  public boolean isCancelRemainingInstancesEnabled() {
    return cancelRemainingInstancesAttribute.getValue(this);
  }

  @Override
  public void setCancelRemainingInstancesEnabled(final boolean cancelRemainingInstances) {
    cancelRemainingInstancesAttribute.setValue(this, cancelRemainingInstances);
  }
}
