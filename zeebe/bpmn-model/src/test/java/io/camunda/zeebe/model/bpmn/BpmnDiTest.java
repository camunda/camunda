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

package io.camunda.zeebe.model.bpmn;

import static io.camunda.zeebe.model.bpmn.BpmnTestConstants.ASSOCIATION_ID;
import static io.camunda.zeebe.model.bpmn.BpmnTestConstants.COLLABORATION_ID;
import static io.camunda.zeebe.model.bpmn.BpmnTestConstants.DATA_INPUT_ASSOCIATION_ID;
import static io.camunda.zeebe.model.bpmn.BpmnTestConstants.END_EVENT_ID;
import static io.camunda.zeebe.model.bpmn.BpmnTestConstants.EXCLUSIVE_GATEWAY;
import static io.camunda.zeebe.model.bpmn.BpmnTestConstants.MESSAGE_FLOW_ID;
import static io.camunda.zeebe.model.bpmn.BpmnTestConstants.PARTICIPANT_ID;
import static io.camunda.zeebe.model.bpmn.BpmnTestConstants.PROCESS_ID;
import static io.camunda.zeebe.model.bpmn.BpmnTestConstants.SEQUENCE_FLOW_ID;
import static io.camunda.zeebe.model.bpmn.BpmnTestConstants.SERVICE_TASK_ID;
import static io.camunda.zeebe.model.bpmn.BpmnTestConstants.START_EVENT_ID;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.model.bpmn.instance.Association;
import io.camunda.zeebe.model.bpmn.instance.Collaboration;
import io.camunda.zeebe.model.bpmn.instance.DataInputAssociation;
import io.camunda.zeebe.model.bpmn.instance.EndEvent;
import io.camunda.zeebe.model.bpmn.instance.ExclusiveGateway;
import io.camunda.zeebe.model.bpmn.instance.MessageFlow;
import io.camunda.zeebe.model.bpmn.instance.Participant;
import io.camunda.zeebe.model.bpmn.instance.Process;
import io.camunda.zeebe.model.bpmn.instance.SequenceFlow;
import io.camunda.zeebe.model.bpmn.instance.ServiceTask;
import io.camunda.zeebe.model.bpmn.instance.StartEvent;
import io.camunda.zeebe.model.bpmn.instance.bpmndi.BpmnDiagram;
import io.camunda.zeebe.model.bpmn.instance.bpmndi.BpmnEdge;
import io.camunda.zeebe.model.bpmn.instance.bpmndi.BpmnLabel;
import io.camunda.zeebe.model.bpmn.instance.bpmndi.BpmnLabelStyle;
import io.camunda.zeebe.model.bpmn.instance.bpmndi.BpmnPlane;
import io.camunda.zeebe.model.bpmn.instance.bpmndi.BpmnShape;
import io.camunda.zeebe.model.bpmn.instance.dc.Bounds;
import io.camunda.zeebe.model.bpmn.instance.dc.Font;
import io.camunda.zeebe.model.bpmn.instance.di.DiagramElement;
import io.camunda.zeebe.model.bpmn.instance.di.Waypoint;
import java.util.Collection;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * @author Sebastian Menski
 */
public class BpmnDiTest {

  private BpmnModelInstance modelInstance;
  private Collaboration collaboration;
  private Participant participant;
  private Process process;
  private StartEvent startEvent;
  private ServiceTask serviceTask;
  private ExclusiveGateway exclusiveGateway;
  private SequenceFlow sequenceFlow;
  private MessageFlow messageFlow;
  private DataInputAssociation dataInputAssociation;
  private Association association;
  private EndEvent endEvent;

  @Before
  public void parseModel() {
    modelInstance =
        Bpmn.readModelFromStream(
            getClass().getResourceAsStream(getClass().getSimpleName() + ".xml"));
    collaboration = modelInstance.getModelElementById(COLLABORATION_ID);
    participant = modelInstance.getModelElementById(PARTICIPANT_ID + 1);
    process = modelInstance.getModelElementById(PROCESS_ID + 1);
    serviceTask = modelInstance.getModelElementById(SERVICE_TASK_ID);
    exclusiveGateway = modelInstance.getModelElementById(EXCLUSIVE_GATEWAY);
    startEvent = modelInstance.getModelElementById(START_EVENT_ID + 2);
    sequenceFlow = modelInstance.getModelElementById(SEQUENCE_FLOW_ID + 3);
    messageFlow = modelInstance.getModelElementById(MESSAGE_FLOW_ID);
    dataInputAssociation = modelInstance.getModelElementById(DATA_INPUT_ASSOCIATION_ID);
    association = modelInstance.getModelElementById(ASSOCIATION_ID);
    endEvent = modelInstance.getModelElementById(END_EVENT_ID + 2);
  }

