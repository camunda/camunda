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

package io.zeebe.model.bpmn.impl.instance.camunda;

import static io.zeebe.model.bpmn.impl.BpmnModelConstants.CAMUNDA_ELEMENT_POTENTIAL_STARTER;
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.CAMUNDA_NS;

import io.zeebe.model.bpmn.impl.instance.BpmnModelElementInstanceImpl;
import io.zeebe.model.bpmn.instance.ResourceAssignmentExpression;
import io.zeebe.model.bpmn.instance.camunda.CamundaPotentialStarter;
import org.camunda.bpm.model.xml.ModelBuilder;
import org.camunda.bpm.model.xml.impl.instance.ModelTypeInstanceContext;
import org.camunda.bpm.model.xml.type.ModelElementTypeBuilder;
import org.camunda.bpm.model.xml.type.ModelElementTypeBuilder.ModelTypeInstanceProvider;
import org.camunda.bpm.model.xml.type.child.ChildElement;
import org.camunda.bpm.model.xml.type.child.SequenceBuilder;

/**
 * The BPMN potentialStarter camunda extension
 *
 * @author Sebastian Menski
 */
public class CamundaPotentialStarterImpl extends BpmnModelElementInstanceImpl
    implements CamundaPotentialStarter {

  protected static ChildElement<ResourceAssignmentExpression> resourceAssignmentExpressionChild;

  public static void registerType(ModelBuilder modelBuilder) {
    final ModelElementTypeBuilder typeBuilder =
        modelBuilder
            .defineType(CamundaPotentialStarter.class, CAMUNDA_ELEMENT_POTENTIAL_STARTER)
            .namespaceUri(CAMUNDA_NS)
            .instanceProvider(
                new ModelTypeInstanceProvider<CamundaPotentialStarter>() {
                  @Override
                  public CamundaPotentialStarter newInstance(
                      ModelTypeInstanceContext instanceContext) {
                    return new CamundaPotentialStarterImpl(instanceContext);
                  }
                });

    final SequenceBuilder sequenceBuilder = typeBuilder.sequence();

    resourceAssignmentExpressionChild =
        sequenceBuilder.element(ResourceAssignmentExpression.class).build();

    typeBuilder.build();
  }

  public CamundaPotentialStarterImpl(ModelTypeInstanceContext instanceContext) {
    super(instanceContext);
  }

  @Override
  public ResourceAssignmentExpression getResourceAssignmentExpression() {
    return resourceAssignmentExpressionChild.getChild(this);
  }

  @Override
  public void setResourceAssignmentExpression(
      ResourceAssignmentExpression resourceAssignmentExpression) {
    resourceAssignmentExpressionChild.setChild(this, resourceAssignmentExpression);
  }
}
