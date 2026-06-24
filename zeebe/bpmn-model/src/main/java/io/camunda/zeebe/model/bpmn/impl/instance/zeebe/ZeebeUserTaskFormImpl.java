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
package io.camunda.zeebe.model.bpmn.impl.instance.zeebe;

import static io.camunda.zeebe.model.bpmn.impl.BpmnModelConstants.BPMN_ATTRIBUTE_ID;

import io.camunda.zeebe.model.bpmn.impl.BpmnModelConstants;
import io.camunda.zeebe.model.bpmn.impl.ZeebeConstants;
import io.camunda.zeebe.model.bpmn.impl.instance.BpmnModelElementInstanceImpl;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeUserTaskForm;
import org.camunda.bpm.model.xml.ModelBuilder;
import org.camunda.bpm.model.xml.impl.instance.ModelTypeInstanceContext;
import org.camunda.bpm.model.xml.type.ModelElementTypeBuilder;
import org.camunda.bpm.model.xml.type.attribute.Attribute;

public class ZeebeUserTaskFormImpl extends BpmnModelElementInstanceImpl
    implements ZeebeUserTaskForm {

  protected static Attribute<String> idAttribute;

  public ZeebeUserTaskFormImpl(final ModelTypeInstanceContext instanceContext) {
    super(instanceContext);
  }

  public static void registerType(final ModelBuilder modelBuilder) {
    final ModelElementTypeBuilder typeBuilder =
        modelBuilder
            .defineType(ZeebeUserTaskForm.class, ZeebeConstants.ELEMENT_USER_TASK_FORM)
            .namespaceUri(BpmnModelConstants.ZEEBE_NS)
            .instanceProvider(ZeebeUserTaskFormImpl::new);

    idAttribute = typeBuilder.stringAttribute(BPMN_ATTRIBUTE_ID).idAttribute().build();

    typeBuilder.build();
  }

  @Override
  public String getId() {
    return idAttribute.getValue(this);
  }

  @Override
  public void setId(final String id) {
    idAttribute.setValue(this, id);
  }
}
