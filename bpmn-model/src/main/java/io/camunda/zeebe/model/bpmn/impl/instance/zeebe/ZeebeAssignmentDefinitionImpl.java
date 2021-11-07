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
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeAssignmentDefinition;
import org.camunda.bpm.model.xml.ModelBuilder;
import org.camunda.bpm.model.xml.impl.instance.ModelTypeInstanceContext;
import org.camunda.bpm.model.xml.type.ModelElementTypeBuilder;
import org.camunda.bpm.model.xml.type.attribute.Attribute;

public final class ZeebeAssignmentDefinitionImpl extends BpmnModelElementInstanceImpl
    implements ZeebeAssignmentDefinition {

  private static Attribute<String> assigneeAttribute;
  private static Attribute<String> candidateGroupsAttribute;

  public ZeebeAssignmentDefinitionImpl(final ModelTypeInstanceContext instanceContext) {
    super(instanceContext);
  }

  public static void registerType(final ModelBuilder modelBuilder) {
    final ModelElementTypeBuilder typeBuilder =
        modelBuilder
            .defineType(
                ZeebeAssignmentDefinition.class, ZeebeConstants.ELEMENT_ASSIGNMENT_DEFINITION)
            .namespaceUri(BpmnModelConstants.ZEEBE_NS)
            .instanceProvider(ZeebeAssignmentDefinitionImpl::new);

    assigneeAttribute =
        typeBuilder
            .stringAttribute(ZeebeConstants.ATTRIBUTE_ASSIGNEE)
            .namespace(BpmnModelConstants.ZEEBE_NS)
            .build();

    candidateGroupsAttribute =
        typeBuilder
            .stringAttribute(ZeebeConstants.ATTRIBUTE_CANDIDATE_GROUPS)
            .namespace(BpmnModelConstants.ZEEBE_NS)
            .build();

    typeBuilder.build();
  }

  @Override
  public String getAssignee() {
    return assigneeAttribute.getValue(this);
  }

  @Override
  public void setAssignee(final String assignee) {
    assigneeAttribute.setValue(this, assignee);
  }

  @Override
  public String getCandidateGroups() {
    return candidateGroupsAttribute.getValue(this);
  }

  @Override
  public void setCandidateGroups(final String candidateGroups) {
    candidateGroupsAttribute.setValue(this, candidateGroups);
  }
}