  @Test
  public void testBpmnDiagram() {
    final Collection<BpmnDiagram> diagrams =
        modelInstance.getModelElementsByType(BpmnDiagram.class);
    assertThat(diagrams).hasSize(1);
    final BpmnDiagram diagram = diagrams.iterator().next();
    assertThat(diagram.getBpmnPlane()).isNotNull();
    assertThat(diagram.getBpmnPlane().getBpmnElement()).isEqualTo(collaboration);
    assertThat(diagram.getBpmnLabelStyles()).hasSize(1);
  }

  @Test
  public void testBpmnPane() {
    final DiagramElement diagramElement = collaboration.getDiagramElement();
    assertThat(diagramElement).isNotNull().isInstanceOf(BpmnPlane.class);
    final BpmnPlane bpmnPlane = (BpmnPlane) diagramElement;
    assertThat(bpmnPlane.getBpmnElement()).isEqualTo(collaboration);
    assertThat(bpmnPlane.getChildElementsByType(DiagramElement.class)).isNotEmpty();
  }

  @Test
  public void testBpmnLabelStyle() {
    final BpmnLabelStyle labelStyle =
        modelInstance.getModelElementsByType(BpmnLabelStyle.class).iterator().next();
    final Font font = labelStyle.getFont();
    assertThat(font).isNotNull();
    assertThat(font.getName()).isEqualTo("Arial");
    assertThat(font.getSize()).isEqualTo(8.0);
    assertThat(font.isBold()).isTrue();
    assertThat(font.isItalic()).isFalse();
    assertThat(font.isStrikeThrough()).isFalse();
    assertThat(font.isUnderline()).isFalse();
  }

  @Test
  public void testBpmnShape() {
    final BpmnShape shape = serviceTask.getDiagramElement();
    assertThat(shape.getBpmnElement()).isEqualTo(serviceTask);
    assertThat(shape.getBpmnLabel()).isNull();
    assertThat(shape.isExpanded()).isFalse();
    assertThat(shape.isHorizontal()).isFalse();
    assertThat(shape.isMarkerVisible()).isFalse();
    assertThat(shape.isMessageVisible()).isFalse();
    assertThat(shape.getParticipantBandKind()).isNull();
    assertThat(shape.getChoreographyActivityShape()).isNull();
  }

  @Test
  public void testBpmnLabel() {
    final BpmnShape shape = startEvent.getDiagramElement();
    assertThat(shape.getBpmnElement()).isEqualTo(startEvent);
    assertThat(shape.getBpmnLabel()).isNotNull();

    final BpmnLabel label = shape.getBpmnLabel();
    assertThat(label.getLabelStyle()).isNull();
    assertThat(label.getBounds()).isNotNull();
  }

  @Test
  public void testBpmnEdge() {
    final BpmnEdge edge = sequenceFlow.getDiagramElement();
    assertThat(edge.getBpmnElement()).isEqualTo(sequenceFlow);
    assertThat(edge.getBpmnLabel()).isNull();
    assertThat(edge.getMessageVisibleKind()).isNull();
    assertThat(edge.getSourceElement()).isInstanceOf(BpmnShape.class);
    assertThat(((BpmnShape) edge.getSourceElement()).getBpmnElement()).isEqualTo(startEvent);
    assertThat(edge.getTargetElement()).isInstanceOf(BpmnShape.class);
    assertThat(((BpmnShape) edge.getTargetElement()).getBpmnElement()).isEqualTo(endEvent);
  }

  @Test
  public void testDiagramElementTypes() {
    assertThat(collaboration.getDiagramElement()).isInstanceOf(BpmnPlane.class);
    assertThat(process.getDiagramElement()).isNull();
    assertThat(participant.getDiagramElement()).isInstanceOf(BpmnShape.class);
    assertThat(participant.getDiagramElement()).isInstanceOf(BpmnShape.class);
    assertThat(startEvent.getDiagramElement()).isInstanceOf(BpmnShape.class);
    assertThat(serviceTask.getDiagramElement()).isInstanceOf(BpmnShape.class);
    assertThat(exclusiveGateway.getDiagramElement()).isInstanceOf(BpmnShape.class);
    assertThat(endEvent.getDiagramElement()).isInstanceOf(BpmnShape.class);
    assertThat(sequenceFlow.getDiagramElement()).isInstanceOf(BpmnEdge.class);
    assertThat(messageFlow.getDiagramElement()).isInstanceOf(BpmnEdge.class);
    assertThat(dataInputAssociation.getDiagramElement()).isInstanceOf(BpmnEdge.class);
    assertThat(association.getDiagramElement()).isInstanceOf(BpmnEdge.class);
  }

