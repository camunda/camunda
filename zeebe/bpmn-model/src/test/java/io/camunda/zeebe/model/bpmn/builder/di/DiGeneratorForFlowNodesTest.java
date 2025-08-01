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

import static io.camunda.zeebe.model.bpmn.BpmnTestConstants.BOUNDARY_ID;
import static io.camunda.zeebe.model.bpmn.BpmnTestConstants.CALL_ACTIVITY_ID;
import static io.camunda.zeebe.model.bpmn.BpmnTestConstants.CATCH_ID;
import static io.camunda.zeebe.model.bpmn.BpmnTestConstants.CONDITION_ID;
import static io.camunda.zeebe.model.bpmn.BpmnTestConstants.END_EVENT_ID;
import static io.camunda.zeebe.model.bpmn.BpmnTestConstants.SEND_TASK_ID;
import static io.camunda.zeebe.model.bpmn.BpmnTestConstants.SERVICE_TASK_ID;
import static io.camunda.zeebe.model.bpmn.BpmnTestConstants.START_EVENT_ID;
import static io.camunda.zeebe.model.bpmn.BpmnTestConstants.SUB_PROCESS_ID;
import static io.camunda.zeebe.model.bpmn.BpmnTestConstants.TASK_ID;
import static io.camunda.zeebe.model.bpmn.BpmnTestConstants.TEST_CONDITION;
import static io.camunda.zeebe.model.bpmn.BpmnTestConstants.TRANSACTION_ID;
import static io.camunda.zeebe.model.bpmn.BpmnTestConstants.USER_TASK_ID;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.builder.ProcessBuilder;
import io.camunda.zeebe.model.bpmn.instance.bpmndi.BpmnDiagram;
import io.camunda.zeebe.model.bpmn.instance.bpmndi.BpmnShape;
import java.io.IOException;
import java.util.Collection;
import java.util.Iterator;
import org.camunda.bpm.model.xml.instance.ModelElementInstance;
import org.junit.After;
import org.junit.Test;

public class DiGeneratorForFlowNodesTest {

  private BpmnModelInstance instance;

  @After
  public void validateModel() throws IOException {
    if (instance != null) {
      Bpmn.validateModel(instance);
    }
  }

  @Test
  public void shouldGeneratePlaneForProcess() {

    // when
    instance = Bpmn.createExecutableProcess("process").done();

    // then
    final Collection<BpmnDiagram> bpmnDiagrams = instance.getModelElementsByType(BpmnDiagram.class);
    assertThat(bpmnDiagrams).hasSize(1);

    final BpmnDiagram diagram = bpmnDiagrams.iterator().next();
    assertThat(diagram.getId()).isNotNull();

    assertThat(diagram.getBpmnPlane()).isNotNull();
    assertThat((ModelElementInstance) instance.getModelElementById("process"))
        .isEqualTo(diagram.getBpmnPlane().getBpmnElement());
  }

  @Test
  public void shouldGenerateShapeForStartEvent() {

    // given
    final ProcessBuilder processBuilder = Bpmn.createExecutableProcess();

    // when
    instance = processBuilder.startEvent(START_EVENT_ID).endEvent(END_EVENT_ID).done();

    // then
    final Collection<BpmnShape> allShapes = instance.getModelElementsByType(BpmnShape.class);
    assertThat(allShapes).hasSize(2);

    assertEventShapeProperties(START_EVENT_ID);
  }

  @Test
  public void shouldGenerateShapeForUserTask() {

    // given
    final ProcessBuilder processBuilder = Bpmn.createExecutableProcess();

    // when
    instance = processBuilder.startEvent(START_EVENT_ID).userTask(USER_TASK_ID).done();

    // then
    final Collection<BpmnShape> allShapes = instance.getModelElementsByType(BpmnShape.class);
    assertThat(allShapes).hasSize(2);

    assertTaskShapeProperties(USER_TASK_ID);
  }

  @Test
  public void shouldGenerateShapeForSendTask() {

    // given
    final ProcessBuilder processBuilder = Bpmn.createExecutableProcess();

    // when
    instance = processBuilder.startEvent(START_EVENT_ID).sendTask(SEND_TASK_ID).done();

    // then
    final Collection<BpmnShape> allShapes = instance.getModelElementsByType(BpmnShape.class);
    assertThat(allShapes).hasSize(2);

    assertTaskShapeProperties(SEND_TASK_ID);
  }

