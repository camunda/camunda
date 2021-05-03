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
package io.camunda.zeebe.model.bpmn.builder.di;

import static io.camunda.zeebe.model.bpmn.BpmnTestConstants.END_EVENT_ID;
import static io.camunda.zeebe.model.bpmn.BpmnTestConstants.SEND_TASK_ID;
import static io.camunda.zeebe.model.bpmn.BpmnTestConstants.SEQUENCE_FLOW_ID;
import static io.camunda.zeebe.model.bpmn.BpmnTestConstants.SERVICE_TASK_ID;
import static io.camunda.zeebe.model.bpmn.BpmnTestConstants.START_EVENT_ID;
import static io.camunda.zeebe.model.bpmn.BpmnTestConstants.SUB_PROCESS_ID;
import static io.camunda.zeebe.model.bpmn.BpmnTestConstants.TASK_ID;
import static io.camunda.zeebe.model.bpmn.BpmnTestConstants.USER_TASK_ID;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.builder.ProcessBuilder;
import io.camunda.zeebe.model.bpmn.instance.bpmndi.BpmnEdge;
import io.camunda.zeebe.model.bpmn.instance.bpmndi.BpmnShape;
import io.camunda.zeebe.model.bpmn.instance.dc.Bounds;
import io.camunda.zeebe.model.bpmn.instance.di.Waypoint;
import java.util.Collection;
import java.util.Iterator;
import org.junit.Test;

public class CoordinatesGenerationTest {

  private BpmnModelInstance instance;

  @Test
  public void shouldPlaceStartEvent() {

    final ProcessBuilder builder = Bpmn.createExecutableProcess();

    instance = builder.startEvent(START_EVENT_ID).done();

    final Bounds startBounds = findBpmnShape(START_EVENT_ID).getBounds();
    assertShapeCoordinates(startBounds, 100, 100);
  }

  @Test
  public void shouldPlaceUserTask() {

    final ProcessBuilder builder = Bpmn.createExecutableProcess();

    instance =
        builder
            .startEvent(START_EVENT_ID)
            .sequenceFlowId(SEQUENCE_FLOW_ID)
            .userTask(USER_TASK_ID)
            .done();

    final Bounds userTaskBounds = findBpmnShape(USER_TASK_ID).getBounds();
    assertShapeCoordinates(userTaskBounds, 186, 78);

    final Collection<Waypoint> sequenceFlowWaypoints =
        findBpmnEdge(SEQUENCE_FLOW_ID).getWaypoints();
    final Iterator<Waypoint> iterator = sequenceFlowWaypoints.iterator();

    Waypoint waypoint = iterator.next();
    assertWaypointCoordinates(waypoint, 136, 118);

    while (iterator.hasNext()) {
      waypoint = iterator.next();
    }

    assertWaypointCoordinates(waypoint, 186, 118);
  }

  @Test
  public void shouldPlaceSendTask() {

    final ProcessBuilder builder = Bpmn.createExecutableProcess();

    instance =
        builder
            .startEvent(START_EVENT_ID)
            .sequenceFlowId(SEQUENCE_FLOW_ID)
            .sendTask(SEND_TASK_ID)
            .done();

    final Bounds sendTaskBounds = findBpmnShape(SEND_TASK_ID).getBounds();
    assertShapeCoordinates(sendTaskBounds, 186, 78);

    final Collection<Waypoint> sequenceFlowWaypoints =
        findBpmnEdge(SEQUENCE_FLOW_ID).getWaypoints();
    final Iterator<Waypoint> iterator = sequenceFlowWaypoints.iterator();

    Waypoint waypoint = iterator.next();
    assertWaypointCoordinates(waypoint, 136, 118);

    while (iterator.hasNext()) {
      waypoint = iterator.next();
    }

    assertWaypointCoordinates(waypoint, 186, 118);
  }

  @Test
  public void shouldPlaceServiceTask() {

    final ProcessBuilder builder = Bpmn.createExecutableProcess();

    instance =
        builder
            .startEvent(START_EVENT_ID)
            .sequenceFlowId(SEQUENCE_FLOW_ID)
            .serviceTask(SERVICE_TASK_ID)
            .done();

    final Bounds serviceTaskBounds = findBpmnShape(SERVICE_TASK_ID).getBounds();
    assertShapeCoordinates(serviceTaskBounds, 186, 78);

    final Collection<Waypoint> sequenceFlowWaypoints =
        findBpmnEdge(SEQUENCE_FLOW_ID).getWaypoints();
    final Iterator<Waypoint> iterator = sequenceFlowWaypoints.iterator();

    Waypoint waypoint = iterator.next();
    assertWaypointCoordinates(waypoint, 136, 118);

    while (iterator.hasNext()) {
      waypoint = iterator.next();
    }

    assertWaypointCoordinates(waypoint, 186, 118);
  }

  @Test
  public void shouldPlaceReceiveTask() {

    final ProcessBuilder builder = Bpmn.createExecutableProcess();

    instance =
        builder
            .startEvent(START_EVENT_ID)
            .sequenceFlowId(SEQUENCE_FLOW_ID)
            .receiveTask(TASK_ID)
            .done();

    final Bounds receiveTaskBounds = findBpmnShape(TASK_ID).getBounds();
    assertShapeCoordinates(receiveTaskBounds, 186, 78);

    final Collection<Waypoint> sequenceFlowWaypoints =
        findBpmnEdge(SEQUENCE_FLOW_ID).getWaypoints();
    final Iterator<Waypoint> iterator = sequenceFlowWaypoints.iterator();

    Waypoint waypoint = iterator.next();
    assertWaypointCoordinates(waypoint, 136, 118);

    while (iterator.hasNext()) {
      waypoint = iterator.next();
    }

    assertWaypointCoordinates(waypoint, 186, 118);
  }

  @Test
  public void shouldPlaceManualTask() {

    final ProcessBuilder builder = Bpmn.createExecutableProcess();

    instance =
        builder
            .startEvent(START_EVENT_ID)
            .sequenceFlowId(SEQUENCE_FLOW_ID)
            .manualTask(TASK_ID)
            .done();

    final Bounds manualTaskBounds = findBpmnShape(TASK_ID).getBounds();
    assertShapeCoordinates(manualTaskBounds, 186, 78);

    final Collection<Waypoint> sequenceFlowWaypoints =
        findBpmnEdge(SEQUENCE_FLOW_ID).getWaypoints();
    final Iterator<Waypoint> iterator = sequenceFlowWaypoints.iterator();

    Waypoint waypoint = iterator.next();
    assertWaypointCoordinates(waypoint, 136, 118);

    while (iterator.hasNext()) {
      waypoint = iterator.next();
    }

    assertWaypointCoordinates(waypoint, 186, 118);
  }

