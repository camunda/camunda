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
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeRelationship;
import org.camunda.bpm.model.xml.ModelBuilder;
import org.camunda.bpm.model.xml.impl.instance.ModelTypeInstanceContext;
import org.camunda.bpm.model.xml.type.ModelElementTypeBuilder;
import org.camunda.bpm.model.xml.type.attribute.Attribute;

public class ZeebeRelationshipImpl extends BpmnModelElementInstanceImpl
    implements ZeebeRelationship {

  protected static Attribute<String> connectionAttribute;
  protected static Attribute<String> operationAttribute;
  protected static Attribute<String> targetAttribute;
  protected static Attribute<String> changeAttribute;

  public ZeebeRelationshipImpl(final ModelTypeInstanceContext instanceContext) {
    super(instanceContext);
  }

  @Override
  public String getConnection() {
    return connectionAttribute.getValue(this);
  }

  @Override
  public void setConnection(final String connection) {
    connectionAttribute.setValue(this, connection);
  }

  @Override
  public String getOperation() {
    return operationAttribute.getValue(this);
  }

  @Override
  public void setOperation(final String operation) {
    operationAttribute.setValue(this, operation);
  }

  @Override
  public String getTarget() {
    return targetAttribute.getValue(this);
  }

  @Override
  public void setTarget(final String target) {
    targetAttribute.setValue(this, target);
  }

  @Override
  public String getChange() {
    return changeAttribute.getValue(this);
  }

  @Override
  public void setChange(final String change) {
    changeAttribute.setValue(this, change);
  }

  public static void registerType(final ModelBuilder modelBuilder) {
    final ModelElementTypeBuilder typeBuilder =
        modelBuilder
            .defineType(ZeebeRelationship.class, ZeebeConstants.ELEMENT_RELATIONSHIP)
            .namespaceUri(BpmnModelConstants.ZEEBE_NS)
            .instanceProvider(ZeebeRelationshipImpl::new);

    connectionAttribute =
        typeBuilder
            .stringAttribute(ZeebeConstants.ATTRIBUTE_CONNECTION)
            .namespace(BpmnModelConstants.ZEEBE_NS)
            .build();
    operationAttribute =
        typeBuilder
            .stringAttribute(ZeebeConstants.ATTRIBUTE_OPERATION)
            .namespace(BpmnModelConstants.ZEEBE_NS)
            .build();
    targetAttribute =
        typeBuilder
            .stringAttribute(ZeebeConstants.ATTRIBUTE_TARGET)
            .namespace(BpmnModelConstants.ZEEBE_NS)
            .build();
    changeAttribute =
        typeBuilder
            .stringAttribute(ZeebeConstants.ATTRIBUTE_CHANGE)
            .namespace(BpmnModelConstants.ZEEBE_NS)
            .build();

    typeBuilder.build();
  }
}
