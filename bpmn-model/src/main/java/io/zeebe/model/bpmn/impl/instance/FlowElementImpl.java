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
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.BPMN_ELEMENT_FLOW_ELEMENT;

import io.zeebe.model.bpmn.instance.Auditing;
import io.zeebe.model.bpmn.instance.BaseElement;
import io.zeebe.model.bpmn.instance.CategoryValue;
import io.zeebe.model.bpmn.instance.FlowElement;
import io.zeebe.model.bpmn.instance.Monitoring;
import java.util.Collection;
import org.camunda.bpm.model.xml.ModelBuilder;
import org.camunda.bpm.model.xml.impl.instance.ModelTypeInstanceContext;
import org.camunda.bpm.model.xml.type.ModelElementTypeBuilder;
import org.camunda.bpm.model.xml.type.attribute.Attribute;
import org.camunda.bpm.model.xml.type.child.ChildElement;
import org.camunda.bpm.model.xml.type.child.SequenceBuilder;
import org.camunda.bpm.model.xml.type.reference.ElementReferenceCollection;

/**
 * The BPMN flowElement element
 *
 * @author Daniel Meyer
 * @author Sebastian Menski
 */
public abstract class FlowElementImpl extends BaseElementImpl implements FlowElement {

  protected static Attribute<String> nameAttribute;
  protected static ChildElement<Auditing> auditingChild;
  protected static ChildElement<Monitoring> monitoringChild;
  protected static ElementReferenceCollection<CategoryValue, CategoryValueRef>
      categoryValueRefCollection;

  public FlowElementImpl(final ModelTypeInstanceContext context) {
    super(context);
  }

  public static void registerType(final ModelBuilder modelBuilder) {

    final ModelElementTypeBuilder typeBuilder =
        modelBuilder
            .defineType(FlowElement.class, BPMN_ELEMENT_FLOW_ELEMENT)
            .namespaceUri(BPMN20_NS)
            .extendsType(BaseElement.class)
            .abstractType();

    nameAttribute = typeBuilder.stringAttribute(BPMN_ATTRIBUTE_NAME).build();

    final SequenceBuilder sequenceBuilder = typeBuilder.sequence();

    auditingChild = sequenceBuilder.element(Auditing.class).build();

    monitoringChild = sequenceBuilder.element(Monitoring.class).build();

    categoryValueRefCollection =
        sequenceBuilder
            .elementCollection(CategoryValueRef.class)
            .qNameElementReferenceCollection(CategoryValue.class)
            .build();

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
  public Auditing getAuditing() {
    return auditingChild.getChild(this);
  }

  @Override
  public void setAuditing(final Auditing auditing) {
    auditingChild.setChild(this, auditing);
  }

  @Override
  public Monitoring getMonitoring() {
    return monitoringChild.getChild(this);
  }

  @Override
  public void setMonitoring(final Monitoring monitoring) {
    monitoringChild.setChild(this, monitoring);
  }

  @Override
  public Collection<CategoryValue> getCategoryValueRefs() {
    return categoryValueRefCollection.getReferenceTargetElements(this);
  }
}
