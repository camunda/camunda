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
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.BPMN_ATTRIBUTE_NAME;
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.BPMN_ELEMENT_RESOURCE_ROLE;

import io.zeebe.model.bpmn.instance.BaseElement;
import io.zeebe.model.bpmn.instance.Resource;
import io.zeebe.model.bpmn.instance.ResourceAssignmentExpression;
import io.zeebe.model.bpmn.instance.ResourceParameterBinding;
import io.zeebe.model.bpmn.instance.ResourceRole;
import java.util.Collection;
import org.camunda.bpm.model.xml.ModelBuilder;
import org.camunda.bpm.model.xml.impl.instance.ModelTypeInstanceContext;
import org.camunda.bpm.model.xml.type.ModelElementTypeBuilder;
import org.camunda.bpm.model.xml.type.ModelElementTypeBuilder.ModelTypeInstanceProvider;
import org.camunda.bpm.model.xml.type.attribute.Attribute;
import org.camunda.bpm.model.xml.type.child.ChildElement;
import org.camunda.bpm.model.xml.type.child.ChildElementCollection;
import org.camunda.bpm.model.xml.type.child.SequenceBuilder;
import org.camunda.bpm.model.xml.type.reference.ElementReference;

/**
 * The BPMN resourceRole element
 *
 * @author Sebastian Menski
 */
public class ResourceRoleImpl extends BaseElementImpl implements ResourceRole {

  protected static Attribute<String> nameAttribute;
  protected static ElementReference<Resource, ResourceRef> resourceRefChild;
  protected static ChildElementCollection<ResourceParameterBinding>
      resourceParameterBindingCollection;
  protected static ChildElement<ResourceAssignmentExpression> resourceAssignmentExpressionChild;

  public ResourceRoleImpl(final ModelTypeInstanceContext instanceContext) {
    super(instanceContext);
  }

  public static void registerType(final ModelBuilder modelBuilder) {
    final ModelElementTypeBuilder typeBuilder =
        modelBuilder
            .defineType(ResourceRole.class, BPMN_ELEMENT_RESOURCE_ROLE)
            .namespaceUri(BPMN20_NS)
            .extendsType(BaseElement.class)
            .instanceProvider(
                new ModelTypeInstanceProvider<ResourceRole>() {
                  @Override
                  public ResourceRole newInstance(final ModelTypeInstanceContext instanceContext) {
                    return new ResourceRoleImpl(instanceContext);
                  }
                });

    nameAttribute = typeBuilder.stringAttribute(BPMN_ATTRIBUTE_NAME).build();

    final SequenceBuilder sequenceBuilder = typeBuilder.sequence();

    resourceRefChild =
        sequenceBuilder.element(ResourceRef.class).qNameElementReference(Resource.class).build();

    resourceParameterBindingCollection =
        sequenceBuilder.elementCollection(ResourceParameterBinding.class).build();

    resourceAssignmentExpressionChild =
        sequenceBuilder.element(ResourceAssignmentExpression.class).build();

    typeBuilder.build();
  }

  @Override
  public String getName() {
    return nameAttribute.getValue(this);
  }

  @Override
  public void setName(final String name) {
    nameAttribute.setValue(this, name);
  }

  @Override
  public Resource getResource() {
    return resourceRefChild.getReferenceTargetElement(this);
  }

  @Override
  public void setResource(final Resource resource) {
    resourceRefChild.setReferenceTargetElement(this, resource);
  }

  @Override
  public Collection<ResourceParameterBinding> getResourceParameterBinding() {
    return resourceParameterBindingCollection.get(this);
  }

  @Override
  public ResourceAssignmentExpression getResourceAssignmentExpression() {
    return resourceAssignmentExpressionChild.getChild(this);
  }
}
