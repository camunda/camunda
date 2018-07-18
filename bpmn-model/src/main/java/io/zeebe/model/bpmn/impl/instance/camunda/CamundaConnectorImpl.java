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

import static io.zeebe.model.bpmn.impl.BpmnModelConstants.CAMUNDA_ELEMENT_CONNECTOR;
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.CAMUNDA_NS;

import io.zeebe.model.bpmn.impl.instance.BpmnModelElementInstanceImpl;
import io.zeebe.model.bpmn.instance.camunda.CamundaConnector;
import io.zeebe.model.bpmn.instance.camunda.CamundaConnectorId;
import io.zeebe.model.bpmn.instance.camunda.CamundaInputOutput;
import org.camunda.bpm.model.xml.ModelBuilder;
import org.camunda.bpm.model.xml.impl.instance.ModelTypeInstanceContext;
import org.camunda.bpm.model.xml.type.ModelElementTypeBuilder;
import org.camunda.bpm.model.xml.type.ModelElementTypeBuilder.ModelTypeInstanceProvider;
import org.camunda.bpm.model.xml.type.child.ChildElement;
import org.camunda.bpm.model.xml.type.child.SequenceBuilder;

/**
 * The BPMN connector camunda extension element
 *
 * @author Sebastian Menski
 */
public class CamundaConnectorImpl extends BpmnModelElementInstanceImpl implements CamundaConnector {

  protected static ChildElement<CamundaConnectorId> camundaConnectorIdChild;
  protected static ChildElement<CamundaInputOutput> camundaInputOutputChild;

  public static void registerType(ModelBuilder modelBuilder) {
    final ModelElementTypeBuilder typeBuilder =
        modelBuilder
            .defineType(CamundaConnector.class, CAMUNDA_ELEMENT_CONNECTOR)
            .namespaceUri(CAMUNDA_NS)
            .instanceProvider(
                new ModelTypeInstanceProvider<CamundaConnector>() {
                  @Override
                  public CamundaConnector newInstance(ModelTypeInstanceContext instanceContext) {
                    return new CamundaConnectorImpl(instanceContext);
                  }
                });

    final SequenceBuilder sequenceBuilder = typeBuilder.sequence();

    camundaConnectorIdChild = sequenceBuilder.element(CamundaConnectorId.class).required().build();

    camundaInputOutputChild = sequenceBuilder.element(CamundaInputOutput.class).build();

    typeBuilder.build();
  }

  public CamundaConnectorImpl(ModelTypeInstanceContext instanceContext) {
    super(instanceContext);
  }

  @Override
  public CamundaConnectorId getCamundaConnectorId() {
    return camundaConnectorIdChild.getChild(this);
  }

  @Override
  public void setCamundaConnectorId(CamundaConnectorId camundaConnectorId) {
    camundaConnectorIdChild.setChild(this, camundaConnectorId);
  }

  @Override
  public CamundaInputOutput getCamundaInputOutput() {
    return camundaInputOutputChild.getChild(this);
  }

  @Override
  public void setCamundaInputOutput(CamundaInputOutput camundaInputOutput) {
    camundaInputOutputChild.setChild(this, camundaInputOutput);
  }
}
