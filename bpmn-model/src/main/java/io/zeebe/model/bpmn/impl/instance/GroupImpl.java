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
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.BPMN_ATTRIBUTE_CATEGORY_VALUE_REF;
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.BPMN_ELEMENT_GROUP;

import io.zeebe.model.bpmn.instance.Artifact;
import io.zeebe.model.bpmn.instance.CategoryValue;
import io.zeebe.model.bpmn.instance.Group;
import io.zeebe.model.bpmn.instance.bpmndi.BpmnEdge;
import org.camunda.bpm.model.xml.ModelBuilder;
import org.camunda.bpm.model.xml.impl.instance.ModelTypeInstanceContext;
import org.camunda.bpm.model.xml.type.ModelElementTypeBuilder;
import org.camunda.bpm.model.xml.type.ModelElementTypeBuilder.ModelTypeInstanceProvider;
import org.camunda.bpm.model.xml.type.reference.AttributeReference;

public class GroupImpl extends ArtifactImpl implements Group {

  protected static AttributeReference<CategoryValue> categoryValueRefAttribute;

  public GroupImpl(final ModelTypeInstanceContext instanceContext) {
    super(instanceContext);
  }

  public static void registerType(final ModelBuilder modelBuilder) {
    final ModelElementTypeBuilder typeBuilder =
        modelBuilder
            .defineType(Group.class, BPMN_ELEMENT_GROUP)
            .namespaceUri(BPMN20_NS)
            .extendsType(Artifact.class)
            .instanceProvider(
                new ModelTypeInstanceProvider<Group>() {
                  @Override
                  public Group newInstance(final ModelTypeInstanceContext instanceContext) {
                    return new GroupImpl(instanceContext);
                  }
                });

    categoryValueRefAttribute =
        typeBuilder
            .stringAttribute(BPMN_ATTRIBUTE_CATEGORY_VALUE_REF)
            .qNameAttributeReference(CategoryValue.class)
            .build();

    typeBuilder.build();
  }

  @Override
  public CategoryValue getCategory() {
    return categoryValueRefAttribute.getReferenceTargetElement(this);
  }

  @Override
  public void setCategory(final CategoryValue categoryValue) {
    categoryValueRefAttribute.setReferenceTargetElement(this, categoryValue);
  }

  @Override
  public BpmnEdge getDiagramElement() {
    return (BpmnEdge) super.getDiagramElement();
  }
}