  @Test
  public void shouldPlaceBusinessRuleTask() {

    final ProcessBuilder builder = Bpmn.createExecutableProcess();

    instance =
        builder
            .startEvent(START_EVENT_ID)
            .sequenceFlowId(SEQUENCE_FLOW_ID)
            .businessRuleTask(TASK_ID)
            .done();

    final Bounds businessRuleTaskBounds = findBpmnShape(TASK_ID).getBounds();
    assertShapeCoordinates(businessRuleTaskBounds, 186, 78);

    final Collection<Waypoint> sequenceFlowWaypoints =
        findBpmnEdge(SEQUENCE_FLOW_ID).getWaypoints();
    final Iterator<Waypoint> iterator = sequenceFlowWaypoints.iterator();

    Waypoint waypoint = iterator.next();
    assertWaypointCoordinates(waypoint, 136, 118);

    while (iterator.hasNext()) {
      waypoint = iterator.next();
    }

    assertWaypointCoordinates(waypoint, 186, 118);
  }

  @Test
  public void shouldPlaceScriptTask() {

    final ProcessBuilder builder = Bpmn.createExecutableProcess();

    instance =
        builder
            .startEvent(START_EVENT_ID)
            .sequenceFlowId(SEQUENCE_FLOW_ID)
            .scriptTask(TASK_ID)
            .done();

    final Bounds scriptTaskBounds = findBpmnShape(TASK_ID).getBounds();
    assertShapeCoordinates(scriptTaskBounds, 186, 78);

    final Collection<Waypoint> sequenceFlowWaypoints =
        findBpmnEdge(SEQUENCE_FLOW_ID).getWaypoints();
    final Iterator<Waypoint> iterator = sequenceFlowWaypoints.iterator();

    Waypoint waypoint = iterator.next();
    assertWaypointCoordinates(waypoint, 136, 118);

    while (iterator.hasNext()) {
      waypoint = iterator.next();
    }

    assertWaypointCoordinates(waypoint, 186, 118);
  }

  @Test
  public void shouldPlaceCatchingIntermediateEvent() {

    final ProcessBuilder builder = Bpmn.createExecutableProcess();

    instance =
        builder
            .startEvent(START_EVENT_ID)
            .sequenceFlowId(SEQUENCE_FLOW_ID)
            .intermediateCatchEvent("id")
            .done();

    final Bounds catchEventBounds = findBpmnShape("id").getBounds();
    assertShapeCoordinates(catchEventBounds, 186, 100);

    final Collection<Waypoint> sequenceFlowWaypoints =
        findBpmnEdge(SEQUENCE_FLOW_ID).getWaypoints();
    final Iterator<Waypoint> iterator = sequenceFlowWaypoints.iterator();

    Waypoint waypoint = iterator.next();
    assertWaypointCoordinates(waypoint, 136, 118);

    while (iterator.hasNext()) {
      waypoint = iterator.next();
    }

    assertWaypointCoordinates(waypoint, 186, 118);
  }

  @Test
  public void shouldPlaceThrowingIntermediateEvent() {

    final ProcessBuilder builder = Bpmn.createExecutableProcess();

    instance =
        builder
            .startEvent(START_EVENT_ID)
            .sequenceFlowId(SEQUENCE_FLOW_ID)
            .intermediateThrowEvent("id")
            .done();

    final Bounds throwEventBounds = findBpmnShape("id").getBounds();
    assertShapeCoordinates(throwEventBounds, 186, 100);

    final Collection<Waypoint> sequenceFlowWaypoints =
        findBpmnEdge(SEQUENCE_FLOW_ID).getWaypoints();
    final Iterator<Waypoint> iterator = sequenceFlowWaypoints.iterator();

    Waypoint waypoint = iterator.next();
    assertWaypointCoordinates(waypoint, 136, 118);

    while (iterator.hasNext()) {
      waypoint = iterator.next();
    }

    assertWaypointCoordinates(waypoint, 186, 118);
  }

  @Test
  public void shouldPlaceEndEvent() {

    final ProcessBuilder builder = Bpmn.createExecutableProcess();

    instance =
        builder
            .startEvent(START_EVENT_ID)
            .sequenceFlowId(SEQUENCE_FLOW_ID)
            .endEvent(END_EVENT_ID)
            .done();

    final Bounds endEventBounds = findBpmnShape(END_EVENT_ID).getBounds();
    assertShapeCoordinates(endEventBounds, 186, 100);

    final Collection<Waypoint> sequenceFlowWaypoints =
        findBpmnEdge(SEQUENCE_FLOW_ID).getWaypoints();
    final Iterator<Waypoint> iterator = sequenceFlowWaypoints.iterator();

    Waypoint waypoint = iterator.next();
    assertWaypointCoordinates(waypoint, 136, 118);

    while (iterator.hasNext()) {
      waypoint = iterator.next();
    }

    assertWaypointCoordinates(waypoint, 186, 118);
  }

  @Test
  public void shouldPlaceCallActivity() {

    final ProcessBuilder builder = Bpmn.createExecutableProcess();

    instance =
        builder
            .startEvent(START_EVENT_ID)
            .sequenceFlowId(SEQUENCE_FLOW_ID)
            .callActivity("id")
            .done();

    final Bounds callActivityBounds = findBpmnShape("id").getBounds();
    assertShapeCoordinates(callActivityBounds, 186, 78);

    final Collection<Waypoint> sequenceFlowWaypoints =
        findBpmnEdge(SEQUENCE_FLOW_ID).getWaypoints();
    final Iterator<Waypoint> iterator = sequenceFlowWaypoints.iterator();

    Waypoint waypoint = iterator.next();
    assertWaypointCoordinates(waypoint, 136, 118);

    while (iterator.hasNext()) {
      waypoint = iterator.next();
    }

    assertWaypointCoordinates(waypoint, 186, 118);
  }