  @Test
  public void shouldNotRemoveBpmElementReference() {
    assertThat(startEvent.getOutgoing()).contains(sequenceFlow);
    assertThat(endEvent.getIncoming()).contains(sequenceFlow);

    final BpmnEdge edge = sequenceFlow.getDiagramElement();
    assertThat(edge.getBpmnElement()).isEqualTo(sequenceFlow);

    startEvent.getOutgoing().remove(sequenceFlow);
    endEvent.getIncoming().remove(sequenceFlow);

    assertThat(startEvent.getOutgoing()).doesNotContain(sequenceFlow);
    assertThat(endEvent.getIncoming()).doesNotContain(sequenceFlow);

    assertThat(edge.getBpmnElement()).isEqualTo(sequenceFlow);
  }

  @Test
  public void shouldCreateValidBpmnDi() {
    modelInstance =
        Bpmn.createProcess("process")
            .startEvent("start")
            .sequenceFlowId("flow")
            .endEvent("end")
            .done();

    process = modelInstance.getModelElementById("process");
    startEvent = modelInstance.getModelElementById("start");
    sequenceFlow = modelInstance.getModelElementById("flow");
    endEvent = modelInstance.getModelElementById("end");

    // create bpmn diagram
    final BpmnDiagram bpmnDiagram = modelInstance.newInstance(BpmnDiagram.class);
    bpmnDiagram.setId("diagram");
    bpmnDiagram.setName("diagram");
    bpmnDiagram.setDocumentation("bpmn diagram element");
    bpmnDiagram.setResolution(120.0);
    modelInstance.getDefinitions().addChildElement(bpmnDiagram);

    // create plane for process
    final BpmnPlane processPlane = modelInstance.newInstance(BpmnPlane.class);
    processPlane.setId("plane");
    processPlane.setBpmnElement(process);
    bpmnDiagram.setBpmnPlane(processPlane);

    // create shape for start event
    final BpmnShape startEventShape = modelInstance.newInstance(BpmnShape.class);
    startEventShape.setId("startShape");
    startEventShape.setBpmnElement(startEvent);
    processPlane.getDiagramElements().add(startEventShape);

    // create bounds for start event shape
    final Bounds startEventBounds = modelInstance.newInstance(Bounds.class);
    startEventBounds.setHeight(36.0);
    startEventBounds.setWidth(36.0);
    startEventBounds.setX(632.0);
    startEventBounds.setY(312.0);
    startEventShape.setBounds(startEventBounds);

    // create shape for end event
    final BpmnShape endEventShape = modelInstance.newInstance(BpmnShape.class);
    endEventShape.setId("endShape");
    endEventShape.setBpmnElement(endEvent);
    processPlane.getDiagramElements().add(endEventShape);

    // create bounds for end event shape
    final Bounds endEventBounds = modelInstance.newInstance(Bounds.class);
    endEventBounds.setHeight(36.0);
    endEventBounds.setWidth(36.0);
    endEventBounds.setX(718.0);
    endEventBounds.setY(312.0);
    endEventShape.setBounds(endEventBounds);

    // create edge for sequence flow
    final BpmnEdge flowEdge = modelInstance.newInstance(BpmnEdge.class);
    flowEdge.setId("flowEdge");
    flowEdge.setBpmnElement(sequenceFlow);
    flowEdge.setSourceElement(startEventShape);
    flowEdge.setTargetElement(endEventShape);
    processPlane.getDiagramElements().add(flowEdge);

    // create waypoints for sequence flow edge
    final Waypoint startWaypoint = modelInstance.newInstance(Waypoint.class);
    startWaypoint.setX(668.0);
    startWaypoint.setY(330.0);
    flowEdge.getWaypoints().add(startWaypoint);

    final Waypoint endWaypoint = modelInstance.newInstance(Waypoint.class);
    endWaypoint.setX(718.0);
    endWaypoint.setY(330.0);
    flowEdge.getWaypoints().add(endWaypoint);
  }

  @After
  public void validateModel() {
    Bpmn.validateModel(modelInstance);
  }
}
