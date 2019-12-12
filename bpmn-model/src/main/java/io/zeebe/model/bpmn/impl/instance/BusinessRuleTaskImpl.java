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
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.BPMN_ATTRIBUTE_IMPLEMENTATION;
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.BPMN_ELEMENT_BUSINESS_RULE_TASK;

import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.model.bpmn.builder.BusinessRuleTaskBuilder;
import io.zeebe.model.bpmn.instance.BusinessRuleTask;
import io.zeebe.model.bpmn.instance.Rendering;
import io.zeebe.model.bpmn.instance.Task;
import org.camunda.bpm.model.xml.ModelBuilder;
import org.camunda.bpm.model.xml.impl.instance.ModelTypeInstanceContext;
import org.camunda.bpm.model.xml.type.ModelElementTypeBuilder;
import org.camunda.bpm.model.xml.type.ModelElementTypeBuilder.ModelTypeInstanceProvider;
import org.camunda.bpm.model.xml.type.attribute.Attribute;
import org.camunda.bpm.model.xml.type.child.ChildElementCollection;

/**
 * The BPMN businessRuleTask element
 *
 * @author Sebastian Menski
 */
public class BusinessRuleTaskImpl extends TaskImpl implements BusinessRuleTask {

  protected static Attribute<String> implementationAttribute;
  protected static ChildElementCollection<Rendering> renderingCollection;

  public BusinessRuleTaskImpl(final ModelTypeInstanceContext context) {
    super(context);
  }

  public static void registerType(final ModelBuilder modelBuilder) {
    final ModelElementTypeBuilder typeBuilder =
        modelBuilder
            .defineType(BusinessRuleTask.class, BPMN_ELEMENT_BUSINESS_RULE_TASK)
            .namespaceUri(BPMN20_NS)
            .extendsType(Task.class)
            .instanceProvider(
                new ModelTypeInstanceProvider<BusinessRuleTask>() {
                  @Override
                  public BusinessRuleTask newInstance(
                      final ModelTypeInstanceContext instanceContext) {
                    return new BusinessRuleTaskImpl(instanceContext);
                  }
                });

    implementationAttribute =
        typeBuilder
            .stringAttribute(BPMN_ATTRIBUTE_IMPLEMENTATION)
            .defaultValue("##unspecified")
            .build();

    typeBuilder.build();
  }

  @Override
  public BusinessRuleTaskBuilder builder() {
    return new BusinessRuleTaskBuilder((BpmnModelInstance) modelInstance, this);
  }

  @Override
  public String getImplementation() {
    return implementationAttribute.getValue(this);
  }

  @Override
  public void setImplementation(final String implementation) {
    implementationAttribute.setValue(this, implementation);
  }
}