  @Test
  public void shouldPlaceExclusiveGateway() {

    final ProcessBuilder builder = Bpmn.createExecutableProcess();

    instance =
        builder
            .startEvent(START_EVENT_ID)
            .sequenceFlowId(SEQUENCE_FLOW_ID)
            .exclusiveGateway("id")
            .done();

    final Bounds gatewayBounds = findBpmnShape("id").getBounds();
    assertShapeCoordinates(gatewayBounds, 186, 93);

    final Collection<Waypoint> sequenceFlowWaypoints =
        findBpmnEdge(SEQUENCE_FLOW_ID).getWaypoints();
    final Iterator<Waypoint> iterator = sequenceFlowWaypoints.iterator();

    Waypoint waypoint = iterator.next();
    assertWaypointCoordinates(waypoint, 136, 118);

    while (iterator.hasNext()) {
      waypoint = iterator.next();
    }

    assertWaypointCoordinates(waypoint, 186, 118);
  }

  @Test
  public void shouldPlaceInclusiveGateway() {

    final ProcessBuilder builder = Bpmn.createExecutableProcess();

    instance =
        builder
            .startEvent(START_EVENT_ID)
            .sequenceFlowId(SEQUENCE_FLOW_ID)
            .inclusiveGateway("id")
            .done();

    final Bounds gatewayBounds = findBpmnShape("id").getBounds();
    assertShapeCoordinates(gatewayBounds, 186, 93);

    final Collection<Waypoint> sequenceFlowWaypoints =
        findBpmnEdge(SEQUENCE_FLOW_ID).getWaypoints();
    final Iterator<Waypoint> iterator = sequenceFlowWaypoints.iterator();

    Waypoint waypoint = iterator.next();
    assertWaypointCoordinates(waypoint, 136, 118);

    while (iterator.hasNext()) {
      waypoint = iterator.next();
    }

    assertWaypointCoordinates(waypoint, 186, 118);
  }

  @Test
  public void shouldPlaceParallelGateway() {

    final ProcessBuilder builder = Bpmn.createExecutableProcess();

    instance =
        builder
            .startEvent(START_EVENT_ID)
            .sequenceFlowId(SEQUENCE_FLOW_ID)
            .parallelGateway("id")
            .done();

    final Bounds gatewayBounds = findBpmnShape("id").getBounds();
    assertShapeCoordinates(gatewayBounds, 186, 93);

    final Collection<Waypoint> sequenceFlowWaypoints =
        findBpmnEdge(SEQUENCE_FLOW_ID).getWaypoints();
    final Iterator<Waypoint> iterator = sequenceFlowWaypoints.iterator();

    Waypoint waypoint = iterator.next();
    assertWaypointCoordinates(waypoint, 136, 118);

    while (iterator.hasNext()) {
      waypoint = iterator.next();
    }

    assertWaypointCoordinates(waypoint, 186, 118);
  }

  @Test
  public void shouldPlaceEventBasedGateway() {

    final ProcessBuilder builder = Bpmn.createExecutableProcess();

    instance =
        builder
            .startEvent(START_EVENT_ID)
            .sequenceFlowId(SEQUENCE_FLOW_ID)
            .eventBasedGateway()
            .id("id")
            .done();

    final Bounds gatewayBounds = findBpmnShape("id").getBounds();
    assertShapeCoordinates(gatewayBounds, 186, 93);

    final Collection<Waypoint> sequenceFlowWaypoints =
        findBpmnEdge(SEQUENCE_FLOW_ID).getWaypoints();
    final Iterator<Waypoint> iterator = sequenceFlowWaypoints.iterator();

    Waypoint waypoint = iterator.next();
    assertWaypointCoordinates(waypoint, 136, 118);

    while (iterator.hasNext()) {
      waypoint = iterator.next();
    }

    assertWaypointCoordinates(waypoint, 186, 118);
  }

  @Test
  public void shouldPlaceBlankSubProcess() {

    final ProcessBuilder builder = Bpmn.createExecutableProcess();

    instance =
        builder
            .startEvent(START_EVENT_ID)
            .sequenceFlowId(SEQUENCE_FLOW_ID)
            .subProcess(SUB_PROCESS_ID)
            .done();

    final Bounds subProcessBounds = findBpmnShape(SUB_PROCESS_ID).getBounds();
    assertShapeCoordinates(subProcessBounds, 186, 18);

    final Collection<Waypoint> sequenceFlowWaypoints =
        findBpmnEdge(SEQUENCE_FLOW_ID).getWaypoints();
    final Iterator<Waypoint> iterator = sequenceFlowWaypoints.iterator();

    Waypoint waypoint = iterator.next();
    assertWaypointCoordinates(waypoint, 136, 118);

    while (iterator.hasNext()) {
      waypoint = iterator.next();
    }

    assertWaypointCoordinates(waypoint, 186, 118);
  }

  @Test
  public void shouldPlaceBoundaryEventForTask() {
    final ProcessBuilder builder = Bpmn.createExecutableProcess();

    instance =
        builder
            .startEvent(START_EVENT_ID)
            .userTask(USER_TASK_ID)
            .boundaryEvent("boundary")
            .sequenceFlowId(SEQUENCE_FLOW_ID)
            .endEvent(END_EVENT_ID)
            .moveToActivity(USER_TASK_ID)
            .endEvent()
            .done();

    final Bounds boundaryEventBounds = findBpmnShape("boundary").getBounds();
    assertShapeCoordinates(boundaryEventBounds, 218, 140);
  }

  @Test
  public void shouldPlaceFollowingFlowNodeProperlyForTask() {
    final ProcessBuilder builder = Bpmn.createExecutableProcess();

    instance =
        builder
            .startEvent(START_EVENT_ID)
            .userTask(USER_TASK_ID)
            .boundaryEvent("boundary")
            .sequenceFlowId(SEQUENCE_FLOW_ID)
            .endEvent(END_EVENT_ID)
            .moveToActivity(USER_TASK_ID)
            .endEvent()
            .done();

    final Bounds endEventBounds = findBpmnShape(END_EVENT_ID).getBounds();
    assertShapeCoordinates(endEventBounds, 266.5, 208);

    final Collection<Waypoint> sequenceFlowWaypoints =
        findBpmnEdge(SEQUENCE_FLOW_ID).getWaypoints();
    final Iterator<Waypoint> iterator = sequenceFlowWaypoints.iterator();

    Waypoint waypoint = iterator.next();
    assertWaypointCoordinates(waypoint, 236, 176);

    while (iterator.hasNext()) {
      waypoint = iterator.next();
    }

    assertWaypointCoordinates(waypoint, 266.5, 226);
  }

