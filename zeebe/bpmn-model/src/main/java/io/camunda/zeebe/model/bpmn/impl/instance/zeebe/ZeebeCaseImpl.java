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

import io.camunda.zeebe.model.bpmn.impl.BpmnModelConstants;
import io.camunda.zeebe.model.bpmn.impl.ZeebeConstants;
import io.camunda.zeebe.model.bpmn.impl.instance.BpmnModelElementInstanceImpl;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeCase;
import org.camunda.bpm.model.xml.ModelBuilder;
import org.camunda.bpm.model.xml.impl.instance.ModelTypeInstanceContext;
import org.camunda.bpm.model.xml.type.ModelElementTypeBuilder;
import org.camunda.bpm.model.xml.type.attribute.Attribute;

public class ZeebeCaseImpl extends BpmnModelElementInstanceImpl implements ZeebeCase {

  private static Attribute<String> titleAttribute;

  public ZeebeCaseImpl(final ModelTypeInstanceContext instanceContext) {
    super(instanceContext);
  }

  @Override
  public String getTitle() {
    return titleAttribute.getValue(this);
  }

  @Override
  public void setTitle(final String title) {
    titleAttribute.setValue(this, title);
  }

  public static void registerType(final ModelBuilder modelBuilder) {
    final ModelElementTypeBuilder typeBuilder =
        modelBuilder
            .defineType(ZeebeCase.class, ZeebeConstants.ELEMENT_CASE)
            .namespaceUri(BpmnModelConstants.ZEEBE_NS)
            .instanceProvider(ZeebeCaseImpl::new);
    // not required: a missing title is reported by the validator instead of failing the parse
    titleAttribute =
        typeBuilder
            .stringAttribute(ZeebeConstants.ATTRIBUTE_TITLE)
            .namespace(BpmnModelConstants.ZEEBE_NS)
            .build();
    typeBuilder.build();
  }
}
