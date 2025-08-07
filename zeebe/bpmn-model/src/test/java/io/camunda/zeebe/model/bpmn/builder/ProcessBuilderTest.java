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

package io.camunda.zeebe.model.bpmn.builder;

import static io.camunda.zeebe.model.bpmn.BpmnTestConstants.BOUNDARY_ID;
import static io.camunda.zeebe.model.bpmn.BpmnTestConstants.CATCH_ID;
import static io.camunda.zeebe.model.bpmn.BpmnTestConstants.CONDITION_ID;
import static io.camunda.zeebe.model.bpmn.BpmnTestConstants.SERVICE_TASK_ID;
import static io.camunda.zeebe.model.bpmn.BpmnTestConstants.START_EVENT_ID;
import static io.camunda.zeebe.model.bpmn.BpmnTestConstants.SUB_PROCESS_ID;
import static io.camunda.zeebe.model.bpmn.BpmnTestConstants.TEST_CONDITION;
import static io.camunda.zeebe.model.bpmn.BpmnTestConstants.TRANSACTION_ID;
import static io.camunda.zeebe.model.bpmn.BpmnTestConstants.USER_TASK_ID;
import static io.camunda.zeebe.model.bpmn.impl.BpmnModelConstants.BPMN20_NS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.assertj.core.api.Fail.fail;