  @Test
  public void shouldPlaceTwoBoundaryEventsForTask() {
    final ProcessBuilder builder = Bpmn.createExecutableProcess();

    instance =
        builder
            .startEvent(START_EVENT_ID)
            .userTask(USER_TASK_ID)
            .boundaryEvent("boundary1")
            .sequenceFlowId(SEQUENCE_FLOW_ID)
            .endEvent(END_EVENT_ID)
            .moveToActivity(USER_TASK_ID)
            .endEvent()
            .moveToActivity(USER_TASK_ID)
            .boundaryEvent("boundary2")
            .done();

    final Bounds boundaryEvent1Bounds = findBpmnShape("boundary1").getBounds();
    assertShapeCoordinates(boundaryEvent1Bounds, 218, 140);

    final Bounds boundaryEvent2Bounds = findBpmnShape("boundary2").getBounds();
    assertShapeCoordinates(boundaryEvent2Bounds, 254, 140);
  }

  @Test
  public void shouldPlaceThreeBoundaryEventsForTask() {
    final ProcessBuilder builder = Bpmn.createExecutableProcess();

    instance =
        builder
            .startEvent(START_EVENT_ID)
            .userTask(USER_TASK_ID)
            .boundaryEvent("boundary1")
            .sequenceFlowId(SEQUENCE_FLOW_ID)
            .endEvent(END_EVENT_ID)
            .moveToActivity(USER_TASK_ID)
            .endEvent()
            .moveToActivity(USER_TASK_ID)
            .boundaryEvent("boundary2")
            .moveToActivity(USER_TASK_ID)
            .boundaryEvent("boundary3")
            .done();

    final Bounds boundaryEvent1Bounds = findBpmnShape("boundary1").getBounds();
    assertShapeCoordinates(boundaryEvent1Bounds, 218, 140);

    final Bounds boundaryEvent2Bounds = findBpmnShape("boundary2").getBounds();
    assertShapeCoordinates(boundaryEvent2Bounds, 254, 140);

    final Bounds boundaryEvent3Bounds = findBpmnShape("boundary3").getBounds();
    assertShapeCoordinates(boundaryEvent3Bounds, 182, 140);
  }

  @Test
  public void shouldPlaceManyBoundaryEventsForTask() {
    final ProcessBuilder builder = Bpmn.createExecutableProcess();

    instance =
        builder
            .startEvent(START_EVENT_ID)
            .userTask(USER_TASK_ID)
            .boundaryEvent("boundary1")
            .sequenceFlowId(SEQUENCE_FLOW_ID)
            .endEvent(END_EVENT_ID)
            .moveToActivity(USER_TASK_ID)
            .endEvent()
            .moveToActivity(USER_TASK_ID)
            .boundaryEvent("boundary2")
            .moveToActivity(USER_TASK_ID)
            .boundaryEvent("boundary3")
            .moveToActivity(USER_TASK_ID)
            .boundaryEvent("boundary4")
            .done();

    final Bounds boundaryEvent1Bounds = findBpmnShape("boundary1").getBounds();
    assertShapeCoordinates(boundaryEvent1Bounds, 218, 140);

    final Bounds boundaryEvent2Bounds = findBpmnShape("boundary2").getBounds();
    assertShapeCoordinates(boundaryEvent2Bounds, 254, 140);

    final Bounds boundaryEvent3Bounds = findBpmnShape("boundary3").getBounds();
    assertShapeCoordinates(boundaryEvent3Bounds, 182, 140);

    final Bounds boundaryEvent4Bounds = findBpmnShape("boundary4").getBounds();
    assertShapeCoordinates(boundaryEvent4Bounds, 218, 140);
  }

  @Test
  public void shouldPlaceBoundaryEventForSubProcess() {
    final ProcessBuilder builder = Bpmn.createExecutableProcess();

    instance =
        builder
            .startEvent(START_EVENT_ID)
            .subProcess(SUB_PROCESS_ID)
            .boundaryEvent("boundary")
            .sequenceFlowId(SEQUENCE_FLOW_ID)
            .endEvent(END_EVENT_ID)
            .moveToActivity(SUB_PROCESS_ID)
            .endEvent()
            .done();

    final Bounds boundaryEventBounds = findBpmnShape("boundary").getBounds();
    assertShapeCoordinates(boundaryEventBounds, 343, 200);
  }

  @Test
  public void shouldPlaceFollowingFlowNodeForSubProcess() {
    final ProcessBuilder builder = Bpmn.createExecutableProcess();

    instance =
        builder
            .startEvent(START_EVENT_ID)
            .subProcess(SUB_PROCESS_ID)
            .boundaryEvent("boundary")
            .sequenceFlowId(SEQUENCE_FLOW_ID)
            .endEvent(END_EVENT_ID)
            .moveToActivity(SUB_PROCESS_ID)
            .endEvent()
            .done();

    final Bounds endEventBounds = findBpmnShape(END_EVENT_ID).getBounds();
    assertShapeCoordinates(endEventBounds, 391.5, 268);

    final Collection<Waypoint> sequenceFlowWaypoints =
        findBpmnEdge(SEQUENCE_FLOW_ID).getWaypoints();
    final Iterator<Waypoint> iterator = sequenceFlowWaypoints.iterator();

    Waypoint waypoint = iterator.next();
    assertWaypointCoordinates(waypoint, 361, 236);

    while (iterator.hasNext()) {
      waypoint = iterator.next();
    }

    assertWaypointCoordinates(waypoint, 391.5, 286);
  }

  @Test
  public void shouldPlaceTwoBoundaryEventsForSubProcess() {
    final ProcessBuilder builder = Bpmn.createExecutableProcess();

    instance =
        builder
            .startEvent(START_EVENT_ID)
            .subProcess(SUB_PROCESS_ID)
            .boundaryEvent("boundary1")
            .moveToActivity(SUB_PROCESS_ID)
            .boundaryEvent("boundary2")
            .moveToActivity(SUB_PROCESS_ID)
            .endEvent()
            .done();

    final Bounds boundaryEvent1Bounds = findBpmnShape("boundary1").getBounds();
    assertShapeCoordinates(boundaryEvent1Bounds, 343, 200);

    final Bounds boundaryEvent2Bounds = findBpmnShape("boundary2").getBounds();
    assertShapeCoordinates(boundaryEvent2Bounds, 379, 200);
  }