  @Test
  public void shouldGenerateShapeForServiceTask() {

    // given
    final ProcessBuilder processBuilder = Bpmn.createExecutableProcess();

    // when
    instance = processBuilder.startEvent(START_EVENT_ID).serviceTask(SERVICE_TASK_ID).done();

    // then
    final Collection<BpmnShape> allShapes = instance.getModelElementsByType(BpmnShape.class);
    assertThat(allShapes).hasSize(2);

    assertTaskShapeProperties(SERVICE_TASK_ID);
  }

  @Test
  public void shouldGenerateShapeForReceiveTask() {

    // given
    final ProcessBuilder processBuilder = Bpmn.createExecutableProcess();

    // when
    instance = processBuilder.startEvent(START_EVENT_ID).receiveTask(TASK_ID).done();

    // then
    final Collection<BpmnShape> allShapes = instance.getModelElementsByType(BpmnShape.class);
    assertThat(allShapes).hasSize(2);

    assertTaskShapeProperties(TASK_ID);
  }

  @Test
  public void shouldGenerateShapeForManualTask() {

    // given
    final ProcessBuilder processBuilder = Bpmn.createExecutableProcess();

    // when
    instance = processBuilder.startEvent(START_EVENT_ID).manualTask(TASK_ID).done();

    // then
    final Collection<BpmnShape> allShapes = instance.getModelElementsByType(BpmnShape.class);
    assertThat(allShapes).hasSize(2);

    assertTaskShapeProperties(TASK_ID);
  }

  @Test
  public void shouldGenerateShapeForBusinessRuleTask() {

    // given
    final ProcessBuilder processBuilder = Bpmn.createExecutableProcess();

    // when
    instance = processBuilder.startEvent(START_EVENT_ID).businessRuleTask(TASK_ID).done();

    // then
    final Collection<BpmnShape> allShapes = instance.getModelElementsByType(BpmnShape.class);
    assertThat(allShapes).hasSize(2);

    assertTaskShapeProperties(TASK_ID);
  }

  @Test
  public void shouldGenerateShapeForScriptTask() {

    // given
    final ProcessBuilder processBuilder = Bpmn.createExecutableProcess();

    // when
    instance = processBuilder.startEvent(START_EVENT_ID).scriptTask(TASK_ID).done();

    // then
    final Collection<BpmnShape> allShapes = instance.getModelElementsByType(BpmnShape.class);
    assertThat(allShapes).hasSize(2);

    assertTaskShapeProperties(TASK_ID);
  }

  @Test
  public void shouldGenerateShapeForCatchingIntermediateEvent() {

    // given
    final ProcessBuilder processBuilder = Bpmn.createExecutableProcess();

    // when
    instance =
        processBuilder
            .startEvent(START_EVENT_ID)
            .intermediateCatchEvent(CATCH_ID)
            .endEvent(END_EVENT_ID)
            .done();

    // then
    final Collection<BpmnShape> allShapes = instance.getModelElementsByType(BpmnShape.class);
    assertThat(allShapes).hasSize(3);

    assertEventShapeProperties(CATCH_ID);
  }

  @Test
  public void shouldGenerateShapeForBoundaryIntermediateEvent() {

    // given
    final ProcessBuilder processBuilder = Bpmn.createExecutableProcess();

    // when
    instance =
        processBuilder
            .startEvent(START_EVENT_ID)
            .userTask(USER_TASK_ID)
            .endEvent(END_EVENT_ID)
            .moveToActivity(USER_TASK_ID)
            .boundaryEvent(BOUNDARY_ID)
            .conditionalEventDefinition(CONDITION_ID)
            .condition(TEST_CONDITION)
            .conditionalEventDefinitionDone()
            .endEvent()
            .done();

    // then
    final Collection<BpmnShape> allShapes = instance.getModelElementsByType(BpmnShape.class);
    assertThat(allShapes).hasSize(5);

    assertEventShapeProperties(BOUNDARY_ID);
  }

  @Test
  public void shouldGenerateShapeForThrowingIntermediateEvent() {

    // given
    final ProcessBuilder processBuilder = Bpmn.createExecutableProcess();

    // when
    instance =
        processBuilder
            .startEvent(START_EVENT_ID)
            .intermediateThrowEvent("inter")
            .endEvent(END_EVENT_ID)
            .done();

    // then
    final Collection<BpmnShape> allShapes = instance.getModelElementsByType(BpmnShape.class);
    assertThat(allShapes).hasSize(3);

    assertEventShapeProperties("inter");
  }

