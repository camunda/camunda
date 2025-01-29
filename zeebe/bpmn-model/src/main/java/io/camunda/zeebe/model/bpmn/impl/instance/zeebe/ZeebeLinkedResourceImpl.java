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
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeBindingType;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeLinkedResource;
import org.camunda.bpm.model.xml.ModelBuilder;
import org.camunda.bpm.model.xml.impl.instance.ModelTypeInstanceContext;
import org.camunda.bpm.model.xml.type.ModelElementTypeBuilder;
import org.camunda.bpm.model.xml.type.attribute.Attribute;

public class ZeebeLinkedResourceImpl extends BpmnModelElementInstanceImpl
    implements ZeebeLinkedResource {
  private static Attribute<String> resourceIdAttribute;
  private static Attribute<ZeebeBindingType> bindingTypeAttribute;
  private static Attribute<String> resourceTypeAttribute;
  private static Attribute<String> versionTagAttribute;
  private static Attribute<String> linkNameAttribute;

  public ZeebeLinkedResourceImpl(final ModelTypeInstanceContext instanceContext) {
    super(instanceContext);
  }

  public static void registerType(final ModelBuilder modelBuilder) {
    final ModelElementTypeBuilder typeBuilder =
        modelBuilder
            .defineType(ZeebeLinkedResource.class, ZeebeConstants.ELEMENT_LINKED_RESOURCE)
            .namespaceUri(BpmnModelConstants.ZEEBE_NS)
            .instanceProvider(ZeebeLinkedResourceImpl::new);

    resourceIdAttribute =
        typeBuilder
            .stringAttribute(ZeebeConstants.ATTRIBUTE_RESOURCE_ID)
            .namespace(BpmnModelConstants.ZEEBE_NS)
            .build();

    bindingTypeAttribute =
        typeBuilder
            .enumAttribute(ZeebeConstants.ATTRIBUTE_BINDING_TYPE, ZeebeBindingType.class)
            .namespace(BpmnModelConstants.ZEEBE_NS)
            .defaultValue(ZeebeBindingType.latest)
            .build();

    resourceTypeAttribute =
        typeBuilder
            .stringAttribute(ZeebeConstants.ATTRIBUTE_RESOURCE_TYPE)
            .namespace(BpmnModelConstants.ZEEBE_NS)
            .build();

    versionTagAttribute =
        typeBuilder
            .stringAttribute(ZeebeConstants.ATTRIBUTE_VERSION_TAG)
            .namespace(BpmnModelConstants.ZEEBE_NS)
            .build();

    linkNameAttribute =
        typeBuilder
            .stringAttribute(ZeebeConstants.ATTRIBUTE_LINK_NAME)
            .namespace(BpmnModelConstants.ZEEBE_NS)
            .build();

    typeBuilder.build();
  }

  @Override
  public String getResourceId() {
    return resourceIdAttribute.getValue(this);
  }

  @Override
  public void setResourceId(final String resourceId) {
    resourceIdAttribute.setValue(this, resourceId);
  }

  @Override
  public ZeebeBindingType getBindingType() {
    return bindingTypeAttribute.getValue(this);
  }

  @Override
  public void setBindingType(final ZeebeBindingType bindingType) {
    bindingTypeAttribute.setValue(this, bindingType);
  }

  @Override
  public String getResourceType() {
    return resourceTypeAttribute.getValue(this);
  }

  @Override
  public void setResourceType(final String resourceType) {
    resourceTypeAttribute.setValue(this, resourceType);
  }

  @Override
  public String getVersionTag() {
    return versionTagAttribute.getValue(this);
  }

  @Override
  public void setVersionTag(final String versionTag) {
    versionTagAttribute.setValue(this, versionTag);
  }

  @Override
  public String getLinkName() {
    return linkNameAttribute.getValue(this);
  }

  @Override
  public void setLinkName(final String linkName) {
    linkNameAttribute.setValue(this, linkName);
  }
}