  @Test
  public void shouldPlaceThreeBoundaryEventsForSubProcess() {
    final ProcessBuilder builder = Bpmn.createExecutableProcess();

    instance =
        builder
            .startEvent(START_EVENT_ID)
            .subProcess(SUB_PROCESS_ID)
            .boundaryEvent("boundary1")
            .moveToActivity(SUB_PROCESS_ID)
            .boundaryEvent("boundary2")
            .moveToActivity(SUB_PROCESS_ID)
            .boundaryEvent("boundary3")
            .moveToActivity(SUB_PROCESS_ID)
            .endEvent()
            .done();

    final Bounds boundaryEvent1Bounds = findBpmnShape("boundary1").getBounds();
    assertShapeCoordinates(boundaryEvent1Bounds, 343, 200);

    final Bounds boundaryEvent2Bounds = findBpmnShape("boundary2").getBounds();
    assertShapeCoordinates(boundaryEvent2Bounds, 379, 200);

    final Bounds boundaryEvent3Bounds = findBpmnShape("boundary3").getBounds();
    assertShapeCoordinates(boundaryEvent3Bounds, 307, 200);
  }

  @Test
  public void shouldPlaceManyBoundaryEventsForSubProcess() {
    final ProcessBuilder builder = Bpmn.createExecutableProcess();

    instance =
        builder
            .startEvent(START_EVENT_ID)
            .subProcess(SUB_PROCESS_ID)
            .boundaryEvent("boundary1")
            .moveToActivity(SUB_PROCESS_ID)
            .boundaryEvent("boundary2")
            .moveToActivity(SUB_PROCESS_ID)
            .boundaryEvent("boundary3")
            .moveToActivity(SUB_PROCESS_ID)
            .boundaryEvent("boundary4")
            .moveToActivity(SUB_PROCESS_ID)
            .endEvent()
            .done();

    final Bounds boundaryEvent1Bounds = findBpmnShape("boundary1").getBounds();
    assertShapeCoordinates(boundaryEvent1Bounds, 343, 200);

    final Bounds boundaryEvent2Bounds = findBpmnShape("boundary2").getBounds();
    assertShapeCoordinates(boundaryEvent2Bounds, 379, 200);

    final Bounds boundaryEvent3Bounds = findBpmnShape("boundary3").getBounds();
    assertShapeCoordinates(boundaryEvent3Bounds, 307, 200);

    final Bounds boundaryEvent4Bounds = findBpmnShape("boundary4").getBounds();
    assertShapeCoordinates(boundaryEvent4Bounds, 343, 200);
  }

  @Test
  public void shouldPlaceTwoBranchesForParallelGateway() {
    final ProcessBuilder builder = Bpmn.createExecutableProcess();

    instance =
        builder
            .startEvent(START_EVENT_ID)
            .parallelGateway("id")
            .sequenceFlowId("s1")
            .userTask(USER_TASK_ID)
            .moveToNode("id")
            .sequenceFlowId("s2")
            .endEvent(END_EVENT_ID)
            .done();

    final Bounds userTaskBounds = findBpmnShape(USER_TASK_ID).getBounds();
    assertShapeCoordinates(userTaskBounds, 286, 78);

    final Bounds endEventBounds = findBpmnShape(END_EVENT_ID).getBounds();
    assertShapeCoordinates(endEventBounds, 286, 208);

    final Collection<Waypoint> sequenceFlowWaypoints = findBpmnEdge("s2").getWaypoints();
    final Iterator<Waypoint> iterator = sequenceFlowWaypoints.iterator();

    Waypoint waypoint = iterator.next();
    assertWaypointCoordinates(waypoint, 211, 143);

    while (iterator.hasNext()) {
      waypoint = iterator.next();
    }

    assertWaypointCoordinates(waypoint, 286, 226);
  }

  @Test
  public void shouldPlaceThreeBranchesForParallelGateway() {
    final ProcessBuilder builder = Bpmn.createExecutableProcess();

    instance =
        builder
            .startEvent(START_EVENT_ID)
            .parallelGateway("id")
            .userTask(USER_TASK_ID)
            .moveToNode("id")
            .endEvent(END_EVENT_ID)
            .moveToNode("id")
            .sequenceFlowId("s1")
            .serviceTask(SERVICE_TASK_ID)
            .done();

    final Bounds userTaskBounds = findBpmnShape(USER_TASK_ID).getBounds();
    assertShapeCoordinates(userTaskBounds, 286, 78);

    final Bounds endEventBounds = findBpmnShape(END_EVENT_ID).getBounds();
    assertShapeCoordinates(endEventBounds, 286, 208);

    final Bounds serviceTaskBounds = findBpmnShape(SERVICE_TASK_ID).getBounds();
    assertShapeCoordinates(serviceTaskBounds, 286, 294);

    final Collection<Waypoint> sequenceFlowWaypoints = findBpmnEdge("s1").getWaypoints();
    final Iterator<Waypoint> iterator = sequenceFlowWaypoints.iterator();

    Waypoint waypoint = iterator.next();
    assertWaypointCoordinates(waypoint, 211, 143);

    while (iterator.hasNext()) {
      waypoint = iterator.next();
    }

    assertWaypointCoordinates(waypoint, 286, 334);
  }

  @Test
  public void shouldPlaceManyBranchesForParallelGateway() {
    final ProcessBuilder builder = Bpmn.createExecutableProcess();

    instance =
        builder
            .startEvent(START_EVENT_ID)
            .parallelGateway("id")
            .userTask(USER_TASK_ID)
            .moveToNode("id")
            .endEvent(END_EVENT_ID)
            .moveToNode("id")
            .serviceTask(SERVICE_TASK_ID)
            .moveToNode("id")
            .sequenceFlowId("s1")
            .sendTask(SEND_TASK_ID)
            .done();

    final Bounds userTaskBounds = findBpmnShape(USER_TASK_ID).getBounds();
    assertShapeCoordinates(userTaskBounds, 286, 78);

    final Bounds endEventBounds = findBpmnShape(END_EVENT_ID).getBounds();
    assertShapeCoordinates(endEventBounds, 286, 208);

    final Bounds serviceTaskBounds = findBpmnShape(SERVICE_TASK_ID).getBounds();
    assertShapeCoordinates(serviceTaskBounds, 286, 294);

    final Bounds sendTaskBounds = findBpmnShape(SEND_TASK_ID).getBounds();
    assertShapeCoordinates(sendTaskBounds, 286, 424);

    final Collection<Waypoint> sequenceFlowWaypoints = findBpmnEdge("s1").getWaypoints();
    final Iterator<Waypoint> iterator = sequenceFlowWaypoints.iterator();

    Waypoint waypoint = iterator.next();
    assertWaypointCoordinates(waypoint, 211, 143);

    while (iterator.hasNext()) {
      waypoint = iterator.next();
    }

    assertWaypointCoordinates(waypoint, 286, 464);
  }