  @Test
  public void shouldGenerateShapeForEndEvent() {

    // given
    final ProcessBuilder processBuilder = Bpmn.createExecutableProcess();

    // when
    instance = processBuilder.startEvent(START_EVENT_ID).endEvent(END_EVENT_ID).done();

    // then
    final Collection<BpmnShape> allShapes = instance.getModelElementsByType(BpmnShape.class);
    assertThat(allShapes).hasSize(2);

    assertEventShapeProperties(END_EVENT_ID);
  }

  @Test
  public void shouldGenerateShapeForBlankSubProcess() {

    // given
    final ProcessBuilder processBuilder = Bpmn.createExecutableProcess();

    // when
    instance =
        processBuilder
            .startEvent(START_EVENT_ID)
            .subProcess(SUB_PROCESS_ID)
            .endEvent(END_EVENT_ID)
            .done();

    // then
    final Collection<BpmnShape> allShapes = instance.getModelElementsByType(BpmnShape.class);
    assertThat(allShapes).hasSize(3);

    final BpmnShape bpmnShapeSubProcess = findBpmnShape(SUB_PROCESS_ID);
    assertThat(bpmnShapeSubProcess).isNotNull();
    assertSubProcessSize(bpmnShapeSubProcess);
    assertThat(bpmnShapeSubProcess.isExpanded()).isTrue();
  }

  @Test
  public void shouldGenerateShapesForNestedFlowNodes() {

    // given
    final ProcessBuilder processBuilder = Bpmn.createExecutableProcess();

    // when
    instance =
        processBuilder
            .startEvent(START_EVENT_ID)
            .subProcess(SUB_PROCESS_ID)
            .embeddedSubProcess()
            .startEvent("innerStartEvent")
            .userTask("innerUserTask")
            .endEvent("innerEndEvent")
            .subProcessDone()
            .endEvent(END_EVENT_ID)
            .done();

    // then
    final Collection<BpmnShape> allShapes = instance.getModelElementsByType(BpmnShape.class);
    assertThat(allShapes).hasSize(6);

    assertEventShapeProperties("innerStartEvent");
    assertTaskShapeProperties("innerUserTask");
    assertEventShapeProperties("innerEndEvent");

    final BpmnShape bpmnShapeSubProcess = findBpmnShape(SUB_PROCESS_ID);
    assertThat(bpmnShapeSubProcess).isNotNull();
    assertThat(bpmnShapeSubProcess.isExpanded()).isTrue();
  }

  @Test
  public void shouldGenerateShapeForEventSubProcess() {

    // given
    final ProcessBuilder processBuilder = Bpmn.createExecutableProcess();

    // when
    instance =
        processBuilder
            .startEvent(START_EVENT_ID)
            .endEvent(END_EVENT_ID)
            .subProcess(SUB_PROCESS_ID)
            .triggerByEvent()
            .embeddedSubProcess()
            .startEvent("innerStartEvent")
            .endEvent("innerEndEvent")
            .subProcessDone()
            .done();

    // then
    final Collection<BpmnShape> allShapes = instance.getModelElementsByType(BpmnShape.class);
    assertThat(allShapes).hasSize(5);

    assertEventShapeProperties("innerStartEvent");
    assertEventShapeProperties("innerEndEvent");

    final BpmnShape bpmnShapeEventSubProcess = findBpmnShape(SUB_PROCESS_ID);
    assertThat(bpmnShapeEventSubProcess).isNotNull();
    assertThat(bpmnShapeEventSubProcess.isExpanded()).isTrue();
  }

  @Test
  public void shouldGenerateShapeForCallActivity() {

    // given
    final ProcessBuilder processBuilder = Bpmn.createExecutableProcess();

    // when
    instance =
        processBuilder
            .startEvent(START_EVENT_ID)
            .callActivity(CALL_ACTIVITY_ID)
            .endEvent(END_EVENT_ID)
            .done();

    // then
    final Collection<BpmnShape> allShapes = instance.getModelElementsByType(BpmnShape.class);
    assertThat(allShapes).hasSize(3);

    assertTaskShapeProperties(CALL_ACTIVITY_ID);
  }