import io.camunda.zeebe.model.bpmn.AssociationDirection;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelException;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.GatewayDirection;
import io.camunda.zeebe.model.bpmn.TransactionMethod;
import io.camunda.zeebe.model.bpmn.instance.Activity;
import io.camunda.zeebe.model.bpmn.instance.Association;
import io.camunda.zeebe.model.bpmn.instance.BaseElement;
import io.camunda.zeebe.model.bpmn.instance.BoundaryEvent;
import io.camunda.zeebe.model.bpmn.instance.BpmnModelElementInstance;
import io.camunda.zeebe.model.bpmn.instance.CompensateEventDefinition;
import io.camunda.zeebe.model.bpmn.instance.ConditionalEventDefinition;
import io.camunda.zeebe.model.bpmn.instance.Definitions;
import io.camunda.zeebe.model.bpmn.instance.EndEvent;
import io.camunda.zeebe.model.bpmn.instance.Error;
import io.camunda.zeebe.model.bpmn.instance.ErrorEventDefinition;
import io.camunda.zeebe.model.bpmn.instance.Escalation;
import io.camunda.zeebe.model.bpmn.instance.EscalationEventDefinition;
import io.camunda.zeebe.model.bpmn.instance.Event;
import io.camunda.zeebe.model.bpmn.instance.EventDefinition;
import io.camunda.zeebe.model.bpmn.instance.FlowElement;
import io.camunda.zeebe.model.bpmn.instance.FlowNode;
import io.camunda.zeebe.model.bpmn.instance.Gateway;
import io.camunda.zeebe.model.bpmn.instance.InclusiveGateway;
import io.camunda.zeebe.model.bpmn.instance.Message;
import io.camunda.zeebe.model.bpmn.instance.MessageEventDefinition;
import io.camunda.zeebe.model.bpmn.instance.MultiInstanceLoopCharacteristics;
import io.camunda.zeebe.model.bpmn.instance.Process;
import io.camunda.zeebe.model.bpmn.instance.ReceiveTask;
import io.camunda.zeebe.model.bpmn.instance.ScriptTask;
import io.camunda.zeebe.model.bpmn.instance.SendTask;
import io.camunda.zeebe.model.bpmn.instance.SequenceFlow;
import io.camunda.zeebe.model.bpmn.instance.ServiceTask;
import io.camunda.zeebe.model.bpmn.instance.Signal;
import io.camunda.zeebe.model.bpmn.instance.SignalEventDefinition;
import io.camunda.zeebe.model.bpmn.instance.StartEvent;
import io.camunda.zeebe.model.bpmn.instance.SubProcess;
import io.camunda.zeebe.model.bpmn.instance.Task;
import io.camunda.zeebe.model.bpmn.instance.TimeCycle;
import io.camunda.zeebe.model.bpmn.instance.TimeDate;
import io.camunda.zeebe.model.bpmn.instance.TimeDuration;
import io.camunda.zeebe.model.bpmn.instance.TimerEventDefinition;
import io.camunda.zeebe.model.bpmn.instance.Transaction;
import io.camunda.zeebe.model.bpmn.instance.UserTask;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeProperties;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeProperty;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.camunda.bpm.model.xml.Model;
import org.camunda.bpm.model.xml.instance.ModelElementInstance;
import org.camunda.bpm.model.xml.type.ModelElementType;
import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class ProcessBuilderTest {

  public static final String TIMER_DATE = "2011-03-11T12:13:14Z";
  public static final String TIMER_DURATION = "P10D";
  public static final String TIMER_CYCLE = "R3/PT10H";
  private static ModelElementType taskType;
  private static ModelElementType gatewayType;
  private static ModelElementType eventType;
  private static ModelElementType processType;
  @Rule public final ExpectedException thrown = ExpectedException.none();
  private BpmnModelInstance modelInstance;

  @BeforeClass
  public static void getElementTypes() {
    final Model model = Bpmn.createEmptyModel().getModel();
    taskType = model.getType(Task.class);
    gatewayType = model.getType(Gateway.class);
    eventType = model.getType(Event.class);
    processType = model.getType(Process.class);
  }

  @After
  public void validateModel() {
    if (modelInstance != null) {
      Bpmn.validateModel(modelInstance);
    }
  }

  @Test
  public void testCreateEmptyProcess() {
    modelInstance = Bpmn.createProcess().done();

    final Definitions definitions = modelInstance.getDefinitions();
    assertThat(definitions).isNotNull();
    assertThat(definitions.getTargetNamespace()).isEqualTo(BPMN20_NS);

    final Collection<ModelElementInstance> processes =
        modelInstance.getModelElementsByType(processType);
    assertThat(processes).hasSize(1);

    final Process process = (Process) processes.iterator().next();
    assertThat(process.getId()).isNotNull();
  }

  @Test
  public void testGetElement() {
    // Make sure this method is publicly available
    final Process process = Bpmn.createProcess().getElement();
    assertThat(process).isNotNull();
  }

  @Test
  public void testCreateProcessWithStartEvent() {
    modelInstance = Bpmn.createProcess().startEvent().done();

    assertThat(modelInstance.getModelElementsByType(eventType)).hasSize(1);
  }

  @Test
  public void testCreateProcessWithEndEvent() {
    modelInstance = Bpmn.createProcess().startEvent().endEvent().done();

    assertThat(modelInstance.getModelElementsByType(eventType)).hasSize(2);
  }

  @Test
  public void testCreateProcessWithServiceTask() {
    modelInstance = Bpmn.createProcess().startEvent().serviceTask().endEvent().done();

    assertThat(modelInstance.getModelElementsByType(eventType)).hasSize(2);
    assertThat(modelInstance.getModelElementsByType(taskType)).hasSize(1);
  }

  @Test
  public void testCreateProcessWithSendTask() {
    modelInstance = Bpmn.createProcess().startEvent().sendTask().endEvent().done();

    assertThat(modelInstance.getModelElementsByType(eventType)).hasSize(2);
    assertThat(modelInstance.getModelElementsByType(taskType)).hasSize(1);
  }

  @Test
  public void testCreateProcessWithUserTask() {
    modelInstance = Bpmn.createProcess().startEvent().userTask().endEvent().done();

    assertThat(modelInstance.getModelElementsByType(eventType)).hasSize(2);
    assertThat(modelInstance.getModelElementsByType(taskType)).hasSize(1);
  }

  @Test
  public void testCreateProcessWithBusinessRuleTask() {
    modelInstance = Bpmn.createProcess().startEvent().businessRuleTask().endEvent().done();

    assertThat(modelInstance.getModelElementsByType(eventType)).hasSize(2);
    assertThat(modelInstance.getModelElementsByType(taskType)).hasSize(1);
  }

  @Test
  public void testCreateProcessWithScriptTask() {
    modelInstance = Bpmn.createProcess().startEvent().scriptTask().endEvent().done();

    assertThat(modelInstance.getModelElementsByType(eventType)).hasSize(2);
    assertThat(modelInstance.getModelElementsByType(taskType)).hasSize(1);
  }

  @Test
  public void testCreateProcessWithReceiveTask() {
    modelInstance = Bpmn.createProcess().startEvent().receiveTask().endEvent().done();

    assertThat(modelInstance.getModelElementsByType(eventType)).hasSize(2);
    assertThat(modelInstance.getModelElementsByType(taskType)).hasSize(1);
  }

  @Test
  public void testCreateProcessWithManualTask() {
    modelInstance = Bpmn.createProcess().startEvent().manualTask().endEvent().done();

    assertThat(modelInstance.getModelElementsByType(eventType)).hasSize(2);
    assertThat(modelInstance.getModelElementsByType(taskType)).hasSize(1);
  }

  @Test
  public void testCreateProcessWithParallelGateway() {
    modelInstance =
        Bpmn.createProcess()
            .startEvent()
            .parallelGateway()
            .scriptTask()
            .endEvent()
            .moveToLastGateway()
            .userTask()
            .endEvent()
            .done();

    assertThat(modelInstance.getModelElementsByType(eventType)).hasSize(3);
    assertThat(modelInstance.getModelElementsByType(taskType)).hasSize(2);
    assertThat(modelInstance.getModelElementsByType(gatewayType)).hasSize(1);
  }

  @Test
  public void testCreateProcessWithExclusiveGateway() {
    modelInstance =
        Bpmn.createProcess()
            .startEvent()
            .userTask()
            .exclusiveGateway()
            .condition("approved", "${approved}")
            .serviceTask()
            .endEvent()
            .moveToLastGateway()
            .condition("not approved", "${!approved}")
            .scriptTask()
            .endEvent()
            .done();

    assertThat(modelInstance.getModelElementsByType(eventType)).hasSize(3);
    assertThat(modelInstance.getModelElementsByType(taskType)).hasSize(3);
    assertThat(modelInstance.getModelElementsByType(gatewayType)).hasSize(1);
  }

  @Test
  public void testCreateProcessWithInclusiveGateway() {
    modelInstance =
        Bpmn.createProcess()
            .startEvent()
            .userTask()
            .inclusiveGateway()
            .condition("approved", "${approved}")
            .serviceTask()
            .endEvent()
            .moveToLastGateway()
            .condition("not approved", "${!approved}")
            .scriptTask()
            .endEvent()
            .done();

    final ModelElementType inclusiveGwType =
        modelInstance.getModel().getType(InclusiveGateway.class);

    assertThat(modelInstance.getModelElementsByType(eventType)).hasSize(3);
    assertThat(modelInstance.getModelElementsByType(taskType)).hasSize(3);
    assertThat(modelInstance.getModelElementsByType(inclusiveGwType)).hasSize(1);
  }

  @Test
  public void testCreateProcessWithForkAndJoin() {
    modelInstance =
        Bpmn.createProcess()
            .startEvent()
            .userTask()
            .parallelGateway()
            .serviceTask()
            .parallelGateway()
            .id("join")
            .moveToLastGateway()
            .scriptTask()
            .connectTo("join")
            .userTask()
            .endEvent()
            .done();

    assertThat(modelInstance.getModelElementsByType(eventType)).hasSize(2);
    assertThat(modelInstance.getModelElementsByType(taskType)).hasSize(4);
    assertThat(modelInstance.getModelElementsByType(gatewayType)).hasSize(2);
  }

  @Test
  public void testCreateProcessWithMultipleParallelTask() {
    modelInstance =
        Bpmn.createProcess()
            .startEvent()
            .parallelGateway("fork")
            .userTask()
            .parallelGateway("join")
            .moveToNode("fork")
            .serviceTask()
            .connectTo("join")
            .moveToNode("fork")
            .userTask()
            .connectTo("join")
            .moveToNode("fork")
            .scriptTask()
            .connectTo("join")
            .endEvent()
            .done();

    assertThat(modelInstance.getModelElementsByType(eventType)).hasSize(2);
    assertThat(modelInstance.getModelElementsByType(taskType)).hasSize(4);
    assertThat(modelInstance.getModelElementsByType(gatewayType)).hasSize(2);
  }

  @Test
  public void testExtend() {
    modelInstance =
        Bpmn.createProcess().startEvent().userTask().id("task1").serviceTask().endEvent().done();

    assertThat(modelInstance.getModelElementsByType(taskType)).hasSize(2);

    final UserTask userTask = modelInstance.getModelElementById("task1");
    final SequenceFlow outgoingSequenceFlow = userTask.getOutgoing().iterator().next();
    final FlowNode serviceTask = outgoingSequenceFlow.getTarget();
    userTask.getOutgoing().remove(outgoingSequenceFlow);
    userTask.builder().scriptTask().userTask().connectTo(serviceTask.getId());

    assertThat(modelInstance.getModelElementsByType(taskType)).hasSize(4);
  }

  @Test
  public void testCreateInvoiceProcess() {
    modelInstance =
        Bpmn.createProcess()
            .executable()
            .startEvent()
            .name("Invoice received")
            .userTask()
            .name("Assign Approver")
            .userTask("approveInvoice")
            .name("Approve Invoice")
            .exclusiveGateway()
            .name("Invoice approved?")
            .gatewayDirection(GatewayDirection.Diverging)
            .condition("yes", "${approved}")
            .userTask()
            .name("Prepare Bank Transfer")
            .serviceTask()
            .name("Archive Invoice")
            .endEvent()
            .name("Invoice processed")
            .moveToLastGateway()
            .condition("no", "${!approved}")
            .userTask()
            .name("Review Invoice")
            .exclusiveGateway()
            .name("Review successful?")
            .gatewayDirection(GatewayDirection.Diverging)
            .condition("no", "${!clarified}")
            .endEvent()
            .name("Invoice not processed")
            .moveToLastGateway()
            .condition("yes", "${clarified}")
            .connectTo("approveInvoice")
            .done();
  }

  @Test
  public void testErrorDefinitionsForStartEvent() {
    modelInstance =
        Bpmn.createProcess()
            .startEvent("start")
            .errorEventDefinition("event")
            .error("errorCode")
            .errorEventDefinitionDone()
            .endEvent()
            .done();

    assertErrorEventDefinition("start", "errorCode");
    assertErrorEventDefinitionForErrorVariables("start");
  }

  @Test
  public void testErrorDefinitionsForStartEventWithoutEventDefinitionId() {
    modelInstance =
        Bpmn.createProcess()
            .startEvent("start")
            .errorEventDefinition()
            .error("errorCode")
            .errorEventDefinitionDone()
            .endEvent()
            .done();

    assertErrorEventDefinition("start", "errorCode");
    assertErrorEventDefinitionForErrorVariables("start");
  }

  @Test
  public void testSubProcessBuilder() {
    final BpmnModelInstance modelInstance =
        Bpmn.createProcess()
            .startEvent()
            .subProcess(SUB_PROCESS_ID)
            .embeddedSubProcess()
            .startEvent()
            .userTask()
            .endEvent()
            .subProcessDone()
            .serviceTask(SERVICE_TASK_ID)
            .endEvent()
            .done();

    final SubProcess subProcess = modelInstance.getModelElementById(SUB_PROCESS_ID);
    final ServiceTask serviceTask = modelInstance.getModelElementById(SERVICE_TASK_ID);
    assertThat(subProcess.getChildElementsByType(Event.class)).hasSize(2);
    assertThat(subProcess.getChildElementsByType(Task.class)).hasSize(1);
    assertThat(subProcess.getFlowElements()).hasSize(5);
    assertThat(subProcess.getSucceedingNodes().singleResult()).isEqualTo(serviceTask);
  }

  @Test
  public void testSubProcessBuilderDetached() {
    modelInstance =
        Bpmn.createProcess()
            .startEvent()
            .subProcess(SUB_PROCESS_ID)
            .serviceTask(SERVICE_TASK_ID)
            .endEvent()
            .done();

    final SubProcess subProcess = modelInstance.getModelElementById(SUB_PROCESS_ID);

    subProcess.builder().embeddedSubProcess().startEvent().userTask().endEvent();

    final ServiceTask serviceTask = modelInstance.getModelElementById(SERVICE_TASK_ID);
    assertThat(subProcess.getChildElementsByType(Event.class)).hasSize(2);
    assertThat(subProcess.getChildElementsByType(Task.class)).hasSize(1);
    assertThat(subProcess.getFlowElements()).hasSize(5);
    assertThat(subProcess.getSucceedingNodes().singleResult()).isEqualTo(serviceTask);
  }

  @Test
  public void testSubProcessBuilderNested() {
    modelInstance =
        Bpmn.createProcess()
            .startEvent()
            .subProcess(SUB_PROCESS_ID + 1)
            .embeddedSubProcess()
            .startEvent()
            .userTask()
            .subProcess(SUB_PROCESS_ID + 2)
            .embeddedSubProcess()
            .startEvent()
            .userTask()
            .endEvent()
            .subProcessDone()
            .serviceTask(SERVICE_TASK_ID + 1)
            .endEvent()
            .subProcessDone()
            .serviceTask(SERVICE_TASK_ID + 2)
            .endEvent()
            .done();

    final SubProcess subProcess = modelInstance.getModelElementById(SUB_PROCESS_ID + 1);
    final ServiceTask serviceTask = modelInstance.getModelElementById(SERVICE_TASK_ID + 2);
    assertThat(subProcess.getChildElementsByType(Event.class)).hasSize(2);
    assertThat(subProcess.getChildElementsByType(Task.class)).hasSize(2);
    assertThat(subProcess.getChildElementsByType(SubProcess.class)).hasSize(1);
    assertThat(subProcess.getFlowElements()).hasSize(9);
    assertThat(subProcess.getSucceedingNodes().singleResult()).isEqualTo(serviceTask);

    final SubProcess nestedSubProcess = modelInstance.getModelElementById(SUB_PROCESS_ID + 2);
    final ServiceTask nestedServiceTask = modelInstance.getModelElementById(SERVICE_TASK_ID + 1);
    assertThat(nestedSubProcess.getChildElementsByType(Event.class)).hasSize(2);
    assertThat(nestedSubProcess.getChildElementsByType(Task.class)).hasSize(1);
    assertThat(nestedSubProcess.getFlowElements()).hasSize(5);
    assertThat(nestedSubProcess.getSucceedingNodes().singleResult()).isEqualTo(nestedServiceTask);
  }

  @Test
  public void testSubProcessBuilderWrongScope() {
    try {
      modelInstance = Bpmn.createProcess().startEvent().subProcessDone().endEvent().done();
      fail("Exception expected");
    } catch (final Exception e) {
      assertThat(e).isInstanceOf(BpmnModelException.class);
    }
  }

  @Test
  public void testTransactionBuilder() {
    final BpmnModelInstance modelInstance =
        Bpmn.createProcess()
            .startEvent()
            .transaction(TRANSACTION_ID)
            .method(TransactionMethod.Image)
            .embeddedSubProcess()
            .startEvent()
            .userTask()
            .endEvent()
            .transactionDone()
            .serviceTask(SERVICE_TASK_ID)
            .endEvent()
            .done();

    final Transaction transaction = modelInstance.getModelElementById(TRANSACTION_ID);
    final ServiceTask serviceTask = modelInstance.getModelElementById(SERVICE_TASK_ID);
    assertThat(transaction.getMethod()).isEqualTo(TransactionMethod.Image);
    assertThat(transaction.getChildElementsByType(Event.class)).hasSize(2);
    assertThat(transaction.getChildElementsByType(Task.class)).hasSize(1);
    assertThat(transaction.getFlowElements()).hasSize(5);
    assertThat(transaction.getSucceedingNodes().singleResult()).isEqualTo(serviceTask);
  }

  @Test
  public void testTransactionBuilderDetached() {
    modelInstance =
        Bpmn.createProcess()
            .startEvent()
            .transaction(TRANSACTION_ID)
            .serviceTask(SERVICE_TASK_ID)
            .endEvent()
            .done();

    final Transaction transaction = modelInstance.getModelElementById(TRANSACTION_ID);

    transaction.builder().embeddedSubProcess().startEvent().userTask().endEvent();

    final ServiceTask serviceTask = modelInstance.getModelElementById(SERVICE_TASK_ID);
    assertThat(transaction.getChildElementsByType(Event.class)).hasSize(2);
    assertThat(transaction.getChildElementsByType(Task.class)).hasSize(1);
    assertThat(transaction.getFlowElements()).hasSize(5);
    assertThat(transaction.getSucceedingNodes().singleResult()).isEqualTo(serviceTask);
  }

  @Test
  public void testScriptText() {
    modelInstance =
        Bpmn.createProcess()
            .startEvent()
            .scriptTask("script")
            .scriptFormat("groovy")
            .scriptText("println \"hello, world\";")
            .endEvent()
            .done();

    final ScriptTask scriptTask = modelInstance.getModelElementById("script");
    assertThat(scriptTask.getScriptFormat()).isEqualTo("groovy");
    assertThat(scriptTask.getScript().getTextContent()).isEqualTo("println \"hello, world\";");
  }

  @Test
  public void testMessageStartEvent() {
    modelInstance = Bpmn.createProcess().startEvent("start").message("message").done();

    assertMessageEventDefinition("start", "message");
  }

  @Test
  public void testMessageStartEventWithExistingMessage() {
    modelInstance =
        Bpmn.createProcess()
            .startEvent("start")
            .message("message")
            .subProcess()
            .triggerByEvent()
            .embeddedSubProcess()
            .startEvent("subStart")
            .message("message")
            .subProcessDone()
            .done();

    final Message message = assertMessageEventDefinition("start", "message");
    final Message subMessage = assertMessageEventDefinition("subStart", "message");

    assertThat(message).isEqualTo(subMessage);

    assertOnlyOneMessageExists("message");
  }

  @Test
  public void testIntermediateMessageCatchEvent() {
    modelInstance =
        Bpmn.createProcess().startEvent().intermediateCatchEvent("catch").message("message").done();

    assertMessageEventDefinition("catch", "message");
  }

  @Test
  public void testIntermediateMessageCatchEventWithExistingMessage() {
    modelInstance =
        Bpmn.createProcess()
            .startEvent()
            .intermediateCatchEvent("catch1")
            .message("message")
            .intermediateCatchEvent("catch2")
            .message("message")
            .done();

    final Message message1 = assertMessageEventDefinition("catch1", "message");
    final Message message2 = assertMessageEventDefinition("catch2", "message");

    assertThat(message1).isEqualTo(message2);

    assertOnlyOneMessageExists("message");
  }

  @Test
  public void testMessageEndEvent() {
    modelInstance = Bpmn.createProcess().startEvent().endEvent("end").message("message").done();

    assertMessageEventDefinition("end", "message");
  }

  @Test
  public void testMessageEventDefintionEndEvent() {
    modelInstance =
        Bpmn.createProcess()
            .startEvent()
            .endEvent("end")
            .messageEventDefinition()
            .message("message")
            .done();

    assertMessageEventDefinition("end", "message");
  }

  @Test
  public void testMessageEndEventWithExistingMessage() {
    modelInstance =
        Bpmn.createProcess()
            .startEvent()
            .parallelGateway()
            .endEvent("end1")
            .message("message")
            .moveToLastGateway()
            .endEvent("end2")
            .message("message")
            .done();

    final Message message1 = assertMessageEventDefinition("end1", "message");
    final Message message2 = assertMessageEventDefinition("end2", "message");

    assertThat(message1).isEqualTo(message2);

    assertOnlyOneMessageExists("message");
  }

  @Test
  public void testMessageEventDefinitionEndEventWithExistingMessage() {
    modelInstance =
        Bpmn.createProcess()
            .startEvent()
            .parallelGateway()
            .endEvent("end1")
            .messageEventDefinition()
            .message("message")
            .messageEventDefinitionDone()
            .moveToLastGateway()
            .endEvent("end2")
            .messageEventDefinition()
            .message("message")
            .done();

    final Message message1 = assertMessageEventDefinition("end1", "message");
    final Message message2 = assertMessageEventDefinition("end2", "message");

    assertThat(message1).isEqualTo(message2);

    assertOnlyOneMessageExists("message");
  }

  @Test
  public void testIntermediateMessageThrowEvent() {
    modelInstance =
        Bpmn.createProcess().startEvent().intermediateThrowEvent("throw").message("message").done();

    assertMessageEventDefinition("throw", "message");
  }

  @Test
  public void testIntermediateMessageEventDefintionThrowEvent() {
    modelInstance =
        Bpmn.createProcess()
            .startEvent()
            .intermediateThrowEvent("throw")
            .messageEventDefinition()
            .message("message")
            .done();

    assertMessageEventDefinition("throw", "message");
  }

  @Test
  public void testIntermediateMessageThrowEventWithExistingMessage() {
    modelInstance =
        Bpmn.createProcess()
            .startEvent()
            .intermediateThrowEvent("throw1")
            .message("message")
            .intermediateThrowEvent("throw2")
            .message("message")
            .done();

    final Message message1 = assertMessageEventDefinition("throw1", "message");
    final Message message2 = assertMessageEventDefinition("throw2", "message");

    assertThat(message1).isEqualTo(message2);
    assertOnlyOneMessageExists("message");
  }

  @Test
  public void testIntermediateMessageEventDefintionThrowEventWithExistingMessage() {
    modelInstance =
        Bpmn.createProcess()
            .startEvent()
            .intermediateThrowEvent("throw1")
            .messageEventDefinition()
            .message("message")
            .messageEventDefinitionDone()
            .intermediateThrowEvent("throw2")
            .messageEventDefinition()
            .message("message")
            .messageEventDefinitionDone()
            .done();

    final Message message1 = assertMessageEventDefinition("throw1", "message");
    final Message message2 = assertMessageEventDefinition("throw2", "message");

    assertThat(message1).isEqualTo(message2);
    assertOnlyOneMessageExists("message");
  }

  @Test
  public void testIntermediateMessageThrowEventWithMessageDefinition() {
    modelInstance =
        Bpmn.createProcess()
            .startEvent()
            .intermediateThrowEvent("throw1")
            .messageEventDefinition()
            .id("messageEventDefinition")
            .message("message")
            .done();

    final MessageEventDefinition event =
        modelInstance.getModelElementById("messageEventDefinition");
    assertThat(event.getMessage().getName()).isEqualTo("message");
  }

  @Test
  public void testMessageEventDefinitionWithID() {
    modelInstance =
        Bpmn.createProcess()
            .startEvent()
            .intermediateThrowEvent("throw1")
            .messageEventDefinition("messageEventDefinition")
            .done();

    MessageEventDefinition event = modelInstance.getModelElementById("messageEventDefinition");
    assertThat(event).isNotNull();

    modelInstance =
        Bpmn.createProcess()
            .startEvent()
            .intermediateThrowEvent("throw2")
            .messageEventDefinition()
            .id("messageEventDefinition1")
            .done();

    // ========================================
    // ==============end event=================
    // ========================================
    event = modelInstance.getModelElementById("messageEventDefinition1");
    assertThat(event).isNotNull();
    modelInstance =
        Bpmn.createProcess()
            .startEvent()
            .endEvent("end1")
            .messageEventDefinition("messageEventDefinition")
            .done();

    event = modelInstance.getModelElementById("messageEventDefinition");
    assertThat(event).isNotNull();

    modelInstance =
        Bpmn.createProcess()
            .startEvent()
            .endEvent("end2")
            .messageEventDefinition()
            .id("messageEventDefinition1")
            .done();

    event = modelInstance.getModelElementById("messageEventDefinition1");
    assertThat(event).isNotNull();
  }

  @Test
  public void testReceiveTaskMessage() {
    modelInstance =
        Bpmn.createProcess().startEvent().receiveTask("receive").message("message").done();

    final ReceiveTask receiveTask = modelInstance.getModelElementById("receive");

    final Message message = receiveTask.getMessage();
    assertThat(message).isNotNull();
    assertThat(message.getName()).isEqualTo("message");
  }

  @Test
  public void testReceiveTaskWithExistingMessage() {
    modelInstance =
        Bpmn.createProcess()
            .startEvent()
            .receiveTask("receive1")
            .message("message")
            .receiveTask("receive2")
            .message("message")
            .done();

    final ReceiveTask receiveTask1 = modelInstance.getModelElementById("receive1");
    final Message message1 = receiveTask1.getMessage();

    final ReceiveTask receiveTask2 = modelInstance.getModelElementById("receive2");
    final Message message2 = receiveTask2.getMessage();

    assertThat(message1).isEqualTo(message2);

    assertOnlyOneMessageExists("message");
  }

  @Test
  public void testSendTaskMessage() {
    modelInstance = Bpmn.createProcess().startEvent().sendTask("send").message("message").done();

    final SendTask sendTask = modelInstance.getModelElementById("send");

    final Message message = sendTask.getMessage();
    assertThat(message).isNotNull();
    assertThat(message.getName()).isEqualTo("message");
  }

  @Test
  public void testSendTaskWithExistingMessage() {
    modelInstance =
        Bpmn.createProcess()
            .startEvent()
            .sendTask("send1")
            .message("message")
            .sendTask("send2")
            .message("message")
            .done();

    final SendTask sendTask1 = modelInstance.getModelElementById("send1");
    final Message message1 = sendTask1.getMessage();

    final SendTask sendTask2 = modelInstance.getModelElementById("send2");
    final Message message2 = sendTask2.getMessage();

    assertThat(message1).isEqualTo(message2);

    assertOnlyOneMessageExists("message");
  }

  @Test
  public void testSignalStartEvent() {
    modelInstance = Bpmn.createProcess().startEvent("start").signal("signal").done();

    assertSignalEventDefinition("start", "signal");
  }

  @Test
  public void testSignalStartEventWithExistingSignal() {
    modelInstance =
        Bpmn.createProcess()
            .startEvent("start")
            .signal("signal")
            .subProcess()
            .triggerByEvent()
            .embeddedSubProcess()
            .startEvent("subStart")
            .signal("signal")
            .subProcessDone()
            .done();

    final Signal signal = assertSignalEventDefinition("start", "signal");
    final Signal subSignal = assertSignalEventDefinition("subStart", "signal");

    assertThat(signal).isEqualTo(subSignal);

    assertOnlyOneSignalExists("signal");
  }

  @Test
  public void testIntermediateSignalCatchEvent() {
    modelInstance =
        Bpmn.createProcess().startEvent().intermediateCatchEvent("catch").signal("signal").done();

    assertSignalEventDefinition("catch", "signal");
  }

  @Test
  public void testIntermediateSignalCatchEventWithExistingSignal() {
    modelInstance =
        Bpmn.createProcess()
            .startEvent()
            .intermediateCatchEvent("catch1")
            .signal("signal")
            .intermediateCatchEvent("catch2")
            .signal("signal")
            .done();

    final Signal signal1 = assertSignalEventDefinition("catch1", "signal");
    final Signal signal2 = assertSignalEventDefinition("catch2", "signal");

    assertThat(signal1).isEqualTo(signal2);

    assertOnlyOneSignalExists("signal");
  }

  @Test
  public void testSignalEndEvent() {
    modelInstance = Bpmn.createProcess().startEvent().endEvent("end").signal("signal").done();

    assertSignalEventDefinition("end", "signal");
  }

  @Test
  public void testSignalEndEventWithExistingSignal() {
    modelInstance =
        Bpmn.createProcess()
            .startEvent()
            .parallelGateway()
            .endEvent("end1")
            .signal("signal")
            .moveToLastGateway()
            .endEvent("end2")
            .signal("signal")
            .done();

    final Signal signal1 = assertSignalEventDefinition("end1", "signal");
    final Signal signal2 = assertSignalEventDefinition("end2", "signal");

    assertThat(signal1).isEqualTo(signal2);

    assertOnlyOneSignalExists("signal");
  }

  @Test
  public void testIntermediateSignalThrowEvent() {
    modelInstance =
        Bpmn.createProcess().startEvent().intermediateThrowEvent("throw").signal("signal").done();

    assertSignalEventDefinition("throw", "signal");
  }

  @Test
  public void testIntermediateSignalThrowEventWithExistingSignal() {
    modelInstance =
        Bpmn.createProcess()
            .startEvent()
            .intermediateThrowEvent("throw1")
            .signal("signal")
            .intermediateThrowEvent("throw2")
            .signal("signal")
            .done();

    final Signal signal1 = assertSignalEventDefinition("throw1", "signal");
    final Signal signal2 = assertSignalEventDefinition("throw2", "signal");

    assertThat(signal1).isEqualTo(signal2);

    assertOnlyOneSignalExists("signal");
  }

  @Test
  public void testMessageBoundaryEvent() {
    modelInstance =
        Bpmn.createProcess()
            .startEvent()
            .userTask("task")
            .endEvent()
            .moveToActivity("task") // jump back to user task and attach a boundary event
            .boundaryEvent("boundary")
            .message("message")
            .endEvent("boundaryEnd")
            .done();

    assertMessageEventDefinition("boundary", "message");

    final UserTask userTask = modelInstance.getModelElementById("task");
    final BoundaryEvent boundaryEvent = modelInstance.getModelElementById("boundary");
    final EndEvent boundaryEnd = modelInstance.getModelElementById("boundaryEnd");

    // boundary event is attached to the user task
    assertThat(boundaryEvent.getAttachedTo()).isEqualTo(userTask);

    // boundary event has no incoming sequence flows
    assertThat(boundaryEvent.getIncoming()).isEmpty();

    // the next flow node is the boundary end event
    final List<FlowNode> succeedingNodes = boundaryEvent.getSucceedingNodes().list();
    assertThat(succeedingNodes).containsOnly(boundaryEnd);
  }

  @Test
  public void testMultipleBoundaryEvents() {
    modelInstance =
        Bpmn.createProcess()
            .startEvent()
            .userTask("task")
            .endEvent()
            .moveToActivity("task") // jump back to user task and attach a boundary event
            .boundaryEvent("boundary1")
            .message("message")
            .endEvent("boundaryEnd1")
            .moveToActivity("task") // jump back to user task and attach another boundary event
            .boundaryEvent("boundary2")
            .signal("signal")
            .endEvent("boundaryEnd2")
            .done();

    assertMessageEventDefinition("boundary1", "message");
    assertSignalEventDefinition("boundary2", "signal");

    final UserTask userTask = modelInstance.getModelElementById("task");
    final BoundaryEvent boundaryEvent1 = modelInstance.getModelElementById("boundary1");
    final EndEvent boundaryEnd1 = modelInstance.getModelElementById("boundaryEnd1");
    final BoundaryEvent boundaryEvent2 = modelInstance.getModelElementById("boundary2");
    final EndEvent boundaryEnd2 = modelInstance.getModelElementById("boundaryEnd2");

    // boundary events are attached to the user task
    assertThat(boundaryEvent1.getAttachedTo()).isEqualTo(userTask);
    assertThat(boundaryEvent2.getAttachedTo()).isEqualTo(userTask);

    // boundary events have no incoming sequence flows
    assertThat(boundaryEvent1.getIncoming()).isEmpty();
    assertThat(boundaryEvent2.getIncoming()).isEmpty();

    // the next flow node is the boundary end event
    List<FlowNode> succeedingNodes = boundaryEvent1.getSucceedingNodes().list();
    assertThat(succeedingNodes).containsOnly(boundaryEnd1);
    succeedingNodes = boundaryEvent2.getSucceedingNodes().list();
    assertThat(succeedingNodes).containsOnly(boundaryEnd2);
  }

  @Test
  public void testMultiInstanceLoopCharacteristicsSequential() {
    modelInstance =
        Bpmn.createProcess()
            .startEvent()
            .userTask("task")
            .multiInstance()
            .sequential()
            .cardinality("card")
            .completionCondition("compl")
            .multiInstanceDone()
            .endEvent()
            .done();

    final UserTask userTask = modelInstance.getModelElementById("task");
    final Collection<MultiInstanceLoopCharacteristics> miCharacteristics =
        userTask.getChildElementsByType(MultiInstanceLoopCharacteristics.class);

    assertThat(miCharacteristics).hasSize(1);

    final MultiInstanceLoopCharacteristics miCharacteristic = miCharacteristics.iterator().next();
    assertThat(miCharacteristic.isSequential()).isTrue();
    assertThat(miCharacteristic.getLoopCardinality().getTextContent()).isEqualTo("card");
    assertThat(miCharacteristic.getCompletionCondition().getTextContent()).isEqualTo("compl");
  }

  @Test
  public void testMultiInstanceLoopCharacteristicsParallel() {
    modelInstance =
        Bpmn.createProcess()
            .startEvent()
            .userTask("task")
            .multiInstance()
            .parallel()
            .multiInstanceDone()
            .endEvent()
            .done();

    final UserTask userTask = modelInstance.getModelElementById("task");
    final Collection<MultiInstanceLoopCharacteristics> miCharacteristics =
        userTask.getChildElementsByType(MultiInstanceLoopCharacteristics.class);

    assertThat(miCharacteristics).hasSize(1);

    final MultiInstanceLoopCharacteristics miCharacteristic = miCharacteristics.iterator().next();
    assertThat(miCharacteristic.isSequential()).isFalse();
  }

  @Test
  public void testTimerStartEventWithDate() {
    modelInstance = Bpmn.createProcess().startEvent("start").timerWithDate(TIMER_DATE).done();

    assertTimerWithDate("start", TIMER_DATE);
  }

  @Test
  public void testTimerStartEventWithDuration() {
    modelInstance =
        Bpmn.createProcess().startEvent("start").timerWithDuration(TIMER_DURATION).done();

    assertTimerWithDuration("start", TIMER_DURATION);
  }

  @Test
  public void testTimerStartEventWithCycle() {
    modelInstance = Bpmn.createProcess().startEvent("start").timerWithCycle(TIMER_CYCLE).done();

    assertTimerWithCycle("start", TIMER_CYCLE);
  }

  @Test
  public void testIntermediateTimerCatchEventWithDate() {
    modelInstance =
        Bpmn.createProcess()
            .startEvent()
            .intermediateCatchEvent("catch")
            .timerWithDate(TIMER_DATE)
            .done();

    assertTimerWithDate("catch", TIMER_DATE);
  }

  @Test
  public void testIntermediateTimerCatchEventWithDuration() {
    modelInstance =
        Bpmn.createProcess()
            .startEvent()
            .intermediateCatchEvent("catch")
            .timerWithDuration(TIMER_DURATION)
            .done();

    assertTimerWithDuration("catch", TIMER_DURATION);
  }

  @Test
  public void testIntermediateTimerCatchEventWithCycle() {
    modelInstance =
        Bpmn.createProcess()
            .startEvent()
            .intermediateCatchEvent("catch")
            .timerWithCycle(TIMER_CYCLE)
            .done();

    assertTimerWithCycle("catch", TIMER_CYCLE);
  }

  @Test
  public void testTimerBoundaryEventWithDate() {
    modelInstance =
        Bpmn.createProcess()
            .startEvent()
            .userTask("task")
            .endEvent()
            .moveToActivity("task")
            .boundaryEvent("boundary")
            .timerWithDate(TIMER_DATE)
            .done();

    assertTimerWithDate("boundary", TIMER_DATE);
  }

  @Test
  public void testTimerBoundaryEventWithDuration() {
    modelInstance =
        Bpmn.createProcess()
            .startEvent()
            .userTask("task")
            .endEvent()
            .moveToActivity("task")
            .boundaryEvent("boundary")
            .timerWithDuration(TIMER_DURATION)
            .done();

    assertTimerWithDuration("boundary", TIMER_DURATION);
  }

  @Test
  public void testTimerBoundaryEventWithCycle() {
    modelInstance =
        Bpmn.createProcess()
            .startEvent()
            .userTask("task")
            .endEvent()
            .moveToActivity("task")
            .boundaryEvent("boundary")
            .timerWithCycle(TIMER_CYCLE)
            .done();

    assertTimerWithCycle("boundary", TIMER_CYCLE);
  }

  @Test
  public void testNotCancelingBoundaryEvent() {
    modelInstance =
        Bpmn.createProcess()
            .startEvent()
            .userTask()
            .boundaryEvent("boundary")
            .cancelActivity(false)
            .done();

    final BoundaryEvent boundaryEvent = modelInstance.getModelElementById("boundary");
    assertThat(boundaryEvent.cancelActivity()).isFalse();
  }

  @Test
  public void testCatchAllErrorBoundaryEvent() {
    modelInstance =
        Bpmn.createProcess()
            .startEvent()
            .userTask("task")
            .endEvent()
            .moveToActivity("task")
            .boundaryEvent("boundary")
            .error()
            .endEvent("boundaryEnd")
            .done();

    final ErrorEventDefinition errorEventDefinition =
        assertAndGetSingleEventDefinition("boundary", ErrorEventDefinition.class);
    assertThat(errorEventDefinition.getError()).isNull();
  }

  @Test
  public void testCompensationTask() {
    modelInstance =
        Bpmn.createProcess()
            .startEvent()
            .userTask("task")
            .boundaryEvent("boundary")
            .compensateEventDefinition()
            .compensateEventDefinitionDone()
            .compensationStart()
            .userTask("compensate")
            .name("compensate")
            .compensationDone()
            .endEvent("theend")
            .done();

    // Checking Association
    final Collection<Association> associations =
        modelInstance.getModelElementsByType(Association.class);
    assertThat(associations).hasSize(1);
    final Association association = associations.iterator().next();
    assertThat(association.getSource().getId()).isEqualTo("boundary");
    assertThat(association.getTarget().getId()).isEqualTo("compensate");
    assertThat(association.getAssociationDirection()).isEqualTo(AssociationDirection.One);

    // Checking Sequence flow
    final UserTask task = modelInstance.getModelElementById("task");
    final Collection<SequenceFlow> outgoing = task.getOutgoing();
    assertThat(outgoing).hasSize(1);
    final SequenceFlow flow = outgoing.iterator().next();
    assertThat(flow.getSource().getId()).isEqualTo("task");
    assertThat(flow.getTarget().getId()).isEqualTo("theend");
  }

  @Test
  public void testCompensationTaskWithNewAPI() {
    modelInstance =
        Bpmn.createProcess()
            .startEvent()
            .userTask("task")
            .boundaryEvent("boundary")
            .compensation(c -> c.userTask("compensate"))
            .moveToActivity("task")
            .endEvent("theend")
            .done();

    // Checking Association
    final Collection<Association> associations =
        modelInstance.getModelElementsByType(Association.class);
    assertThat(associations).hasSize(1);
    final Association association = associations.iterator().next();
    assertThat(association.getSource().getId()).isEqualTo("boundary");
    assertThat(association.getTarget().getId()).isEqualTo("compensate");
    assertThat(association.getAssociationDirection()).isEqualTo(AssociationDirection.One);

    // Checking Sequence flow
    final UserTask task = modelInstance.getModelElementById("task");
    final Collection<SequenceFlow> outgoing = task.getOutgoing();
    assertThat(outgoing).hasSize(1);
    final SequenceFlow flow = outgoing.iterator().next();
    assertThat(flow.getSource().getId()).isEqualTo("task");
    assertThat(flow.getTarget().getId()).isEqualTo("theend");
  }

  @Test
  public void testOnlyOneCompensateBoundaryEventAllowed() {
    // given
    final UserTaskBuilder builder =
        Bpmn.createProcess()
            .startEvent()
            .userTask("task")
            .boundaryEvent("boundary")
            .compensateEventDefinition()
            .compensateEventDefinitionDone()
            .compensationStart()
            .userTask("compensate")
            .name("compensate");

    // then
    thrown.expect(BpmnModelException.class);
    thrown.expectMessage(
        "Only single compensation handler allowed. Call compensationDone() to continue main flow.");

    // when
    builder.userTask();
  }

  @Test
  public void testInvalidCompensationStartCall() {
    // given
    final StartEventBuilder builder = Bpmn.createProcess().startEvent();

    // then
    thrown.expect(BpmnModelException.class);
    thrown.expectMessage(
        "Compensation can only be started on a boundary event with a compensation event definition");

    // when
    builder.compensationStart();
  }

  @Test
  public void testInvalidCompensationDoneCall() {
    // given
    final AbstractFlowNodeBuilder builder =
        Bpmn.createProcess()
            .startEvent()
            .userTask("task")
            .boundaryEvent("boundary")
            .compensateEventDefinition()
            .compensateEventDefinitionDone();

    // then
    thrown.expect(BpmnModelException.class);
    thrown.expectMessage("No compensation in progress. Call compensationStart() first.");

    // when
    builder.compensationDone();
  }

  @Test
  public void testErrorBoundaryEvent() {
    modelInstance =
        Bpmn.createProcess()
            .startEvent()
            .userTask("task")
            .endEvent()
            .moveToActivity("task")
            .boundaryEvent("boundary")
            .error("myErrorCode")
            .endEvent("boundaryEnd")
            .done();

    assertErrorEventDefinition("boundary", "myErrorCode");

    final UserTask userTask = modelInstance.getModelElementById("task");
    final BoundaryEvent boundaryEvent = modelInstance.getModelElementById("boundary");
    final EndEvent boundaryEnd = modelInstance.getModelElementById("boundaryEnd");

    // boundary event is attached to the user task
    assertThat(boundaryEvent.getAttachedTo()).isEqualTo(userTask);

    // boundary event has no incoming sequence flows
    assertThat(boundaryEvent.getIncoming()).isEmpty();

    // the next flow node is the boundary end event
    final List<FlowNode> succeedingNodes = boundaryEvent.getSucceedingNodes().list();
    assertThat(succeedingNodes).containsOnly(boundaryEnd);
  }

  @Test
  public void testErrorDefinitionForBoundaryEvent() {
    modelInstance =
        Bpmn.createProcess()
            .startEvent()
            .userTask("task")
            .endEvent()
            .moveToActivity("task")
            .boundaryEvent("boundary")
            .errorEventDefinition("event")
            .error("errorCode")
            .errorEventDefinitionDone()
            .endEvent("boundaryEnd")
            .done();

    assertErrorEventDefinition("boundary", "errorCode");
    assertErrorEventDefinitionForErrorVariables("boundary");
  }

  @Test
  public void testErrorDefinitionForBoundaryEventWithoutEventDefinitionId() {
    modelInstance =
        Bpmn.createProcess()
            .startEvent()
            .userTask("task")
            .endEvent()
            .moveToActivity("task")
            .boundaryEvent("boundary")
            .errorEventDefinition()
            .error("errorCode")
            .errorEventDefinitionDone()
            .endEvent("boundaryEnd")
            .done();

    assertErrorEventDefinition("boundary", "errorCode");
    assertErrorEventDefinitionForErrorVariables("boundary");
  }

  @Test
  public void testErrorEndEvent() {
    modelInstance = Bpmn.createProcess().startEvent().endEvent("end").error("myErrorCode").done();

    assertErrorEventDefinition("end", "myErrorCode");
  }

  @Test
  public void testErrorEndEventWithExistingError() {
    modelInstance =
        Bpmn.createProcess()
            .startEvent()
            .userTask("task")
            .endEvent("end")
            .error("myErrorCode")
            .moveToActivity("task")
            .boundaryEvent("boundary")
            .error("myErrorCode")
            .endEvent("boundaryEnd")
            .done();

    final Error boundaryError = assertErrorEventDefinition("boundary", "myErrorCode");
    final Error endError = assertErrorEventDefinition("end", "myErrorCode");

    assertThat(boundaryError).isEqualTo(endError);

    assertOnlyOneErrorExists("myErrorCode");
  }

  @Test
  public void testErrorStartEvent() {
    modelInstance =
        Bpmn.createProcess()
            .startEvent()
            .endEvent()
            .subProcess()
            .triggerByEvent()
            .embeddedSubProcess()
            .startEvent("subProcessStart")
            .error("myErrorCode")
            .endEvent()
            .done();

    assertErrorEventDefinition("subProcessStart", "myErrorCode");
  }

  @Test
  public void testCatchAllErrorStartEvent() {
    modelInstance =
        Bpmn.createProcess()
            .startEvent()
            .endEvent()
            .subProcess()
            .triggerByEvent()
            .embeddedSubProcess()
            .startEvent("subProcessStart")
            .error()
            .endEvent()
            .done();

    final ErrorEventDefinition errorEventDefinition =
        assertAndGetSingleEventDefinition("subProcessStart", ErrorEventDefinition.class);
    assertThat(errorEventDefinition.getError()).isNull();
  }

  @Test
  public void testCatchAllEscalationBoundaryEvent() {
    modelInstance =
        Bpmn.createProcess()
            .startEvent()
            .userTask("task")
            .endEvent()
            .moveToActivity("task")
            .boundaryEvent("boundary")
            .escalation()
            .endEvent("boundaryEnd")
            .done();

    final EscalationEventDefinition escalationEventDefinition =
        assertAndGetSingleEventDefinition("boundary", EscalationEventDefinition.class);
    assertThat(escalationEventDefinition.getEscalation()).isNull();
  }

  @Test
  public void testEscalationBoundaryEvent() {
    modelInstance =
        Bpmn.createProcess()
            .startEvent()
            .subProcess("subProcess")
            .endEvent()
            .moveToActivity("subProcess")
            .boundaryEvent("boundary")
            .escalation("myEscalationCode")
            .endEvent("boundaryEnd")
            .done();

    assertEscalationEventDefinition("boundary", "myEscalationCode");

    final SubProcess subProcess = modelInstance.getModelElementById("subProcess");
    final BoundaryEvent boundaryEvent = modelInstance.getModelElementById("boundary");
    final EndEvent boundaryEnd = modelInstance.getModelElementById("boundaryEnd");

    // boundary event is attached to the sub process
    assertThat(boundaryEvent.getAttachedTo()).isEqualTo(subProcess);

    // boundary event has no incoming sequence flows
    assertThat(boundaryEvent.getIncoming()).isEmpty();

    // the next flow node is the boundary end event
    final List<FlowNode> succeedingNodes = boundaryEvent.getSucceedingNodes().list();
    assertThat(succeedingNodes).containsOnly(boundaryEnd);
  }

  @Test
  public void testEscalationEndEvent() {
    modelInstance =
        Bpmn.createProcess().startEvent().endEvent("end").escalation("myEscalationCode").done();

    assertEscalationEventDefinition("end", "myEscalationCode");
  }

  @Test
  public void testEscalationStartEvent() {
    modelInstance =
        Bpmn.createProcess()
            .startEvent()
            .endEvent()
            .subProcess()
            .triggerByEvent()
            .embeddedSubProcess()
            .startEvent("subProcessStart")
            .escalation("myEscalationCode")
            .endEvent()
            .done();

    assertEscalationEventDefinition("subProcessStart", "myEscalationCode");
  }

  @Test
  public void testCatchAllEscalationStartEvent() {
    modelInstance =
        Bpmn.createProcess()
            .startEvent()
            .endEvent()
            .subProcess()
            .triggerByEvent()
            .embeddedSubProcess()
            .startEvent("subProcessStart")
            .escalation()
            .endEvent()
            .done();

    final EscalationEventDefinition escalationEventDefinition =
        assertAndGetSingleEventDefinition("subProcessStart", EscalationEventDefinition.class);
    assertThat(escalationEventDefinition.getEscalation()).isNull();
  }

  @Test
  public void testIntermediateEscalationThrowEvent() {
    modelInstance =
        Bpmn.createProcess()
            .startEvent()
            .intermediateThrowEvent("throw")
            .escalation("myEscalationCode")
            .endEvent()
            .done();

    assertEscalationEventDefinition("throw", "myEscalationCode");
  }

  @Test
  public void testEscalationEndEventWithExistingEscalation() {
    modelInstance =
        Bpmn.createProcess()
            .startEvent()
            .userTask("task")
            .endEvent("end")
            .escalation("myEscalationCode")
            .moveToActivity("task")
            .boundaryEvent("boundary")
            .escalation("myEscalationCode")
            .endEvent("boundaryEnd")
            .done();

    final Escalation boundaryEscalation =
        assertEscalationEventDefinition("boundary", "myEscalationCode");
    final Escalation endEscalation = assertEscalationEventDefinition("end", "myEscalationCode");

    assertThat(boundaryEscalation).isEqualTo(endEscalation);

    assertOnlyOneEscalationExists("myEscalationCode");
  }

  @Test
  public void testCompensationStartEvent() {
    modelInstance =
        Bpmn.createProcess()
            .startEvent()
            .endEvent()
            .subProcess()
            .triggerByEvent()
            .embeddedSubProcess()
            .startEvent("subProcessStart")
            .compensation()
            .endEvent()
            .done();

    assertCompensationEventDefinition("subProcessStart");
  }

  @Test
  public void testInterruptingStartEvent() {
    modelInstance =
        Bpmn.createProcess()
            .startEvent()
            .endEvent()
            .subProcess()
            .triggerByEvent()
            .embeddedSubProcess()
            .startEvent("subProcessStart")
            .interrupting(true)
            .error()
            .endEvent()
            .done();

    final StartEvent startEvent = modelInstance.getModelElementById("subProcessStart");
    assertThat(startEvent).isNotNull();
    assertThat(startEvent.isInterrupting()).isTrue();
  }

  @Test
  public void testNonInterruptingStartEvent() {
    modelInstance =
        Bpmn.createProcess()
            .startEvent()
            .endEvent()
            .subProcess()
            .triggerByEvent()
            .embeddedSubProcess()
            .startEvent("subProcessStart")
            .interrupting(false)
            .error()
            .endEvent()
            .done();

    final StartEvent startEvent = modelInstance.getModelElementById("subProcessStart");
    assertThat(startEvent).isNotNull();
    assertThat(startEvent.isInterrupting()).isFalse();
  }

  @Test
  public void testCompensateEventDefintionCatchStartEvent() {
    modelInstance =
        Bpmn.createProcess()
            .startEvent("start")
            .compensateEventDefinition()
            .waitForCompletion(false)
            .compensateEventDefinitionDone()
            .userTask("userTask")
            .endEvent("end")
            .done();

    final CompensateEventDefinition eventDefinition =
        assertAndGetSingleEventDefinition("start", CompensateEventDefinition.class);
    final Activity activity = eventDefinition.getActivity();
    assertThat(activity).isNull();
    assertThat(eventDefinition.isWaitForCompletion()).isFalse();
  }

  @Test
  public void testCompensateEventDefintionCatchBoundaryEvent() {
    modelInstance =
        Bpmn.createProcess()
            .startEvent()
            .userTask("userTask")
            .boundaryEvent("catch")
            .compensateEventDefinition()
            .waitForCompletion(false)
            .compensateEventDefinitionDone()
            .endEvent("end")
            .done();

    final CompensateEventDefinition eventDefinition =
        assertAndGetSingleEventDefinition("catch", CompensateEventDefinition.class);
    final Activity activity = eventDefinition.getActivity();
    assertThat(activity).isNull();
    assertThat(eventDefinition.isWaitForCompletion()).isFalse();
  }

  @Test
  public void testCompensateEventDefintionCatchBoundaryEventWithId() {
    modelInstance =
        Bpmn.createProcess()
            .startEvent()
            .userTask("userTask")
            .boundaryEvent("catch")
            .compensateEventDefinition("foo")
            .waitForCompletion(false)
            .compensateEventDefinitionDone()
            .endEvent("end")
            .done();

    final CompensateEventDefinition eventDefinition =
        assertAndGetSingleEventDefinition("catch", CompensateEventDefinition.class);
    assertThat(eventDefinition.getId()).isEqualTo("foo");
  }

  @Test
  public void testCompensateEventDefintionThrowEndEvent() {
    modelInstance =
        Bpmn.createProcess()
            .startEvent()
            .userTask("userTask")
            .endEvent("end")
            .compensateEventDefinition()
            .activityRef("userTask")
            .waitForCompletion(true)
            .compensateEventDefinitionDone()
            .done();

    final CompensateEventDefinition eventDefinition =
        assertAndGetSingleEventDefinition("end", CompensateEventDefinition.class);
    final Activity activity = eventDefinition.getActivity();
    assertThat(activity).isEqualTo(modelInstance.getModelElementById("userTask"));
    assertThat(eventDefinition.isWaitForCompletion()).isTrue();
  }

  @Test
  public void testCompensateEventDefintionThrowIntermediateEvent() {
    modelInstance =
        Bpmn.createProcess()
            .startEvent()
            .userTask("userTask")
            .intermediateThrowEvent("throw")
            .compensateEventDefinition()
            .activityRef("userTask")
            .waitForCompletion(true)
            .compensateEventDefinitionDone()
            .endEvent("end")
            .done();

    final CompensateEventDefinition eventDefinition =
        assertAndGetSingleEventDefinition("throw", CompensateEventDefinition.class);
    final Activity activity = eventDefinition.getActivity();
    assertThat(activity).isEqualTo(modelInstance.getModelElementById("userTask"));
    assertThat(eventDefinition.isWaitForCompletion()).isTrue();
  }

  @Test
  public void testCompensateEventDefintionThrowIntermediateEventWithId() {
    modelInstance =
        Bpmn.createProcess()
            .startEvent()
            .userTask("userTask")
            .intermediateCatchEvent("throw")
            .compensateEventDefinition("foo")
            .activityRef("userTask")
            .waitForCompletion(true)
            .compensateEventDefinitionDone()
            .endEvent("end")
            .done();

    final CompensateEventDefinition eventDefinition =
        assertAndGetSingleEventDefinition("throw", CompensateEventDefinition.class);
    assertThat(eventDefinition.getId()).isEqualTo("foo");
  }

  @Test
  public void testCompensateEventDefintionReferencesNonExistingActivity() {
    modelInstance = Bpmn.createProcess().startEvent().userTask("userTask").endEvent("end").done();

    final UserTask userTask = modelInstance.getModelElementById("userTask");
    final UserTaskBuilder userTaskBuilder = userTask.builder();

    try {
      userTaskBuilder
          .boundaryEvent()
          .compensateEventDefinition()
          .activityRef("nonExistingTask")
          .done();
      fail("should fail");
    } catch (final BpmnModelException e) {
      assertThat(e).hasMessageContaining("Activity with id 'nonExistingTask' does not exist");
    }
  }

  @Test
  public void testCompensateEventDefintionReferencesActivityInDifferentScope() {
    modelInstance =
        Bpmn.createProcess()
            .startEvent()
            .userTask("userTask")
            .subProcess()
            .embeddedSubProcess()
            .startEvent()
            .userTask("subProcessTask")
            .endEvent()
            .subProcessDone()
            .endEvent("throw")
            .compensateEventDefinition()
            .activityRef("subProcessTask")
            .done();

    final CompensateEventDefinition eventDefinition =
        assertAndGetSingleEventDefinition("throw", CompensateEventDefinition.class);
    assertThat(eventDefinition.getActivity().getId()).isEqualTo("subProcessTask");
  }

  @Test
  public void testIntermediateConditionalEventDefinition() {

    modelInstance =
        Bpmn.createProcess()
            .startEvent()
            .intermediateCatchEvent(CATCH_ID)
            .conditionalEventDefinition(CONDITION_ID)
            .condition(TEST_CONDITION)
            .conditionalEventDefinitionDone()
            .endEvent()
            .done();

    final ConditionalEventDefinition eventDefinition =
        assertAndGetSingleEventDefinition(CATCH_ID, ConditionalEventDefinition.class);
    assertThat(eventDefinition.getId()).isEqualTo(CONDITION_ID);
    assertThat(eventDefinition.getCondition().getTextContent()).isEqualTo(TEST_CONDITION);
  }

  @Test
  public void testIntermediateConditionalEventDefinitionShortCut() {

    modelInstance =
        Bpmn.createProcess()
            .startEvent()
            .intermediateCatchEvent(CATCH_ID)
            .condition(TEST_CONDITION)
            .endEvent()
            .done();

    final ConditionalEventDefinition eventDefinition =
        assertAndGetSingleEventDefinition(CATCH_ID, ConditionalEventDefinition.class);
    assertThat(eventDefinition.getCondition().getTextContent()).isEqualTo(TEST_CONDITION);
  }

  @Test
  public void testBoundaryConditionalEventDefinition() {

    modelInstance =
        Bpmn.createProcess()
            .startEvent()
            .userTask(USER_TASK_ID)
            .endEvent()
            .moveToActivity(USER_TASK_ID)
            .boundaryEvent(BOUNDARY_ID)
            .conditionalEventDefinition(CONDITION_ID)
            .condition(TEST_CONDITION)
            .conditionalEventDefinitionDone()
            .endEvent()
            .done();

    final ConditionalEventDefinition eventDefinition =
        assertAndGetSingleEventDefinition(BOUNDARY_ID, ConditionalEventDefinition.class);
    assertThat(eventDefinition.getId()).isEqualTo(CONDITION_ID);
    assertThat(eventDefinition.getCondition().getTextContent()).isEqualTo(TEST_CONDITION);
  }

  @Test
  public void testEventSubProcessConditionalStartEvent() {

    modelInstance =
        Bpmn.createProcess()
            .startEvent()
            .userTask()
            .endEvent()
            .subProcess()
            .triggerByEvent()
            .embeddedSubProcess()
            .startEvent(START_EVENT_ID)
            .conditionalEventDefinition(CONDITION_ID)
            .condition(TEST_CONDITION)
            .conditionalEventDefinitionDone()
            .endEvent()
            .done();

    final ConditionalEventDefinition eventDefinition =
        assertAndGetSingleEventDefinition(START_EVENT_ID, ConditionalEventDefinition.class);
    assertThat(eventDefinition.getId()).isEqualTo(CONDITION_ID);
    assertThat(eventDefinition.getCondition().getTextContent()).isEqualTo(TEST_CONDITION);
  }

  @Test
  public void testMoveToDoesNotReturnRawBuilders() {
    // just checks that it compiles
    Bpmn.createProcess()
        .startEvent("goto")
        .moveToNode("goto")
        .serviceTask("task", b -> b.name("name"));

    Bpmn.createProcess()
        .startEvent()
        .exclusiveGateway()
        .userTask()
        .moveToLastExclusiveGateway()
        .serviceTask("task", b -> b.name("name"));

    Bpmn.createProcess()
        .startEvent()
        .parallelGateway()
        .userTask()
        .moveToLastGateway()
        .serviceTask("task", b -> b.name("name"));

    Bpmn.createProcess()
        .startEvent()
        .serviceTask("goto")
        .userTask()
        .moveToActivity("goto")
        .serviceTask("task", b -> b.name("name"));
  }

  /** or else generic types in parameters are not available and things won't compile */
  @Test
  public void testConnectToDoesNotReturnRawBuilder() {
    Bpmn.createProcess()
        .startEvent()
        .serviceTask("goto")
        .connectTo("goto")
        .serviceTask("task", b -> b.name("name"))
        .done();
  }

  @Test
  public void testSetZeebeProperty() {
    final ProcessBuilder processBuilder =
        Bpmn.createExecutableProcess("process")
            .zeebeProperty("processProperty", "processPropertyValue");

    processBuilder
        .startEvent("Start_Event")
        .zeebeProperty("startEventProperty", "startEventPropertyValue")
        .userTask("User_Task")
        .zeebeProperty("userTaskProperty", "userTaskPropertyValue")
        .serviceTask("Service_Task")
        .zeebeProperty("serviceTaskProperty1", "serviceTaskPropertyValue1")
        .zeebeProperty("serviceTaskProperty2", "serviceTaskPropertyValue2")
        .exclusiveGateway("Exclusive_Gateway")
        .zeebeProperty("gatewayProperty", "gatewayPropertyValue")
        .defaultFlow()
        .adHocSubProcess(
            "Ad_Hoc_Sub_Process",
            ahsp -> {
              ahsp.zeebeProperty("adHocSubProcessProperty", "adHocSubProcessPropertyValue")
                  .task("Ad_Hoc_Task")
                  .zeebeProperty("adHocTaskProperty", "adHocTaskPropertyValue");
            });

    processBuilder.eventSubProcess(
        "Event_Subprocess",
        eventSubProcess -> {
          eventSubProcess
              .zeebeProperty("eventSubProcessProperty", "eventSubProcessPropertyValue")
              .startEvent()
              .userTask()
              .endEvent();
        });

    modelInstance = processBuilder.done();

    assertThat(getExtensionProperties("process"))
        .containsExactly(entry("processProperty", "processPropertyValue"));

    assertThat(getExtensionProperties("Start_Event"))
        .containsExactly(entry("startEventProperty", "startEventPropertyValue"));

    assertThat(getExtensionProperties("User_Task"))
        .containsExactly(entry("userTaskProperty", "userTaskPropertyValue"));

    assertThat(getExtensionProperties("Service_Task"))
        .containsOnly(
            entry("serviceTaskProperty1", "serviceTaskPropertyValue1"),
            entry("serviceTaskProperty2", "serviceTaskPropertyValue2"));

    assertThat(getExtensionProperties("Exclusive_Gateway"))
        .containsExactly(entry("gatewayProperty", "gatewayPropertyValue"));

    assertThat(getExtensionProperties("Ad_Hoc_Sub_Process"))
        .containsExactly(entry("adHocSubProcessProperty", "adHocSubProcessPropertyValue"));

    assertThat(getExtensionProperties("Ad_Hoc_Task"))
        .containsExactly(entry("adHocTaskProperty", "adHocTaskPropertyValue"));

    assertThat(getExtensionProperties("Event_Subprocess"))
        .containsExactly(entry("eventSubProcessProperty", "eventSubProcessPropertyValue"));
  }

  protected Message assertMessageEventDefinition(final String elementId, final String messageName) {
    final MessageEventDefinition messageEventDefinition =
        assertAndGetSingleEventDefinition(elementId, MessageEventDefinition.class);
    final Message message = messageEventDefinition.getMessage();
    assertThat(message).isNotNull();
    assertThat(message.getName()).isEqualTo(messageName);

    return message;
  }

  protected void assertOnlyOneMessageExists(final String messageName) {
    final Collection<Message> messages = modelInstance.getModelElementsByType(Message.class);
    assertThat(messages).extracting("name").containsOnlyOnce(messageName);
  }

  protected Signal assertSignalEventDefinition(final String elementId, final String signalName) {
    final SignalEventDefinition signalEventDefinition =
        assertAndGetSingleEventDefinition(elementId, SignalEventDefinition.class);
    final Signal signal = signalEventDefinition.getSignal();
    assertThat(signal).isNotNull();
    assertThat(signal.getName()).isEqualTo(signalName);

    return signal;
  }

  protected void assertOnlyOneSignalExists(final String signalName) {
    final Collection<Signal> signals = modelInstance.getModelElementsByType(Signal.class);
    assertThat(signals).extracting("name").containsOnlyOnce(signalName);
  }

  protected Error assertErrorEventDefinition(final String elementId, final String errorCode) {
    final ErrorEventDefinition errorEventDefinition =
        assertAndGetSingleEventDefinition(elementId, ErrorEventDefinition.class);
    final Error error = errorEventDefinition.getError();
    assertThat(error).isNotNull();
    assertThat(error.getErrorCode()).isEqualTo(errorCode);

    return error;
  }

  protected void assertErrorEventDefinitionForErrorVariables(final String elementId) {
    final ErrorEventDefinition errorEventDefinition =
        assertAndGetSingleEventDefinition(elementId, ErrorEventDefinition.class);
    assertThat(errorEventDefinition).isNotNull();
  }

  protected void assertOnlyOneErrorExists(final String errorCode) {
    final Collection<Error> errors = modelInstance.getModelElementsByType(Error.class);
    assertThat(errors).extracting("errorCode").containsOnlyOnce(errorCode);
  }

  protected Escalation assertEscalationEventDefinition(
      final String elementId, final String escalationCode) {
    final EscalationEventDefinition escalationEventDefinition =
        assertAndGetSingleEventDefinition(elementId, EscalationEventDefinition.class);
    final Escalation escalation = escalationEventDefinition.getEscalation();
    assertThat(escalation).isNotNull();
    assertThat(escalation.getEscalationCode()).isEqualTo(escalationCode);

    return escalation;
  }

  protected void assertOnlyOneEscalationExists(final String escalationCode) {
    final Collection<Escalation> escalations =
        modelInstance.getModelElementsByType(Escalation.class);
    assertThat(escalations).extracting("escalationCode").containsOnlyOnce(escalationCode);
  }

  protected void assertCompensationEventDefinition(final String elementId) {
    assertAndGetSingleEventDefinition(elementId, CompensateEventDefinition.class);
  }

  protected void assertTimerWithDate(final String elementId, final String timerDate) {
    final TimerEventDefinition timerEventDefinition =
        assertAndGetSingleEventDefinition(elementId, TimerEventDefinition.class);
    final TimeDate timeDate = timerEventDefinition.getTimeDate();
    assertThat(timeDate).isNotNull();
    assertThat(timeDate.getTextContent()).isEqualTo(timerDate);
  }

  protected void assertTimerWithDuration(final String elementId, final String timerDuration) {
    final TimerEventDefinition timerEventDefinition =
        assertAndGetSingleEventDefinition(elementId, TimerEventDefinition.class);
    final TimeDuration timeDuration = timerEventDefinition.getTimeDuration();
    assertThat(timeDuration).isNotNull();
    assertThat(timeDuration.getTextContent()).isEqualTo(timerDuration);
  }

  protected void assertTimerWithCycle(final String elementId, final String timerCycle) {
    final TimerEventDefinition timerEventDefinition =
        assertAndGetSingleEventDefinition(elementId, TimerEventDefinition.class);
    final TimeCycle timeCycle = timerEventDefinition.getTimeCycle();
    assertThat(timeCycle).isNotNull();
    assertThat(timeCycle.getTextContent()).isEqualTo(timerCycle);
  }

  protected <T extends EventDefinition> T assertAndGetSingleEventDefinition(
      final String elementId, final Class<T> eventDefinitionType) {
    final BpmnModelElementInstance element = modelInstance.getModelElementById(elementId);
    assertThat(element).isNotNull();
    final Collection<EventDefinition> eventDefinitions =
        element.getChildElementsByType(EventDefinition.class);
    assertThat(eventDefinitions).hasSize(1);

    final EventDefinition eventDefinition = eventDefinitions.iterator().next();
    assertThat(eventDefinition).isNotNull().isInstanceOf(eventDefinitionType);
    return (T) eventDefinition;
  }

  protected Map<String, String> getExtensionProperties(final String elementId) {
    final BaseElement element = modelInstance.getModelElementById(elementId);
    return Optional.ofNullable(element.getSingleExtensionElement(ZeebeProperties.class))
        .map(ZeebeProperties::getProperties)
        .map(
            properties ->
                properties.stream()
                    .collect(Collectors.toMap(ZeebeProperty::getName, ZeebeProperty::getValue)))
        .orElseGet(Collections::emptyMap);
  }

  @Test
  public void testCreateEventSubProcess() {
    final ProcessBuilder process = Bpmn.createProcess();
    modelInstance = process.startEvent().sendTask().endEvent().done();

    final EventSubProcessBuilder eventSubProcess = process.eventSubProcess();
    eventSubProcess.startEvent().userTask().endEvent();

    final SubProcess subProcess = eventSubProcess.getElement();

    // no input or output from the sub process
    assertThat(subProcess.getIncoming()).isEmpty();
    assertThat(subProcess.getOutgoing()).isEmpty();

    // subProcess was triggered by event
    assertThat(eventSubProcess.getElement().triggeredByEvent()).isTrue();

    // subProcess contains startEvent, sendTask and endEvent
    assertThat(subProcess.getChildElementsByType(StartEvent.class)).isNotNull();
    assertThat(subProcess.getChildElementsByType(UserTask.class)).isNotNull();
    assertThat(subProcess.getChildElementsByType(EndEvent.class)).isNotNull();
  }

  @Test
  public void testCreateEventSubProcessInSubProcess() {
    final ProcessBuilder process = Bpmn.createProcess();
    modelInstance =
        process
            .startEvent()
            .subProcess("mysubprocess")
            .embeddedSubProcess()
            .startEvent()
            .userTask()
            .endEvent()
            .subProcessDone()
            .userTask()
            .endEvent()
            .done();

    final SubProcess subprocess = modelInstance.getModelElementById("mysubprocess");
    subprocess
        .builder()
        .embeddedSubProcess()
        .eventSubProcess("myeventsubprocess")
        .startEvent()
        .userTask()
        .endEvent()
        .subProcessDone();

    final SubProcess eventSubProcess = modelInstance.getModelElementById("myeventsubprocess");

    // no input or output from the sub process
    assertThat(eventSubProcess.getIncoming()).isEmpty();
    assertThat(eventSubProcess.getOutgoing()).isEmpty();

    // subProcess was triggered by event
    assertThat(eventSubProcess.triggeredByEvent()).isTrue();

    // subProcess contains startEvent, sendTask and endEvent
    assertThat(eventSubProcess.getChildElementsByType(StartEvent.class)).isNotNull();
    assertThat(eventSubProcess.getChildElementsByType(UserTask.class)).isNotNull();
    assertThat(eventSubProcess.getChildElementsByType(EndEvent.class)).isNotNull();
  }

  @Test
  public void testCreateEventSubProcessError() {
    final ProcessBuilder process = Bpmn.createProcess();
    modelInstance = process.startEvent().sendTask().endEvent().done();

    final EventSubProcessBuilder eventSubProcess = process.eventSubProcess();
    eventSubProcess.startEvent().userTask().endEvent();

    try {
      eventSubProcess.subProcessDone();
      fail("eventSubProcess has returned a builder after completion");
    } catch (final BpmnModelException e) {
      assertThat(e).hasMessageContaining("Unable to find a parent subProcess.");
    }
  }

  @Test
  public void testSetIdAsDefaultNameForFlowElements() {
    final BpmnModelInstance instance =
        Bpmn.createExecutableProcess("process")
            .startEvent("start")
            .userTask("user")
            .endEvent("end")
            .name("name")
            .done();

    final String startName = ((FlowElement) instance.getModelElementById("start")).getName();
    assertThat(startName).isEqualTo("start");
    final String userName = ((FlowElement) instance.getModelElementById("user")).getName();
    assertThat(userName).isEqualTo("user");
    final String endName = ((FlowElement) instance.getModelElementById("end")).getName();
    assertThat(endName).isEqualTo("name");
  }
}