  @Test
  public void shouldPlaceTwoBranchesForExclusiveGateway() {
    final ProcessBuilder builder = Bpmn.createExecutableProcess();

    instance =
        builder
            .startEvent(START_EVENT_ID)
            .exclusiveGateway("id")
            .sequenceFlowId("s1")
            .userTask(USER_TASK_ID)
            .moveToNode("id")
            .sequenceFlowId("s2")
            .endEvent(END_EVENT_ID)
            .done();

    final Bounds userTaskBounds = findBpmnShape(USER_TASK_ID).getBounds();
    assertShapeCoordinates(userTaskBounds, 286, 78);

    final Bounds endEventBounds = findBpmnShape(END_EVENT_ID).getBounds();
    assertShapeCoordinates(endEventBounds, 286, 208);

    final Collection<Waypoint> sequenceFlowWaypoints = findBpmnEdge("s2").getWaypoints();
    final Iterator<Waypoint> iterator = sequenceFlowWaypoints.iterator();

    Waypoint waypoint = iterator.next();
    assertWaypointCoordinates(waypoint, 211, 143);

    while (iterator.hasNext()) {
      waypoint = iterator.next();
    }

    assertWaypointCoordinates(waypoint, 286, 226);
  }

  @Test
  public void shouldPlaceThreeBranchesForExclusiveGateway() {
    final ProcessBuilder builder = Bpmn.createExecutableProcess();

    instance =
        builder
            .startEvent(START_EVENT_ID)
            .exclusiveGateway("id")
            .userTask(USER_TASK_ID)
            .moveToNode("id")
            .endEvent(END_EVENT_ID)
            .moveToNode("id")
            .sequenceFlowId("s1")
            .serviceTask(SERVICE_TASK_ID)
            .done();

    final Bounds userTaskBounds = findBpmnShape(USER_TASK_ID).getBounds();
    assertShapeCoordinates(userTaskBounds, 286, 78);

    final Bounds endEventBounds = findBpmnShape(END_EVENT_ID).getBounds();
    assertShapeCoordinates(endEventBounds, 286, 208);

    final Bounds serviceTaskBounds = findBpmnShape(SERVICE_TASK_ID).getBounds();
    assertShapeCoordinates(serviceTaskBounds, 286, 294);

    final Collection<Waypoint> sequenceFlowWaypoints = findBpmnEdge("s1").getWaypoints();
    final Iterator<Waypoint> iterator = sequenceFlowWaypoints.iterator();

    Waypoint waypoint = iterator.next();
    assertWaypointCoordinates(waypoint, 211, 143);

    while (iterator.hasNext()) {
      waypoint = iterator.next();
    }

    assertWaypointCoordinates(waypoint, 286, 334);
  }

  @Test
  public void shouldPlaceManyBranchesForExclusiveGateway() {
    final ProcessBuilder builder = Bpmn.createExecutableProcess();

    instance =
        builder
            .startEvent(START_EVENT_ID)
            .exclusiveGateway("id")
            .userTask(USER_TASK_ID)
            .moveToNode("id")
            .endEvent(END_EVENT_ID)
            .moveToNode("id")
            .serviceTask(SERVICE_TASK_ID)
            .moveToNode("id")
            .sequenceFlowId("s1")
            .sendTask(SEND_TASK_ID)
            .done();

    final Bounds userTaskBounds = findBpmnShape(USER_TASK_ID).getBounds();
    assertShapeCoordinates(userTaskBounds, 286, 78);

    final Bounds endEventBounds = findBpmnShape(END_EVENT_ID).getBounds();
    assertShapeCoordinates(endEventBounds, 286, 208);

    final Bounds serviceTaskBounds = findBpmnShape(SERVICE_TASK_ID).getBounds();
    assertShapeCoordinates(serviceTaskBounds, 286, 294);

    final Bounds sendTaskBounds = findBpmnShape(SEND_TASK_ID).getBounds();
    assertShapeCoordinates(sendTaskBounds, 286, 424);

    final Collection<Waypoint> sequenceFlowWaypoints = findBpmnEdge("s1").getWaypoints();
    final Iterator<Waypoint> iterator = sequenceFlowWaypoints.iterator();

    Waypoint waypoint = iterator.next();
    assertWaypointCoordinates(waypoint, 211, 143);

    while (iterator.hasNext()) {
      waypoint = iterator.next();
    }

    assertWaypointCoordinates(waypoint, 286, 464);
  }

  @Test
  public void shouldPlaceTwoBranchesForEventBasedGateway() {
    final ProcessBuilder builder = Bpmn.createExecutableProcess();

    instance =
        builder
            .startEvent(START_EVENT_ID)
            .eventBasedGateway()
            .id("id")
            .sequenceFlowId("s1")
            .userTask(USER_TASK_ID)
            .moveToNode("id")
            .sequenceFlowId("s2")
            .endEvent(END_EVENT_ID)
            .done();

    final Bounds userTaskBounds = findBpmnShape(USER_TASK_ID).getBounds();
    assertShapeCoordinates(userTaskBounds, 286, 78);

    final Bounds endEventBounds = findBpmnShape(END_EVENT_ID).getBounds();
    assertShapeCoordinates(endEventBounds, 286, 208);

    final Collection<Waypoint> sequenceFlowWaypoints = findBpmnEdge("s2").getWaypoints();
    final Iterator<Waypoint> iterator = sequenceFlowWaypoints.iterator();

    Waypoint waypoint = iterator.next();
    assertWaypointCoordinates(waypoint, 211, 143);

    while (iterator.hasNext()) {
      waypoint = iterator.next();
    }

    assertWaypointCoordinates(waypoint, 286, 226);
  }