  @Test
  public void shouldGenerateShapeForTransaction() {

    // given
    final ProcessBuilder processBuilder = Bpmn.createExecutableProcess();

    // when
    instance =
        processBuilder
            .startEvent(START_EVENT_ID)
            .transaction(TRANSACTION_ID)
            .embeddedSubProcess()
            .startEvent("innerStartEvent")
            .userTask("innerUserTask")
            .endEvent("innerEndEvent")
            .transactionDone()
            .endEvent(END_EVENT_ID)
            .done();

    // then
    final Collection<BpmnShape> allShapes = instance.getModelElementsByType(BpmnShape.class);
    assertThat(allShapes).hasSize(6);

    assertEventShapeProperties("innerStartEvent");
    assertTaskShapeProperties("innerUserTask");
    assertEventShapeProperties("innerEndEvent");

    final BpmnShape bpmnShapeSubProcess = findBpmnShape(TRANSACTION_ID);
    assertThat(bpmnShapeSubProcess).isNotNull();
    assertThat(bpmnShapeSubProcess.isExpanded()).isTrue();
  }

  @Test
  public void shouldGenerateShapeForParallelGateway() {

    // given
    final ProcessBuilder processBuilder = Bpmn.createExecutableProcess();

    // when
    instance =
        processBuilder
            .startEvent(START_EVENT_ID)
            .parallelGateway("and")
            .endEvent(END_EVENT_ID)
            .done();

    // then
    final Collection<BpmnShape> allShapes = instance.getModelElementsByType(BpmnShape.class);
    assertThat(allShapes).hasSize(3);

    assertGatewayShapeProperties("and");
  }

  @Test
  public void shouldGenerateShapeForInclusiveGateway() {

    // given
    final ProcessBuilder processBuilder = Bpmn.createExecutableProcess();

    // when
    instance =
        processBuilder
            .startEvent(START_EVENT_ID)
            .inclusiveGateway("inclusive")
            .endEvent(END_EVENT_ID)
            .done();

    // then
    final Collection<BpmnShape> allShapes = instance.getModelElementsByType(BpmnShape.class);
    assertThat(allShapes).hasSize(3);

    assertGatewayShapeProperties("inclusive");
  }

  @Test
  public void shouldGenerateShapeForEventBasedGateway() {

    // given
    final ProcessBuilder processBuilder = Bpmn.createExecutableProcess();

    // when
    instance =
        processBuilder
            .startEvent(START_EVENT_ID)
            .eventBasedGateway()
            .id("eventBased")
            .endEvent(END_EVENT_ID)
            .done();

    // then
    final Collection<BpmnShape> allShapes = instance.getModelElementsByType(BpmnShape.class);
    assertThat(allShapes).hasSize(3);

    assertGatewayShapeProperties("eventBased");
  }

  @Test
  public void shouldGenerateShapeForExclusiveGateway() {

    // given
    final ProcessBuilder processBuilder = Bpmn.createExecutableProcess();

    // when
    instance =
        processBuilder
            .startEvent(START_EVENT_ID)
            .exclusiveGateway("or")
            .endEvent(END_EVENT_ID)
            .done();

    // then
    final Collection<BpmnShape> allShapes = instance.getModelElementsByType(BpmnShape.class);
    assertThat(allShapes).hasSize(3);

    assertGatewayShapeProperties("or");
    final BpmnShape bpmnShape = findBpmnShape("or");
    assertThat(bpmnShape.isMarkerVisible()).isTrue();
  }

  protected void assertTaskShapeProperties(final String id) {
    final BpmnShape bpmnShapeTask = findBpmnShape(id);
    assertThat(bpmnShapeTask).isNotNull();
    assertActivitySize(bpmnShapeTask);
  }

  protected void assertEventShapeProperties(final String id) {
    final BpmnShape bpmnShapeEvent = findBpmnShape(id);
    assertThat(bpmnShapeEvent).isNotNull();
    assertEventSize(bpmnShapeEvent);
  }

  protected void assertGatewayShapeProperties(final String id) {
    final BpmnShape bpmnShapeGateway = findBpmnShape(id);
    assertThat(bpmnShapeGateway).isNotNull();
    assertGatewaySize(bpmnShapeGateway);
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

  protected void assertEventSize(final BpmnShape shape) {
    assertSize(shape, 36, 36);
  }

  protected void assertGatewaySize(final BpmnShape shape) {
    assertSize(shape, 50, 50);
  }

  protected void assertSubProcessSize(final BpmnShape shape) {
    assertSize(shape, 200, 350);
  }

  protected void assertActivitySize(final BpmnShape shape) {
    assertSize(shape, 80, 100);
  }

  protected void assertSize(final BpmnShape shape, final int height, final int width) {
    assertThat(shape.getBounds().getHeight()).isEqualTo(height);
    assertThat(shape.getBounds().getWidth()).isEqualTo(width);
  }
}
