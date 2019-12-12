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
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.BPMN_ATTRIBUTE_IS_CLOSED;
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.BPMN_ATTRIBUTE_IS_EXECUTABLE;
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.BPMN_ATTRIBUTE_PROCESS_TYPE;
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.BPMN_ELEMENT_PROCESS;

import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.model.bpmn.ProcessType;
import io.zeebe.model.bpmn.builder.ProcessBuilder;
import io.zeebe.model.bpmn.instance.Artifact;
import io.zeebe.model.bpmn.instance.Auditing;
import io.zeebe.model.bpmn.instance.CallableElement;
import io.zeebe.model.bpmn.instance.CorrelationSubscription;
import io.zeebe.model.bpmn.instance.FlowElement;
import io.zeebe.model.bpmn.instance.LaneSet;
import io.zeebe.model.bpmn.instance.Monitoring;
import io.zeebe.model.bpmn.instance.Process;
import io.zeebe.model.bpmn.instance.Property;
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
import org.camunda.bpm.model.xml.type.reference.ElementReferenceCollection;

/**
 * The BPMN process element
 *
 * @author Daniel Meyer
 * @author Sebastian Menski
 */
public class ProcessImpl extends CallableElementImpl implements Process {

  protected static Attribute<ProcessType> processTypeAttribute;
  protected static Attribute<Boolean> isClosedAttribute;
  protected static Attribute<Boolean> isExecutableAttribute;
  // TODO: definitionalCollaborationRef
  protected static ChildElement<Auditing> auditingChild;
  protected static ChildElement<Monitoring> monitoringChild;
  protected static ChildElementCollection<Property> propertyCollection;
  protected static ChildElementCollection<LaneSet> laneSetCollection;
  protected static ChildElementCollection<FlowElement> flowElementCollection;
  protected static ChildElementCollection<Artifact> artifactCollection;
  protected static ChildElementCollection<ResourceRole> resourceRoleCollection;
  protected static ChildElementCollection<CorrelationSubscription>
      correlationSubscriptionCollection;
  protected static ElementReferenceCollection<Process, Supports> supportsCollection;

  public ProcessImpl(final ModelTypeInstanceContext context) {
    super(context);
  }

  public static void registerType(final ModelBuilder modelBuilder) {
    final ModelElementTypeBuilder typeBuilder =
        modelBuilder
            .defineType(Process.class, BPMN_ELEMENT_PROCESS)
            .namespaceUri(BPMN20_NS)
            .extendsType(CallableElement.class)
            .instanceProvider(
                new ModelTypeInstanceProvider<Process>() {
                  @Override
                  public Process newInstance(final ModelTypeInstanceContext instanceContext) {
                    return new ProcessImpl(instanceContext);
                  }
                });

    processTypeAttribute =
        typeBuilder
            .enumAttribute(BPMN_ATTRIBUTE_PROCESS_TYPE, ProcessType.class)
            .defaultValue(ProcessType.None)
            .build();

    isClosedAttribute =
        typeBuilder.booleanAttribute(BPMN_ATTRIBUTE_IS_CLOSED).defaultValue(false).build();

    isExecutableAttribute = typeBuilder.booleanAttribute(BPMN_ATTRIBUTE_IS_EXECUTABLE).build();

    // TODO: definitionalCollaborationRef

    final SequenceBuilder sequenceBuilder = typeBuilder.sequence();

    auditingChild = sequenceBuilder.element(Auditing.class).build();

    monitoringChild = sequenceBuilder.element(Monitoring.class).build();

    propertyCollection = sequenceBuilder.elementCollection(Property.class).build();

    laneSetCollection = sequenceBuilder.elementCollection(LaneSet.class).build();

    flowElementCollection = sequenceBuilder.elementCollection(FlowElement.class).build();

    artifactCollection = sequenceBuilder.elementCollection(Artifact.class).build();

    resourceRoleCollection = sequenceBuilder.elementCollection(ResourceRole.class).build();

    correlationSubscriptionCollection =
        sequenceBuilder.elementCollection(CorrelationSubscription.class).build();

    supportsCollection =
        sequenceBuilder
            .elementCollection(Supports.class)
            .qNameElementReferenceCollection(Process.class)
            .build();

    typeBuilder.build();
  }

  @Override
  public ProcessBuilder builder() {
    return new ProcessBuilder((BpmnModelInstance) modelInstance, this);
  }

  @Override
  public ProcessType getProcessType() {
    return processTypeAttribute.getValue(this);
  }

  @Override
  public void setProcessType(final ProcessType processType) {
    processTypeAttribute.setValue(this, processType);
  }

  @Override
  public boolean isClosed() {
    return isClosedAttribute.getValue(this);
  }

  @Override
  public void setClosed(final boolean closed) {
    isClosedAttribute.setValue(this, closed);
  }

  @Override
  public boolean isExecutable() {
    return isExecutableAttribute.getValue(this);
  }

  @Override
  public void setExecutable(final boolean executable) {
    isExecutableAttribute.setValue(this, executable);
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
  public Collection<Property> getProperties() {
    return propertyCollection.get(this);
  }

  @Override
  public Collection<LaneSet> getLaneSets() {
    return laneSetCollection.get(this);
  }

  @Override
  public Collection<FlowElement> getFlowElements() {
    return flowElementCollection.get(this);
  }

  @Override
  public Collection<Artifact> getArtifacts() {
    return artifactCollection.get(this);
  }

  @Override
  public Collection<CorrelationSubscription> getCorrelationSubscriptions() {
    return correlationSubscriptionCollection.get(this);
  }

  @Override
  public Collection<ResourceRole> getResourceRoles() {
    return resourceRoleCollection.get(this);
  }

  @Override
  public Collection<Process> getSupports() {
    return supportsCollection.getReferenceTargetElements(this);
  }
}