  @Test
  public void shouldPlaceThreeBranchesForEventBasedGateway() {
    final ProcessBuilder builder = Bpmn.createExecutableProcess();

    instance =
        builder
            .startEvent(START_EVENT_ID)
            .eventBasedGateway()
            .id("id")
            .userTask(USER_TASK_ID)
            .moveToNode("id")
            .endEvent(END_EVENT_ID)
            .moveToNode("id")
            .sequenceFlowId("s1")
            .serviceTask(SERVICE_TASK_ID)
            .done();

    final Bounds userTaskBounds = findBpmnShape(USER_TASK_ID).getBounds();
    assertShapeCoordinates(userTaskBounds, 286, 78);

    final Bounds endEventBounds = findBpmnShape(END_EVENT_ID).getBounds();
    assertShapeCoordinates(endEventBounds, 286, 208);

    final Bounds serviceTaskBounds = findBpmnShape(SERVICE_TASK_ID).getBounds();
    assertShapeCoordinates(serviceTaskBounds, 286, 294);

    final Collection<Waypoint> sequenceFlowWaypoints = findBpmnEdge("s1").getWaypoints();
    final Iterator<Waypoint> iterator = sequenceFlowWaypoints.iterator();

    Waypoint waypoint = iterator.next();
    assertWaypointCoordinates(waypoint, 211, 143);

    while (iterator.hasNext()) {
      waypoint = iterator.next();
    }

    assertWaypointCoordinates(waypoint, 286, 334);
  }

  @Test
  public void shouldPlaceManyBranchesForEventBasedGateway() {
    final ProcessBuilder builder = Bpmn.createExecutableProcess();

    instance =
        builder
            .startEvent(START_EVENT_ID)
            .eventBasedGateway()
            .id("id")
            .userTask(USER_TASK_ID)
            .moveToNode("id")
            .endEvent(END_EVENT_ID)
            .moveToNode("id")
            .serviceTask(SERVICE_TASK_ID)
            .moveToNode("id")
            .sequenceFlowId("s1")
            .sendTask(SEND_TASK_ID)
            .done();

    final Bounds userTaskBounds = findBpmnShape(USER_TASK_ID).getBounds();
    assertShapeCoordinates(userTaskBounds, 286, 78);

    final Bounds endEventBounds = findBpmnShape(END_EVENT_ID).getBounds();
    assertShapeCoordinates(endEventBounds, 286, 208);

    final Bounds serviceTaskBounds = findBpmnShape(SERVICE_TASK_ID).getBounds();
    assertShapeCoordinates(serviceTaskBounds, 286, 294);

    final Bounds sendTaskBounds = findBpmnShape(SEND_TASK_ID).getBounds();
    assertShapeCoordinates(sendTaskBounds, 286, 424);

    final Collection<Waypoint> sequenceFlowWaypoints = findBpmnEdge("s1").getWaypoints();
    final Iterator<Waypoint> iterator = sequenceFlowWaypoints.iterator();

    Waypoint waypoint = iterator.next();
    assertWaypointCoordinates(waypoint, 211, 143);

    while (iterator.hasNext()) {
      waypoint = iterator.next();
    }

    assertWaypointCoordinates(waypoint, 286, 464);
  }

  @Test
  public void shouldPlaceTwoBranchesForInclusiveGateway() {
    final ProcessBuilder builder = Bpmn.createExecutableProcess();

    instance =
        builder
            .startEvent(START_EVENT_ID)
            .inclusiveGateway("id")
            .sequenceFlowId("s1")
            .userTask(USER_TASK_ID)
            .moveToNode("id")
            .sequenceFlowId("s2")
            .endEvent(END_EVENT_ID)
            .done();

    final Bounds userTaskBounds = findBpmnShape(USER_TASK_ID).getBounds();
    assertShapeCoordinates(userTaskBounds, 286, 78);

    final Bounds endEventBounds = findBpmnShape(END_EVENT_ID).getBounds();
    assertShapeCoordinates(endEventBounds, 286, 208);

    final Collection<Waypoint> sequenceFlowWaypoints = findBpmnEdge("s2").getWaypoints();
    final Iterator<Waypoint> iterator = sequenceFlowWaypoints.iterator();

    Waypoint waypoint = iterator.next();
    assertWaypointCoordinates(waypoint, 211, 143);

    while (iterator.hasNext()) {
      waypoint = iterator.next();
    }

    assertWaypointCoordinates(waypoint, 286, 226);
  }

  @Test
  public void shouldPlaceThreeBranchesForInclusiveGateway() {
    final ProcessBuilder builder = Bpmn.createExecutableProcess();

    instance =
        builder
            .startEvent(START_EVENT_ID)
            .inclusiveGateway("id")
            .userTask(USER_TASK_ID)
            .moveToNode("id")
            .endEvent(END_EVENT_ID)
            .moveToNode("id")
            .sequenceFlowId("s1")
            .serviceTask(SERVICE_TASK_ID)
            .done();

    final Bounds userTaskBounds = findBpmnShape(USER_TASK_ID).getBounds();
    assertShapeCoordinates(userTaskBounds, 286, 78);

    final Bounds endEventBounds = findBpmnShape(END_EVENT_ID).getBounds();
    assertShapeCoordinates(endEventBounds, 286, 208);

    final Bounds serviceTaskBounds = findBpmnShape(SERVICE_TASK_ID).getBounds();
    assertShapeCoordinates(serviceTaskBounds, 286, 294);

    final Collection<Waypoint> sequenceFlowWaypoints = findBpmnEdge("s1").getWaypoints();
    final Iterator<Waypoint> iterator = sequenceFlowWaypoints.iterator();

    Waypoint waypoint = iterator.next();
    assertWaypointCoordinates(waypoint, 211, 143);

    while (iterator.hasNext()) {
      waypoint = iterator.next();
    }

    assertWaypointCoordinates(waypoint, 286, 334);
  }

  @Test
  public void shouldPlaceManyBranchesForInclusiveGateway() {
    final ProcessBuilder builder = Bpmn.createExecutableProcess();

    instance =
        builder
            .startEvent(START_EVENT_ID)
            .inclusiveGateway("id")
            .userTask(USER_TASK_ID)
            .moveToNode("id")
            .endEvent(END_EVENT_ID)
            .moveToNode("id")
            .serviceTask(SERVICE_TASK_ID)
            .moveToNode("id")
            .sequenceFlowId("s1")
            .sendTask(SEND_TASK_ID)
            .done();

    final Bounds userTaskBounds = findBpmnShape(USER_TASK_ID).getBounds();
    assertShapeCoordinates(userTaskBounds, 286, 78);

    final Bounds endEventBounds = findBpmnShape(END_EVENT_ID).getBounds();
    assertShapeCoordinates(endEventBounds, 286, 208);

    final Bounds serviceTaskBounds = findBpmnShape(SERVICE_TASK_ID).getBounds();
    assertShapeCoordinates(serviceTaskBounds, 286, 294);

    final Bounds sendTaskBounds = findBpmnShape(SEND_TASK_ID).getBounds();
    assertShapeCoordinates(sendTaskBounds, 286, 424);

    final Collection<Waypoint> sequenceFlowWaypoints = findBpmnEdge("s1").getWaypoints();
    final Iterator<Waypoint> iterator = sequenceFlowWaypoints.iterator();

    Waypoint waypoint = iterator.next();
    assertWaypointCoordinates(waypoint, 211, 143);

    while (iterator.hasNext()) {
      waypoint = iterator.next();
    }

    assertWaypointCoordinates(waypoint, 286, 464);
  }

  public void shouldPlaceStartEventWithinSubProcess() {

    final ProcessBuilder builder = Bpmn.createExecutableProcess();

    instance =
        builder
            .startEvent(START_EVENT_ID)
            .subProcess(SUB_PROCESS_ID)
            .embeddedSubProcess()
            .startEvent("innerStartEvent")
            .done();

    final Bounds startEventBounds = findBpmnShape("innerStartEvent").getBounds();
    assertShapeCoordinates(startEventBounds, 236, 100);
  }

  @Test
  public void shouldAdjustSubProcessWidth() {

    final ProcessBuilder builder = Bpmn.createExecutableProcess();

    instance =
        builder
            .startEvent(START_EVENT_ID)
            .subProcess(SUB_PROCESS_ID)
            .embeddedSubProcess()
            .startEvent("innerStartEvent")
            .parallelGateway("innerParallelGateway")
            .userTask("innerUserTask")
            .endEvent("innerEndEvent")
            .subProcessDone()
            .done();

    final Bounds subProcessBounds = findBpmnShape(SUB_PROCESS_ID).getBounds();
    assertThat(subProcessBounds.getWidth()).isEqualTo(472);
  }

  @Test
  public void shouldAdjustSubProcessWidthWithEmbeddedSubProcess() {

    final ProcessBuilder builder = Bpmn.createExecutableProcess();

    instance =
        builder
            .startEvent(START_EVENT_ID)
            .subProcess(SUB_PROCESS_ID)
            .embeddedSubProcess()
            .startEvent("innerStartEvent")
            .subProcess("innerSubProcess")
            .embeddedSubProcess()
            .startEvent()
            .userTask()
            .userTask()
            .endEvent()
            .subProcessDone()
            .endEvent("innerEndEvent")
            .subProcessDone()
            .done();

    final Bounds subProcessBounds = findBpmnShape(SUB_PROCESS_ID).getBounds();
    assertThat(subProcessBounds.getWidth()).isEqualTo(794);
  }

  @Test
  public void shouldAdjustSubProcessHeight() {

    final ProcessBuilder builder = Bpmn.createExecutableProcess();

    instance =
        builder
            .startEvent(START_EVENT_ID)
            .subProcess(SUB_PROCESS_ID)
            .embeddedSubProcess()
            .startEvent("innerStartEvent")
            .parallelGateway("innerParallelGateway")
            .endEvent("innerEndEvent")
            .moveToNode("innerParallelGateway")
            .userTask("innerUserTask")
            .subProcessDone()
            .done();

    final Bounds subProcessBounds = findBpmnShape(SUB_PROCESS_ID).getBounds();
    assertThat(subProcessBounds.getHeight()).isEqualTo(298);
  }

  @Test
  public void shouldAdjustSubProcessHeightWithEmbeddedProcess() {

    final ProcessBuilder builder = Bpmn.createExecutableProcess();

    instance =
        builder
            .startEvent(START_EVENT_ID)
            .subProcess(SUB_PROCESS_ID)
            .embeddedSubProcess()
            .startEvent("innerStartEvent")
            .subProcess()
            .embeddedSubProcess()
            .startEvent()
            .exclusiveGateway("id")
            .userTask()
            .moveToNode("id")
            .endEvent()
            .subProcessDone()
            .endEvent("innerEndEvent")
            .subProcessDone()
            .endEvent()
            .done();

    final Bounds subProcessBounds = findBpmnShape(SUB_PROCESS_ID).getBounds();
    assertThat(subProcessBounds.getY()).isEqualTo(-32);
    assertThat(subProcessBounds.getHeight()).isEqualTo(376);
  }

  @Test
  public void shouldPlaceCompensation() {
    final ProcessBuilder builder = Bpmn.createExecutableProcess();

    instance =
        builder
            .startEvent()
            .userTask("task")
            .boundaryEvent("boundary")
            .compensateEventDefinition()
            .compensateEventDefinitionDone()
            .compensationStart()
            .userTask("compensate")
            .name("compensate")
            .compensationDone()
            .userTask("task2")
            .boundaryEvent("boundary2")
            .compensateEventDefinition()
            .compensateEventDefinitionDone()
            .compensationStart()
            .userTask("compensate2")
            .name("compensate2")
            .compensationDone()
            .endEvent("theend")
            .done();

    final Bounds compensationBounds = findBpmnShape("compensate").getBounds();
    assertShapeCoordinates(compensationBounds, 266.5, 186);
    final Bounds compensation2Bounds = findBpmnShape("compensate2").getBounds();
    assertShapeCoordinates(compensation2Bounds, 416.5, 186);
  }

  protected BpmnShape findBpmnShape(final String id) {
    final Collection<BpmnShape> allShapes = instance.getModelElementsByType(BpmnShape.class);

    final Iterator<BpmnShape> iterator = allShapes.iterator();
    while (iterator.hasNext()) {
      final BpmnShape shape = iterator.next();
      if (shape.getBpmnElement().getId().equals(id)) {
        return shape;
      }
    }
    return null;
  }

  protected BpmnEdge findBpmnEdge(final String sequenceFlowId) {
    final Collection<BpmnEdge> allEdges = instance.getModelElementsByType(BpmnEdge.class);
    final Iterator<BpmnEdge> iterator = allEdges.iterator();

    while (iterator.hasNext()) {
      final BpmnEdge edge = iterator.next();
      if (edge.getBpmnElement().getId().equals(sequenceFlowId)) {
        return edge;
      }
    }
    return null;
  }

  protected void assertShapeCoordinates(final Bounds bounds, final double x, final double y) {
    assertThat(bounds.getX()).isEqualTo(x);
    assertThat(bounds.getY()).isEqualTo(y);
  }

  protected void assertWaypointCoordinates(
      final Waypoint waypoint, final double x, final double y) {
    assertThat(x).isEqualTo(waypoint.getX());
    assertThat(y).isEqualTo(waypoint.getY());
  }
}
