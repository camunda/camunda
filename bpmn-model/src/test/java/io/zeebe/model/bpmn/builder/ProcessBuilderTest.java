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

package io.zeebe.model.bpmn.builder;

import static io.zeebe.model.bpmn.BpmnTestConstants.BOUNDARY_ID;
import static io.zeebe.model.bpmn.BpmnTestConstants.CALL_ACTIVITY_ID;
import static io.zeebe.model.bpmn.BpmnTestConstants.CATCH_ID;
import static io.zeebe.model.bpmn.BpmnTestConstants.CONDITION_ID;
import static io.zeebe.model.bpmn.BpmnTestConstants.EXTERNAL_TASK_ID;
import static io.zeebe.model.bpmn.BpmnTestConstants.PROCESS_ID;
import static io.zeebe.model.bpmn.BpmnTestConstants.SERVICE_TASK_ID;
import static io.zeebe.model.bpmn.BpmnTestConstants.START_EVENT_ID;
import static io.zeebe.model.bpmn.BpmnTestConstants.SUB_PROCESS_ID;
import static io.zeebe.model.bpmn.BpmnTestConstants.TASK_ID;
import static io.zeebe.model.bpmn.BpmnTestConstants.TEST_CLASS_API;
import static io.zeebe.model.bpmn.BpmnTestConstants.TEST_CONDITION;
import static io.zeebe.model.bpmn.BpmnTestConstants.TEST_CONDITIONAL_VARIABLE_EVENTS;
import static io.zeebe.model.bpmn.BpmnTestConstants.TEST_CONDITIONAL_VARIABLE_EVENTS_LIST;
import static io.zeebe.model.bpmn.BpmnTestConstants.TEST_CONDITIONAL_VARIABLE_NAME;
import static io.zeebe.model.bpmn.BpmnTestConstants.TEST_DELEGATE_EXPRESSION_API;
import static io.zeebe.model.bpmn.BpmnTestConstants.TEST_DUE_DATE_API;
import static io.zeebe.model.bpmn.BpmnTestConstants.TEST_EXPRESSION_API;
import static io.zeebe.model.bpmn.BpmnTestConstants.TEST_EXTERNAL_TASK_TOPIC;
import static io.zeebe.model.bpmn.BpmnTestConstants.TEST_FOLLOW_UP_DATE_API;
import static io.zeebe.model.bpmn.BpmnTestConstants.TEST_GROUPS_API;
import static io.zeebe.model.bpmn.BpmnTestConstants.TEST_GROUPS_LIST_API;
import static io.zeebe.model.bpmn.BpmnTestConstants.TEST_HISTORY_TIME_TO_LIVE;
import static io.zeebe.model.bpmn.BpmnTestConstants.TEST_PRIORITY_API;
import static io.zeebe.model.bpmn.BpmnTestConstants.TEST_PROCESS_TASK_PRIORITY;
import static io.zeebe.model.bpmn.BpmnTestConstants.TEST_SERVICE_TASK_PRIORITY;
import static io.zeebe.model.bpmn.BpmnTestConstants.TEST_STARTABLE_IN_TASKLIST;
import static io.zeebe.model.bpmn.BpmnTestConstants.TEST_STRING_API;
import static io.zeebe.model.bpmn.BpmnTestConstants.TEST_USERS_API;
import static io.zeebe.model.bpmn.BpmnTestConstants.TEST_USERS_LIST_API;
import static io.zeebe.model.bpmn.BpmnTestConstants.TEST_VERSION_TAG;
import static io.zeebe.model.bpmn.BpmnTestConstants.TRANSACTION_ID;
import static io.zeebe.model.bpmn.BpmnTestConstants.USER_TASK_ID;
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.BPMN20_NS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Fail.fail;
import static org.junit.Assert.assertEquals;

import io.zeebe.model.bpmn.AssociationDirection;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelException;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.model.bpmn.GatewayDirection;
import io.zeebe.model.bpmn.TransactionMethod;
import io.zeebe.model.bpmn.instance.Activity;
import io.zeebe.model.bpmn.instance.Association;
import io.zeebe.model.bpmn.instance.BaseElement;
import io.zeebe.model.bpmn.instance.BoundaryEvent;
import io.zeebe.model.bpmn.instance.BpmnModelElementInstance;
import io.zeebe.model.bpmn.instance.BusinessRuleTask;
import io.zeebe.model.bpmn.instance.CallActivity;
import io.zeebe.model.bpmn.instance.CompensateEventDefinition;
import io.zeebe.model.bpmn.instance.ConditionalEventDefinition;
import io.zeebe.model.bpmn.instance.Definitions;
import io.zeebe.model.bpmn.instance.EndEvent;
import io.zeebe.model.bpmn.instance.Error;
import io.zeebe.model.bpmn.instance.ErrorEventDefinition;
import io.zeebe.model.bpmn.instance.Escalation;
import io.zeebe.model.bpmn.instance.EscalationEventDefinition;
import io.zeebe.model.bpmn.instance.Event;
import io.zeebe.model.bpmn.instance.EventDefinition;
import io.zeebe.model.bpmn.instance.ExtensionElements;
import io.zeebe.model.bpmn.instance.FlowElement;
import io.zeebe.model.bpmn.instance.FlowNode;
import io.zeebe.model.bpmn.instance.Gateway;
import io.zeebe.model.bpmn.instance.InclusiveGateway;
import io.zeebe.model.bpmn.instance.Message;
import io.zeebe.model.bpmn.instance.MessageEventDefinition;
import io.zeebe.model.bpmn.instance.MultiInstanceLoopCharacteristics;
import io.zeebe.model.bpmn.instance.Process;
import io.zeebe.model.bpmn.instance.ReceiveTask;
import io.zeebe.model.bpmn.instance.ScriptTask;
import io.zeebe.model.bpmn.instance.SendTask;
import io.zeebe.model.bpmn.instance.SequenceFlow;
import io.zeebe.model.bpmn.instance.ServiceTask;
import io.zeebe.model.bpmn.instance.Signal;
import io.zeebe.model.bpmn.instance.SignalEventDefinition;
import io.zeebe.model.bpmn.instance.StartEvent;
import io.zeebe.model.bpmn.instance.SubProcess;
import io.zeebe.model.bpmn.instance.Task;
import io.zeebe.model.bpmn.instance.TimeCycle;
import io.zeebe.model.bpmn.instance.TimeDate;
import io.zeebe.model.bpmn.instance.TimeDuration;
import io.zeebe.model.bpmn.instance.TimerEventDefinition;
import io.zeebe.model.bpmn.instance.Transaction;
import io.zeebe.model.bpmn.instance.UserTask;
import io.zeebe.model.bpmn.instance.camunda.CamundaExecutionListener;
import io.zeebe.model.bpmn.instance.camunda.CamundaFailedJobRetryTimeCycle;
import io.zeebe.model.bpmn.instance.camunda.CamundaFormData;
import io.zeebe.model.bpmn.instance.camunda.CamundaFormField;
import io.zeebe.model.bpmn.instance.camunda.CamundaIn;
import io.zeebe.model.bpmn.instance.camunda.CamundaInputOutput;
import io.zeebe.model.bpmn.instance.camunda.CamundaInputParameter;
import io.zeebe.model.bpmn.instance.camunda.CamundaOut;
import io.zeebe.model.bpmn.instance.camunda.CamundaOutputParameter;
import io.zeebe.model.bpmn.instance.camunda.CamundaTaskListener;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.camunda.bpm.model.xml.Model;
import org.camunda.bpm.model.xml.instance.ModelElementInstance;
import org.camunda.bpm.model.xml.type.ModelElementType;
import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

/** @author Sebastian Menski */
public class ProcessBuilderTest {

  public static final String TIMER_DATE = "2011-03-11T12:13:14Z";
  public static final String TIMER_DURATION = "P10D";
  public static final String TIMER_CYCLE = "R3/PT10H";

  public static final String FAILED_JOB_RETRY_TIME_CYCLE = "R5/PT1M";

  @Rule public ExpectedException thrown = ExpectedException.none();

  private BpmnModelInstance modelInstance;
  private static ModelElementType taskType;
  private static ModelElementType gatewayType;
  private static ModelElementType eventType;
  private static ModelElementType processType;

  @BeforeClass
  public static void getElementTypes() {
    final Model model = Bpmn.createEmptyModel().getModel();
    taskType = model.getType(Task.class);
    gatewayType = model.getType(Gateway.class);
    eventType = model.getType(Event.class);
    processType = model.getType(Process.class);
  }

  @After
  public void validateModel() throws IOException {
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
            .camundaFormKey("embedded:app:forms/start-form.html")
            .userTask()
            .name("Assign Approver")
            .camundaFormKey("embedded:app:forms/assign-approver.html")
            .camundaAssignee("demo")
            .userTask("approveInvoice")
            .name("Approve Invoice")
            .camundaFormKey("embedded:app:forms/approve-invoice.html")
            .camundaAssignee("${approver}")
            .exclusiveGateway()
            .name("Invoice approved?")
            .gatewayDirection(GatewayDirection.Diverging)
            .condition("yes", "${approved}")
            .userTask()
            .name("Prepare Bank Transfer")
            .camundaFormKey("embedded:app:forms/prepare-bank-transfer.html")
            .camundaCandidateGroups("accounting")
            .serviceTask()
            .name("Archive Invoice")
            .camundaClass("org.camunda.bpm.example.invoice.service.ArchiveInvoiceService")
            .endEvent()
            .name("Invoice processed")
            .moveToLastGateway()
            .condition("no", "${!approved}")
            .userTask()
            .name("Review Invoice")
            .camundaFormKey("embedded:app:forms/review-invoice.html")
            .camundaAssignee("demo")
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
  public void testProcessCamundaExtensions() {
    modelInstance =
        Bpmn.createProcess(PROCESS_ID)
            .camundaJobPriority("${somePriority}")
            .camundaTaskPriority(TEST_PROCESS_TASK_PRIORITY)
            .camundaHistoryTimeToLive(TEST_HISTORY_TIME_TO_LIVE)
            .camundaStartableInTasklist(TEST_STARTABLE_IN_TASKLIST)
            .camundaVersionTag(TEST_VERSION_TAG)
            .startEvent()
            .endEvent()
            .done();

    final Process process = modelInstance.getModelElementById(PROCESS_ID);
    assertThat(process.getCamundaJobPriority()).isEqualTo("${somePriority}");
    assertThat(process.getCamundaTaskPriority()).isEqualTo(TEST_PROCESS_TASK_PRIORITY);
    assertThat(process.getCamundaHistoryTimeToLive()).isEqualTo(TEST_HISTORY_TIME_TO_LIVE);
    assertThat(process.isCamundaStartableInTasklist()).isEqualTo(TEST_STARTABLE_IN_TASKLIST);
    assertThat(process.getCamundaVersionTag()).isEqualTo(TEST_VERSION_TAG);
  }

  @Test
  public void testProcessStartableInTasklist() {
    modelInstance = Bpmn.createProcess(PROCESS_ID).startEvent().endEvent().done();

    final Process process = modelInstance.getModelElementById(PROCESS_ID);
    assertThat(process.isCamundaStartableInTasklist()).isEqualTo(true);
  }

  @Test
  public void testTaskCamundaExternalTask() {
    modelInstance =
        Bpmn.createProcess()
            .startEvent()
            .serviceTask(EXTERNAL_TASK_ID)
            .camundaExternalTask(TEST_EXTERNAL_TASK_TOPIC)
            .endEvent()
            .done();

    final ServiceTask serviceTask = modelInstance.getModelElementById(EXTERNAL_TASK_ID);
    assertThat(serviceTask.getCamundaType()).isEqualTo("external");
    assertThat(serviceTask.getCamundaTopic()).isEqualTo(TEST_EXTERNAL_TASK_TOPIC);
  }

  @Test
  public void testTaskCamundaExtensions() {
    modelInstance =
        Bpmn.createProcess()
            .startEvent()
            .serviceTask(TASK_ID)
            .camundaAsyncBefore()
            .notCamundaExclusive()
            .camundaJobPriority("${somePriority}")
            .camundaTaskPriority(TEST_SERVICE_TASK_PRIORITY)
            .camundaFailedJobRetryTimeCycle(FAILED_JOB_RETRY_TIME_CYCLE)
            .endEvent()
            .done();

    final ServiceTask serviceTask = modelInstance.getModelElementById(TASK_ID);
    assertThat(serviceTask.isCamundaAsyncBefore()).isTrue();
    assertThat(serviceTask.isCamundaExclusive()).isFalse();
    assertThat(serviceTask.getCamundaJobPriority()).isEqualTo("${somePriority}");
    assertThat(serviceTask.getCamundaTaskPriority()).isEqualTo(TEST_SERVICE_TASK_PRIORITY);

    assertCamundaFailedJobRetryTimeCycle(serviceTask);
  }

  @Test
  public void testServiceTaskCamundaExtensions() {
    modelInstance =
        Bpmn.createProcess()
            .startEvent()
            .serviceTask(TASK_ID)
            .camundaClass(TEST_CLASS_API)
            .camundaDelegateExpression(TEST_DELEGATE_EXPRESSION_API)
            .camundaExpression(TEST_EXPRESSION_API)
            .camundaResultVariable(TEST_STRING_API)
            .camundaTopic(TEST_STRING_API)
            .camundaType(TEST_STRING_API)
            .camundaTaskPriority(TEST_SERVICE_TASK_PRIORITY)
            .camundaFailedJobRetryTimeCycle(FAILED_JOB_RETRY_TIME_CYCLE)
            .done();

    final ServiceTask serviceTask = modelInstance.getModelElementById(TASK_ID);
    assertThat(serviceTask.getCamundaClass()).isEqualTo(TEST_CLASS_API);
    assertThat(serviceTask.getCamundaDelegateExpression()).isEqualTo(TEST_DELEGATE_EXPRESSION_API);
    assertThat(serviceTask.getCamundaExpression()).isEqualTo(TEST_EXPRESSION_API);
    assertThat(serviceTask.getCamundaResultVariable()).isEqualTo(TEST_STRING_API);
    assertThat(serviceTask.getCamundaTopic()).isEqualTo(TEST_STRING_API);
    assertThat(serviceTask.getCamundaType()).isEqualTo(TEST_STRING_API);
    assertThat(serviceTask.getCamundaTaskPriority()).isEqualTo(TEST_SERVICE_TASK_PRIORITY);

    assertCamundaFailedJobRetryTimeCycle(serviceTask);
  }

  @Test
  public void testServiceTaskCamundaClass() {
    modelInstance =
        Bpmn.createProcess()
            .startEvent()
            .serviceTask(TASK_ID)
            .camundaClass(getClass().getName())
            .done();

    final ServiceTask serviceTask = modelInstance.getModelElementById(TASK_ID);
    assertThat(serviceTask.getCamundaClass()).isEqualTo(getClass().getName());
  }

  @Test
  public void testSendTaskCamundaExtensions() {
    modelInstance =
        Bpmn.createProcess()
            .startEvent()
            .sendTask(TASK_ID)
            .camundaClass(TEST_CLASS_API)
            .camundaDelegateExpression(TEST_DELEGATE_EXPRESSION_API)
            .camundaExpression(TEST_EXPRESSION_API)
            .camundaResultVariable(TEST_STRING_API)
            .camundaTopic(TEST_STRING_API)
            .camundaType(TEST_STRING_API)
            .camundaTaskPriority(TEST_SERVICE_TASK_PRIORITY)
            .camundaFailedJobRetryTimeCycle(FAILED_JOB_RETRY_TIME_CYCLE)
            .endEvent()
            .done();

    final SendTask sendTask = modelInstance.getModelElementById(TASK_ID);
    assertThat(sendTask.getCamundaClass()).isEqualTo(TEST_CLASS_API);
    assertThat(sendTask.getCamundaDelegateExpression()).isEqualTo(TEST_DELEGATE_EXPRESSION_API);
    assertThat(sendTask.getCamundaExpression()).isEqualTo(TEST_EXPRESSION_API);
    assertThat(sendTask.getCamundaResultVariable()).isEqualTo(TEST_STRING_API);
    assertThat(sendTask.getCamundaTopic()).isEqualTo(TEST_STRING_API);
    assertThat(sendTask.getCamundaType()).isEqualTo(TEST_STRING_API);
    assertThat(sendTask.getCamundaTaskPriority()).isEqualTo(TEST_SERVICE_TASK_PRIORITY);

    assertCamundaFailedJobRetryTimeCycle(sendTask);
  }

  @Test
  public void testSendTaskCamundaClass() {
    modelInstance =
        Bpmn.createProcess()
            .startEvent()
            .sendTask(TASK_ID)
            .camundaClass(this.getClass())
            .endEvent()
            .done();

    final SendTask sendTask = modelInstance.getModelElementById(TASK_ID);
    assertThat(sendTask.getCamundaClass()).isEqualTo(this.getClass().getName());
  }

  @Test
  public void testUserTaskCamundaExtensions() {
    modelInstance =
        Bpmn.createProcess()
            .startEvent()
            .userTask(TASK_ID)
            .camundaAssignee(TEST_STRING_API)
            .camundaCandidateGroups(TEST_GROUPS_API)
            .camundaCandidateUsers(TEST_USERS_LIST_API)
            .camundaDueDate(TEST_DUE_DATE_API)
            .camundaFollowUpDate(TEST_FOLLOW_UP_DATE_API)
            .camundaFormHandlerClass(TEST_CLASS_API)
            .camundaFormKey(TEST_STRING_API)
            .camundaPriority(TEST_PRIORITY_API)
            .camundaFailedJobRetryTimeCycle(FAILED_JOB_RETRY_TIME_CYCLE)
            .endEvent()
            .done();

    final UserTask userTask = modelInstance.getModelElementById(TASK_ID);
    assertThat(userTask.getCamundaAssignee()).isEqualTo(TEST_STRING_API);
    assertThat(userTask.getCamundaCandidateGroups()).isEqualTo(TEST_GROUPS_API);
    assertThat(userTask.getCamundaCandidateGroupsList()).containsAll(TEST_GROUPS_LIST_API);
    assertThat(userTask.getCamundaCandidateUsers()).isEqualTo(TEST_USERS_API);
    assertThat(userTask.getCamundaCandidateUsersList()).containsAll(TEST_USERS_LIST_API);
    assertThat(userTask.getCamundaDueDate()).isEqualTo(TEST_DUE_DATE_API);
    assertThat(userTask.getCamundaFollowUpDate()).isEqualTo(TEST_FOLLOW_UP_DATE_API);
    assertThat(userTask.getCamundaFormHandlerClass()).isEqualTo(TEST_CLASS_API);
    assertThat(userTask.getCamundaFormKey()).isEqualTo(TEST_STRING_API);
    assertThat(userTask.getCamundaPriority()).isEqualTo(TEST_PRIORITY_API);

    assertCamundaFailedJobRetryTimeCycle(userTask);
  }

  @Test
  public void testBusinessRuleTaskCamundaExtensions() {
    modelInstance =
        Bpmn.createProcess()
            .startEvent()
            .businessRuleTask(TASK_ID)
            .camundaClass(TEST_CLASS_API)
            .camundaDelegateExpression(TEST_DELEGATE_EXPRESSION_API)
            .camundaExpression(TEST_EXPRESSION_API)
            .camundaResultVariable("resultVar")
            .camundaTopic("topic")
            .camundaType("type")
            .camundaDecisionRef("decisionRef")
            .camundaDecisionRefBinding("latest")
            .camundaDecisionRefVersion("7")
            .camundaDecisionRefVersionTag("0.1.0")
            .camundaDecisionRefTenantId("tenantId")
            .camundaMapDecisionResult("singleEntry")
            .camundaTaskPriority(TEST_SERVICE_TASK_PRIORITY)
            .camundaFailedJobRetryTimeCycle(FAILED_JOB_RETRY_TIME_CYCLE)
            .endEvent()
            .done();

    final BusinessRuleTask businessRuleTask = modelInstance.getModelElementById(TASK_ID);
    assertThat(businessRuleTask.getCamundaClass()).isEqualTo(TEST_CLASS_API);
    assertThat(businessRuleTask.getCamundaDelegateExpression())
        .isEqualTo(TEST_DELEGATE_EXPRESSION_API);
    assertThat(businessRuleTask.getCamundaExpression()).isEqualTo(TEST_EXPRESSION_API);
    assertThat(businessRuleTask.getCamundaResultVariable()).isEqualTo("resultVar");
    assertThat(businessRuleTask.getCamundaTopic()).isEqualTo("topic");
    assertThat(businessRuleTask.getCamundaType()).isEqualTo("type");
    assertThat(businessRuleTask.getCamundaDecisionRef()).isEqualTo("decisionRef");
    assertThat(businessRuleTask.getCamundaDecisionRefBinding()).isEqualTo("latest");
    assertThat(businessRuleTask.getCamundaDecisionRefVersion()).isEqualTo("7");
    assertThat(businessRuleTask.getCamundaDecisionRefVersionTag()).isEqualTo("0.1.0");
    assertThat(businessRuleTask.getCamundaDecisionRefTenantId()).isEqualTo("tenantId");
    assertThat(businessRuleTask.getCamundaMapDecisionResult()).isEqualTo("singleEntry");
    assertThat(businessRuleTask.getCamundaTaskPriority()).isEqualTo(TEST_SERVICE_TASK_PRIORITY);

    assertCamundaFailedJobRetryTimeCycle(businessRuleTask);
  }

  @Test
  public void testBusinessRuleTaskCamundaClass() {
    modelInstance =
        Bpmn.createProcess()
            .startEvent()
            .businessRuleTask(TASK_ID)
            .camundaClass(Bpmn.class)
            .endEvent()
            .done();

    final BusinessRuleTask businessRuleTask = modelInstance.getModelElementById(TASK_ID);
    assertThat(businessRuleTask.getCamundaClass()).isEqualTo("io.zeebe.model.bpmn.Bpmn");
  }

  @Test
  public void testScriptTaskCamundaExtensions() {
    modelInstance =
        Bpmn.createProcess()
            .startEvent()
            .scriptTask(TASK_ID)
            .camundaResultVariable(TEST_STRING_API)
            .camundaResource(TEST_STRING_API)
            .camundaFailedJobRetryTimeCycle(FAILED_JOB_RETRY_TIME_CYCLE)
            .endEvent()
            .done();

    final ScriptTask scriptTask = modelInstance.getModelElementById(TASK_ID);
    assertThat(scriptTask.getCamundaResultVariable()).isEqualTo(TEST_STRING_API);
    assertThat(scriptTask.getCamundaResource()).isEqualTo(TEST_STRING_API);

    assertCamundaFailedJobRetryTimeCycle(scriptTask);
  }

  @Test
  public void testStartEventCamundaExtensions() {
    modelInstance =
        Bpmn.createProcess()
            .startEvent(START_EVENT_ID)
            .camundaAsyncBefore()
            .notCamundaExclusive()
            .camundaFormHandlerClass(TEST_CLASS_API)
            .camundaFormKey(TEST_STRING_API)
            .camundaInitiator(TEST_STRING_API)
            .camundaFailedJobRetryTimeCycle(FAILED_JOB_RETRY_TIME_CYCLE)
            .done();

    final StartEvent startEvent = modelInstance.getModelElementById(START_EVENT_ID);
    assertThat(startEvent.isCamundaAsyncBefore()).isTrue();
    assertThat(startEvent.isCamundaExclusive()).isFalse();
    assertThat(startEvent.getCamundaFormHandlerClass()).isEqualTo(TEST_CLASS_API);
    assertThat(startEvent.getCamundaFormKey()).isEqualTo(TEST_STRING_API);
    assertThat(startEvent.getCamundaInitiator()).isEqualTo(TEST_STRING_API);

    assertCamundaFailedJobRetryTimeCycle(startEvent);
  }

  @Test
  public void testErrorDefinitionsForStartEvent() {
    modelInstance =
        Bpmn.createProcess()
            .startEvent("start")
            .errorEventDefinition("event")
            .errorCodeVariable("errorCodeVariable")
            .errorMessageVariable("errorMessageVariable")
            .error("errorCode")
            .errorEventDefinitionDone()
            .endEvent()
            .done();

    assertErrorEventDefinition("start", "errorCode");
    assertErrorEventDefinitionForErrorVariables(
        "start", "errorCodeVariable", "errorMessageVariable");
  }

  @Test
  public void testErrorDefinitionsForStartEventWithoutEventDefinitionId() {
    modelInstance =
        Bpmn.createProcess()
            .startEvent("start")
            .errorEventDefinition()
            .errorCodeVariable("errorCodeVariable")
            .errorMessageVariable("errorMessageVariable")
            .error("errorCode")
            .errorEventDefinitionDone()
            .endEvent()
            .done();

    assertErrorEventDefinition("start", "errorCode");
    assertErrorEventDefinitionForErrorVariables(
        "start", "errorCodeVariable", "errorMessageVariable");
  }

  @Test
  public void testCallActivityCamundaExtension() {
    modelInstance =
        Bpmn.createProcess()
            .startEvent()
            .callActivity(CALL_ACTIVITY_ID)
            .calledElement(TEST_STRING_API)
            .camundaAsyncBefore()
            .camundaCalledElementBinding("version")
            .camundaCalledElementVersion("1.0")
            .camundaCalledElementVersionTag("ver-1.0")
            .camundaCalledElementTenantId("t1")
            .camundaCaseRef("case")
            .camundaCaseBinding("deployment")
            .camundaCaseVersion("2")
            .camundaCaseTenantId("t2")
            .camundaIn("in-source", "in-target")
            .camundaOut("out-source", "out-target")
            .camundaVariableMappingClass(TEST_CLASS_API)
            .camundaVariableMappingDelegateExpression(TEST_DELEGATE_EXPRESSION_API)
            .notCamundaExclusive()
            .camundaFailedJobRetryTimeCycle(FAILED_JOB_RETRY_TIME_CYCLE)
            .endEvent()
            .done();

    final CallActivity callActivity = modelInstance.getModelElementById(CALL_ACTIVITY_ID);
    assertThat(callActivity.getCalledElement()).isEqualTo(TEST_STRING_API);
    assertThat(callActivity.isCamundaAsyncBefore()).isTrue();
    assertThat(callActivity.getCamundaCalledElementBinding()).isEqualTo("version");
    assertThat(callActivity.getCamundaCalledElementVersion()).isEqualTo("1.0");
    assertThat(callActivity.getCamundaCalledElementVersionTag()).isEqualTo("ver-1.0");
    assertThat(callActivity.getCamundaCalledElementTenantId()).isEqualTo("t1");
    assertThat(callActivity.getCamundaCaseRef()).isEqualTo("case");
    assertThat(callActivity.getCamundaCaseBinding()).isEqualTo("deployment");
    assertThat(callActivity.getCamundaCaseVersion()).isEqualTo("2");
    assertThat(callActivity.getCamundaCaseTenantId()).isEqualTo("t2");
    assertThat(callActivity.isCamundaExclusive()).isFalse();

    final CamundaIn camundaIn =
        (CamundaIn)
            callActivity.getExtensionElements().getUniqueChildElementByType(CamundaIn.class);
    assertThat(camundaIn.getCamundaSource()).isEqualTo("in-source");
    assertThat(camundaIn.getCamundaTarget()).isEqualTo("in-target");

    final CamundaOut camundaOut =
        (CamundaOut)
            callActivity.getExtensionElements().getUniqueChildElementByType(CamundaOut.class);
    assertThat(camundaOut.getCamundaSource()).isEqualTo("out-source");
    assertThat(camundaOut.getCamundaTarget()).isEqualTo("out-target");

    assertThat(callActivity.getCamundaVariableMappingClass()).isEqualTo(TEST_CLASS_API);
    assertThat(callActivity.getCamundaVariableMappingDelegateExpression())
        .isEqualTo(TEST_DELEGATE_EXPRESSION_API);
    assertCamundaFailedJobRetryTimeCycle(callActivity);
  }

  @Test
  public void testCallActivityCamundaVariableMappingClass() {
    modelInstance =
        Bpmn.createProcess()
            .startEvent()
            .callActivity(CALL_ACTIVITY_ID)
            .camundaVariableMappingClass(this.getClass())
            .endEvent()
            .done();

    final CallActivity callActivity = modelInstance.getModelElementById(CALL_ACTIVITY_ID);
    assertThat(callActivity.getCamundaVariableMappingClass()).isEqualTo(this.getClass().getName());
  }

  @Test
  public void testSubProcessBuilder() {
    final BpmnModelInstance modelInstance =
        Bpmn.createProcess()
            .startEvent()
            .subProcess(SUB_PROCESS_ID)
            .camundaAsyncBefore()
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
    assertThat(subProcess.isCamundaAsyncBefore()).isTrue();
    assertThat(subProcess.isCamundaExclusive()).isTrue();
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

    subProcess
        .builder()
        .camundaAsyncBefore()
        .embeddedSubProcess()
        .startEvent()
        .userTask()
        .endEvent();

    final ServiceTask serviceTask = modelInstance.getModelElementById(SERVICE_TASK_ID);
    assertThat(subProcess.isCamundaAsyncBefore()).isTrue();
    assertThat(subProcess.isCamundaExclusive()).isTrue();
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
            .camundaAsyncBefore()
            .embeddedSubProcess()
            .startEvent()
            .userTask()
            .subProcess(SUB_PROCESS_ID + 2)
            .camundaAsyncBefore()
            .notCamundaExclusive()
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
    assertThat(subProcess.isCamundaAsyncBefore()).isTrue();
    assertThat(subProcess.isCamundaExclusive()).isTrue();
    assertThat(subProcess.getChildElementsByType(Event.class)).hasSize(2);
    assertThat(subProcess.getChildElementsByType(Task.class)).hasSize(2);
    assertThat(subProcess.getChildElementsByType(SubProcess.class)).hasSize(1);
    assertThat(subProcess.getFlowElements()).hasSize(9);
    assertThat(subProcess.getSucceedingNodes().singleResult()).isEqualTo(serviceTask);

    final SubProcess nestedSubProcess = modelInstance.getModelElementById(SUB_PROCESS_ID + 2);
    final ServiceTask nestedServiceTask = modelInstance.getModelElementById(SERVICE_TASK_ID + 1);
    assertThat(nestedSubProcess.isCamundaAsyncBefore()).isTrue();
    assertThat(nestedSubProcess.isCamundaExclusive()).isFalse();
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
            .camundaAsyncBefore()
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
    assertThat(transaction.isCamundaAsyncBefore()).isTrue();
    assertThat(transaction.isCamundaExclusive()).isTrue();
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

    transaction
        .builder()
        .camundaAsyncBefore()
        .embeddedSubProcess()
        .startEvent()
        .userTask()
        .endEvent();

    final ServiceTask serviceTask = modelInstance.getModelElementById(SERVICE_TASK_ID);
    assertThat(transaction.isCamundaAsyncBefore()).isTrue();
    assertThat(transaction.isCamundaExclusive()).isTrue();
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
  public void testEventBasedGatewayAsyncAfter() {
    try {
      modelInstance =
          Bpmn.createProcess().startEvent().eventBasedGateway().camundaAsyncAfter().done();

      fail("Expected UnsupportedOperationException");
    } catch (final UnsupportedOperationException ex) {
      // happy path
    }

    try {
      modelInstance =
          Bpmn.createProcess()
              .startEvent()
              .eventBasedGateway()
              .camundaAsyncAfter(true)
              .endEvent()
              .done();
      fail("Expected UnsupportedOperationException");
    } catch (final UnsupportedOperationException ex) {
      // happy ending :D
    }
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
            .camundaTaskPriority(TEST_SERVICE_TASK_PRIORITY)
            .camundaType("external")
            .camundaTopic("TOPIC")
            .done();

    final MessageEventDefinition event =
        modelInstance.getModelElementById("messageEventDefinition");
    assertThat(event.getCamundaTaskPriority()).isEqualTo(TEST_SERVICE_TASK_PRIORITY);
    assertThat(event.getCamundaTopic()).isEqualTo("TOPIC");
    assertThat(event.getCamundaType()).isEqualTo("external");
    assertThat(event.getMessage().getName()).isEqualTo("message");
  }

  @Test
  public void testIntermediateMessageThrowEventWithTaskPriority() {
    modelInstance =
        Bpmn.createProcess()
            .startEvent()
            .intermediateThrowEvent("throw1")
            .messageEventDefinition("messageEventDefinition")
            .camundaTaskPriority(TEST_SERVICE_TASK_PRIORITY)
            .done();

    final MessageEventDefinition event =
        modelInstance.getModelElementById("messageEventDefinition");
    assertThat(event.getCamundaTaskPriority()).isEqualTo(TEST_SERVICE_TASK_PRIORITY);
  }

  @Test
  public void testEndEventWithTaskPriority() {
    modelInstance =
        Bpmn.createProcess()
            .startEvent()
            .endEvent("end")
            .messageEventDefinition("messageEventDefinition")
            .camundaTaskPriority(TEST_SERVICE_TASK_PRIORITY)
            .done();

    final MessageEventDefinition event =
        modelInstance.getModelElementById("messageEventDefinition");
    assertThat(event.getCamundaTaskPriority()).isEqualTo(TEST_SERVICE_TASK_PRIORITY);
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
  public void testIntermediateSignalThrowEventWithPayloadLocalVar() {
    modelInstance =
        Bpmn.createProcess()
            .startEvent()
            .intermediateThrowEvent("throw")
            .signalEventDefinition("signal")
            .camundaInSourceTarget("source", "target1")
            .camundaInSourceExpressionTarget("${'sourceExpression'}", "target2")
            .camundaInAllVariables("all", true)
            .camundaInBusinessKey("aBusinessKey")
            .throwEventDefinitionDone()
            .endEvent()
            .done();

    assertSignalEventDefinition("throw", "signal");
    final SignalEventDefinition signalEventDefinition =
        assertAndGetSingleEventDefinition("throw", SignalEventDefinition.class);

    assertThat(signalEventDefinition.getSignal().getName()).isEqualTo("signal");

    final List<CamundaIn> camundaInParams =
        signalEventDefinition
            .getExtensionElements()
            .getElementsQuery()
            .filterByType(CamundaIn.class)
            .list();
    assertThat(camundaInParams.size()).isEqualTo(4);

    int paramCounter = 0;
    for (final CamundaIn inParam : camundaInParams) {
      if (inParam.getCamundaVariables() != null) {
        assertThat(inParam.getCamundaVariables()).isEqualTo("all");
        if (inParam.getCamundaLocal()) {
          paramCounter++;
        }
      } else if (inParam.getCamundaBusinessKey() != null) {
        assertThat(inParam.getCamundaBusinessKey()).isEqualTo("aBusinessKey");
        paramCounter++;
      } else if (inParam.getCamundaSourceExpression() != null) {
        assertThat(inParam.getCamundaSourceExpression()).isEqualTo("${'sourceExpression'}");
        assertThat(inParam.getCamundaTarget()).isEqualTo("target2");
        paramCounter++;
      } else if (inParam.getCamundaSource() != null) {
        assertThat(inParam.getCamundaSource()).isEqualTo("source");
        assertThat(inParam.getCamundaTarget()).isEqualTo("target1");
        paramCounter++;
      }
    }
    assertThat(paramCounter).isEqualTo(camundaInParams.size());
  }

  @Test
  public void testIntermediateSignalThrowEventWithPayload() {
    modelInstance =
        Bpmn.createProcess()
            .startEvent()
            .intermediateThrowEvent("throw")
            .signalEventDefinition("signal")
            .camundaInAllVariables("all")
            .throwEventDefinitionDone()
            .endEvent()
            .done();

    final SignalEventDefinition signalEventDefinition =
        assertAndGetSingleEventDefinition("throw", SignalEventDefinition.class);

    final List<CamundaIn> camundaInParams =
        signalEventDefinition
            .getExtensionElements()
            .getElementsQuery()
            .filterByType(CamundaIn.class)
            .list();
    assertThat(camundaInParams.size()).isEqualTo(1);

    assertThat(camundaInParams.get(0).getCamundaVariables()).isEqualTo("all");
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
  public void testCamundaTaskListenerByClassName() {
    modelInstance =
        Bpmn.createProcess()
            .startEvent()
            .userTask("task")
            .camundaTaskListenerClass("start", "aClass")
            .endEvent()
            .done();

    final UserTask userTask = modelInstance.getModelElementById("task");
    final ExtensionElements extensionElements = userTask.getExtensionElements();
    final Collection<CamundaTaskListener> taskListeners =
        extensionElements.getChildElementsByType(CamundaTaskListener.class);
    assertThat(taskListeners).hasSize(1);

    final CamundaTaskListener taskListener = taskListeners.iterator().next();
    assertThat(taskListener.getCamundaClass()).isEqualTo("aClass");
    assertThat(taskListener.getCamundaEvent()).isEqualTo("start");
  }

  @Test
  public void testCamundaTaskListenerByClass() {
    modelInstance =
        Bpmn.createProcess()
            .startEvent()
            .userTask("task")
            .camundaTaskListenerClass("start", this.getClass())
            .endEvent()
            .done();

    final UserTask userTask = modelInstance.getModelElementById("task");
    final ExtensionElements extensionElements = userTask.getExtensionElements();
    final Collection<CamundaTaskListener> taskListeners =
        extensionElements.getChildElementsByType(CamundaTaskListener.class);
    assertThat(taskListeners).hasSize(1);

    final CamundaTaskListener taskListener = taskListeners.iterator().next();
    assertThat(taskListener.getCamundaClass()).isEqualTo(this.getClass().getName());
    assertThat(taskListener.getCamundaEvent()).isEqualTo("start");
  }

  @Test
  public void testCamundaTaskListenerByExpression() {
    modelInstance =
        Bpmn.createProcess()
            .startEvent()
            .userTask("task")
            .camundaTaskListenerExpression("start", "anExpression")
            .endEvent()
            .done();

    final UserTask userTask = modelInstance.getModelElementById("task");
    final ExtensionElements extensionElements = userTask.getExtensionElements();
    final Collection<CamundaTaskListener> taskListeners =
        extensionElements.getChildElementsByType(CamundaTaskListener.class);
    assertThat(taskListeners).hasSize(1);

    final CamundaTaskListener taskListener = taskListeners.iterator().next();
    assertThat(taskListener.getCamundaExpression()).isEqualTo("anExpression");
    assertThat(taskListener.getCamundaEvent()).isEqualTo("start");
  }

  @Test
  public void testCamundaTaskListenerByDelegateExpression() {
    modelInstance =
        Bpmn.createProcess()
            .startEvent()
            .userTask("task")
            .camundaTaskListenerDelegateExpression("start", "aDelegate")
            .endEvent()
            .done();

    final UserTask userTask = modelInstance.getModelElementById("task");
    final ExtensionElements extensionElements = userTask.getExtensionElements();
    final Collection<CamundaTaskListener> taskListeners =
        extensionElements.getChildElementsByType(CamundaTaskListener.class);
    assertThat(taskListeners).hasSize(1);

    final CamundaTaskListener taskListener = taskListeners.iterator().next();
    assertThat(taskListener.getCamundaDelegateExpression()).isEqualTo("aDelegate");
    assertThat(taskListener.getCamundaEvent()).isEqualTo("start");
  }

  @Test
  public void testCamundaExecutionListenerByClassName() {
    modelInstance =
        Bpmn.createProcess()
            .startEvent()
            .userTask("task")
            .camundaExecutionListenerClass("start", "aClass")
            .endEvent()
            .done();

    final UserTask userTask = modelInstance.getModelElementById("task");
    final ExtensionElements extensionElements = userTask.getExtensionElements();
    final Collection<CamundaExecutionListener> executionListeners =
        extensionElements.getChildElementsByType(CamundaExecutionListener.class);
    assertThat(executionListeners).hasSize(1);

    final CamundaExecutionListener executionListener = executionListeners.iterator().next();
    assertThat(executionListener.getCamundaClass()).isEqualTo("aClass");
    assertThat(executionListener.getCamundaEvent()).isEqualTo("start");
  }

  @Test
  public void testCamundaExecutionListenerByClass() {
    modelInstance =
        Bpmn.createProcess()
            .startEvent()
            .userTask("task")
            .camundaExecutionListenerClass("start", this.getClass())
            .endEvent()
            .done();

    final UserTask userTask = modelInstance.getModelElementById("task");
    final ExtensionElements extensionElements = userTask.getExtensionElements();
    final Collection<CamundaExecutionListener> executionListeners =
        extensionElements.getChildElementsByType(CamundaExecutionListener.class);
    assertThat(executionListeners).hasSize(1);

    final CamundaExecutionListener executionListener = executionListeners.iterator().next();
    assertThat(executionListener.getCamundaClass()).isEqualTo(this.getClass().getName());
    assertThat(executionListener.getCamundaEvent()).isEqualTo("start");
  }

  @Test
  public void testCamundaExecutionListenerByExpression() {
    modelInstance =
        Bpmn.createProcess()
            .startEvent()
            .userTask("task")
            .camundaExecutionListenerExpression("start", "anExpression")
            .endEvent()
            .done();

    final UserTask userTask = modelInstance.getModelElementById("task");
    final ExtensionElements extensionElements = userTask.getExtensionElements();
    final Collection<CamundaExecutionListener> executionListeners =
        extensionElements.getChildElementsByType(CamundaExecutionListener.class);
    assertThat(executionListeners).hasSize(1);

    final CamundaExecutionListener executionListener = executionListeners.iterator().next();
    assertThat(executionListener.getCamundaExpression()).isEqualTo("anExpression");
    assertThat(executionListener.getCamundaEvent()).isEqualTo("start");
  }

  @Test
  public void testCamundaExecutionListenerByDelegateExpression() {
    modelInstance =
        Bpmn.createProcess()
            .startEvent()
            .userTask("task")
            .camundaExecutionListenerDelegateExpression("start", "aDelegateExpression")
            .endEvent()
            .done();

    final UserTask userTask = modelInstance.getModelElementById("task");
    final ExtensionElements extensionElements = userTask.getExtensionElements();
    final Collection<CamundaExecutionListener> executionListeners =
        extensionElements.getChildElementsByType(CamundaExecutionListener.class);
    assertThat(executionListeners).hasSize(1);

    final CamundaExecutionListener executionListener = executionListeners.iterator().next();
    assertThat(executionListener.getCamundaDelegateExpression()).isEqualTo("aDelegateExpression");
    assertThat(executionListener.getCamundaEvent()).isEqualTo("start");
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
            .camundaCollection("coll")
            .camundaElementVariable("element")
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
    assertThat(miCharacteristic.getCamundaCollection()).isEqualTo("coll");
    assertThat(miCharacteristic.getCamundaElementVariable()).isEqualTo("element");
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
  public void testTaskWithCamundaInputOutput() {
    modelInstance =
        Bpmn.createProcess()
            .startEvent()
            .userTask("task")
            .camundaInputParameter("foo", "bar")
            .camundaInputParameter("yoo", "hoo")
            .camundaOutputParameter("one", "two")
            .camundaOutputParameter("three", "four")
            .endEvent()
            .done();

    final UserTask task = modelInstance.getModelElementById("task");
    assertCamundaInputOutputParameter(task);
  }

  @Test
  public void testTaskWithCamundaInputOutputWithExistingExtensionElements() {
    modelInstance =
        Bpmn.createProcess()
            .startEvent()
            .userTask("task")
            .camundaExecutionListenerExpression("end", "${true}")
            .camundaInputParameter("foo", "bar")
            .camundaInputParameter("yoo", "hoo")
            .camundaOutputParameter("one", "two")
            .camundaOutputParameter("three", "four")
            .endEvent()
            .done();

    final UserTask task = modelInstance.getModelElementById("task");
    assertCamundaInputOutputParameter(task);
  }

  @Test
  public void testTaskWithCamundaInputOutputWithExistingCamundaInputOutput() {
    modelInstance =
        Bpmn.createProcess()
            .startEvent()
            .userTask("task")
            .camundaInputParameter("foo", "bar")
            .camundaOutputParameter("one", "two")
            .endEvent()
            .done();

    final UserTask task = modelInstance.getModelElementById("task");

    task.builder().camundaInputParameter("yoo", "hoo").camundaOutputParameter("three", "four");

    assertCamundaInputOutputParameter(task);
  }

  @Test
  public void testSubProcessWithCamundaInputOutput() {
    modelInstance =
        Bpmn.createProcess()
            .startEvent()
            .subProcess("subProcess")
            .camundaInputParameter("foo", "bar")
            .camundaInputParameter("yoo", "hoo")
            .camundaOutputParameter("one", "two")
            .camundaOutputParameter("three", "four")
            .embeddedSubProcess()
            .startEvent()
            .endEvent()
            .subProcessDone()
            .endEvent()
            .done();

    final SubProcess subProcess = modelInstance.getModelElementById("subProcess");
    assertCamundaInputOutputParameter(subProcess);
  }

  @Test
  public void testSubProcessWithCamundaInputOutputWithExistingExtensionElements() {
    modelInstance =
        Bpmn.createProcess()
            .startEvent()
            .subProcess("subProcess")
            .camundaExecutionListenerExpression("end", "${true}")
            .camundaInputParameter("foo", "bar")
            .camundaInputParameter("yoo", "hoo")
            .camundaOutputParameter("one", "two")
            .camundaOutputParameter("three", "four")
            .embeddedSubProcess()
            .startEvent()
            .endEvent()
            .subProcessDone()
            .endEvent()
            .done();

    final SubProcess subProcess = modelInstance.getModelElementById("subProcess");
    assertCamundaInputOutputParameter(subProcess);
  }

  @Test
  public void testSubProcessWithCamundaInputOutputWithExistingCamundaInputOutput() {
    modelInstance =
        Bpmn.createProcess()
            .startEvent()
            .subProcess("subProcess")
            .camundaInputParameter("foo", "bar")
            .camundaOutputParameter("one", "two")
            .embeddedSubProcess()
            .startEvent()
            .endEvent()
            .subProcessDone()
            .endEvent()
            .done();

    final SubProcess subProcess = modelInstance.getModelElementById("subProcess");

    subProcess
        .builder()
        .camundaInputParameter("yoo", "hoo")
        .camundaOutputParameter("three", "four");

    assertCamundaInputOutputParameter(subProcess);
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
            .errorCodeVariable("errorCodeVariable")
            .errorMessageVariable("errorMessageVariable")
            .error("errorCode")
            .errorEventDefinitionDone()
            .endEvent("boundaryEnd")
            .done();

    assertErrorEventDefinition("boundary", "errorCode");
    assertErrorEventDefinitionForErrorVariables(
        "boundary", "errorCodeVariable", "errorMessageVariable");
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
            .errorCodeVariable("errorCodeVariable")
            .errorMessageVariable("errorMessageVariable")
            .error("errorCode")
            .errorEventDefinitionDone()
            .endEvent("boundaryEnd")
            .done();

    assertErrorEventDefinition("boundary", "errorCode");
    assertErrorEventDefinitionForErrorVariables(
        "boundary", "errorCodeVariable", "errorMessageVariable");
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
  public void testUserTaskCamundaFormField() {
    modelInstance =
        Bpmn.createProcess()
            .startEvent()
            .userTask(TASK_ID)
            .camundaFormField()
            .camundaId("myFormField_1")
            .camundaLabel("Form Field One")
            .camundaType("string")
            .camundaDefaultValue("myDefaultVal_1")
            .camundaFormFieldDone()
            .camundaFormField()
            .camundaId("myFormField_2")
            .camundaLabel("Form Field Two")
            .camundaType("integer")
            .camundaDefaultValue("myDefaultVal_2")
            .camundaFormFieldDone()
            .endEvent()
            .done();

    final UserTask userTask = modelInstance.getModelElementById(TASK_ID);
    assertCamundaFormField(userTask);
  }

  @Test
  public void testUserTaskCamundaFormFieldWithExistingCamundaFormData() {
    modelInstance =
        Bpmn.createProcess()
            .startEvent()
            .userTask(TASK_ID)
            .camundaFormField()
            .camundaId("myFormField_1")
            .camundaLabel("Form Field One")
            .camundaType("string")
            .camundaDefaultValue("myDefaultVal_1")
            .camundaFormFieldDone()
            .endEvent()
            .done();

    final UserTask userTask = modelInstance.getModelElementById(TASK_ID);

    userTask
        .builder()
        .camundaFormField()
        .camundaId("myFormField_2")
        .camundaLabel("Form Field Two")
        .camundaType("integer")
        .camundaDefaultValue("myDefaultVal_2")
        .camundaFormFieldDone();

    assertCamundaFormField(userTask);
  }

  @Test
  public void testStartEventCamundaFormField() {
    modelInstance =
        Bpmn.createProcess()
            .startEvent(START_EVENT_ID)
            .camundaFormField()
            .camundaId("myFormField_1")
            .camundaLabel("Form Field One")
            .camundaType("string")
            .camundaDefaultValue("myDefaultVal_1")
            .camundaFormFieldDone()
            .camundaFormField()
            .camundaId("myFormField_2")
            .camundaLabel("Form Field Two")
            .camundaType("integer")
            .camundaDefaultValue("myDefaultVal_2")
            .camundaFormFieldDone()
            .endEvent()
            .done();

    final StartEvent startEvent = modelInstance.getModelElementById(START_EVENT_ID);
    assertCamundaFormField(startEvent);
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
            .endEvent("end")
            .done();

    final UserTask userTask = modelInstance.getModelElementById("userTask");
    final UserTaskBuilder userTaskBuilder = userTask.builder();

    try {
      userTaskBuilder
          .boundaryEvent("boundary")
          .compensateEventDefinition()
          .activityRef("subProcessTask")
          .done();
      fail("should fail");
    } catch (final BpmnModelException e) {
      assertThat(e)
          .hasMessageContaining(
              "Activity with id 'subProcessTask' must be in the same scope as 'boundary'");
    }
  }

  @Test
  public void testConditionalEventDefinitionCamundaExtensions() {
    modelInstance =
        Bpmn.createProcess()
            .startEvent()
            .intermediateCatchEvent()
            .conditionalEventDefinition(CONDITION_ID)
            .condition(TEST_CONDITION)
            .camundaVariableEvents(TEST_CONDITIONAL_VARIABLE_EVENTS)
            .camundaVariableEvents(TEST_CONDITIONAL_VARIABLE_EVENTS_LIST)
            .camundaVariableName(TEST_CONDITIONAL_VARIABLE_NAME)
            .conditionalEventDefinitionDone()
            .endEvent()
            .done();

    final ConditionalEventDefinition conditionalEventDef =
        modelInstance.getModelElementById(CONDITION_ID);
    assertThat(conditionalEventDef.getCamundaVariableEvents())
        .isEqualTo(TEST_CONDITIONAL_VARIABLE_EVENTS);
    assertThat(conditionalEventDef.getCamundaVariableEventsList())
        .containsAll(TEST_CONDITIONAL_VARIABLE_EVENTS_LIST);
    assertThat(conditionalEventDef.getCamundaVariableName())
        .isEqualTo(TEST_CONDITIONAL_VARIABLE_NAME);
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

  protected Message assertMessageEventDefinition(String elementId, String messageName) {
    final MessageEventDefinition messageEventDefinition =
        assertAndGetSingleEventDefinition(elementId, MessageEventDefinition.class);
    final Message message = messageEventDefinition.getMessage();
    assertThat(message).isNotNull();
    assertThat(message.getName()).isEqualTo(messageName);

    return message;
  }

  protected void assertOnlyOneMessageExists(String messageName) {
    final Collection<Message> messages = modelInstance.getModelElementsByType(Message.class);
    assertThat(messages).extracting("name").containsOnlyOnce(messageName);
  }

  protected Signal assertSignalEventDefinition(String elementId, String signalName) {
    final SignalEventDefinition signalEventDefinition =
        assertAndGetSingleEventDefinition(elementId, SignalEventDefinition.class);
    final Signal signal = signalEventDefinition.getSignal();
    assertThat(signal).isNotNull();
    assertThat(signal.getName()).isEqualTo(signalName);

    return signal;
  }

  protected void assertOnlyOneSignalExists(String signalName) {
    final Collection<Signal> signals = modelInstance.getModelElementsByType(Signal.class);
    assertThat(signals).extracting("name").containsOnlyOnce(signalName);
  }

  protected Error assertErrorEventDefinition(String elementId, String errorCode) {
    final ErrorEventDefinition errorEventDefinition =
        assertAndGetSingleEventDefinition(elementId, ErrorEventDefinition.class);
    final Error error = errorEventDefinition.getError();
    assertThat(error).isNotNull();
    assertThat(error.getErrorCode()).isEqualTo(errorCode);

    return error;
  }

  protected void assertErrorEventDefinitionForErrorVariables(
      String elementId, String errorCodeVariable, String errorMessageVariable) {
    final ErrorEventDefinition errorEventDefinition =
        assertAndGetSingleEventDefinition(elementId, ErrorEventDefinition.class);
    assertThat(errorEventDefinition).isNotNull();
    if (errorCodeVariable != null) {
      assertThat(errorEventDefinition.getCamundaErrorCodeVariable()).isEqualTo(errorCodeVariable);
    }
    if (errorMessageVariable != null) {
      assertThat(errorEventDefinition.getCamundaErrorMessageVariable())
          .isEqualTo(errorMessageVariable);
    }
  }

  protected void assertOnlyOneErrorExists(String errorCode) {
    final Collection<Error> errors = modelInstance.getModelElementsByType(Error.class);
    assertThat(errors).extracting("errorCode").containsOnlyOnce(errorCode);
  }

  protected Escalation assertEscalationEventDefinition(String elementId, String escalationCode) {
    final EscalationEventDefinition escalationEventDefinition =
        assertAndGetSingleEventDefinition(elementId, EscalationEventDefinition.class);
    final Escalation escalation = escalationEventDefinition.getEscalation();
    assertThat(escalation).isNotNull();
    assertThat(escalation.getEscalationCode()).isEqualTo(escalationCode);

    return escalation;
  }

  protected void assertOnlyOneEscalationExists(String escalationCode) {
    final Collection<Escalation> escalations =
        modelInstance.getModelElementsByType(Escalation.class);
    assertThat(escalations).extracting("escalationCode").containsOnlyOnce(escalationCode);
  }

  protected void assertCompensationEventDefinition(String elementId) {
    assertAndGetSingleEventDefinition(elementId, CompensateEventDefinition.class);
  }

  protected void assertCamundaInputOutputParameter(BaseElement element) {
    final CamundaInputOutput camundaInputOutput =
        element
            .getExtensionElements()
            .getElementsQuery()
            .filterByType(CamundaInputOutput.class)
            .singleResult();
    assertThat(camundaInputOutput).isNotNull();

    final List<CamundaInputParameter> camundaInputParameters =
        new ArrayList<>(camundaInputOutput.getCamundaInputParameters());
    assertThat(camundaInputParameters).hasSize(2);

    CamundaInputParameter camundaInputParameter = camundaInputParameters.get(0);
    assertThat(camundaInputParameter.getCamundaName()).isEqualTo("foo");
    assertThat(camundaInputParameter.getTextContent()).isEqualTo("bar");

    camundaInputParameter = camundaInputParameters.get(1);
    assertThat(camundaInputParameter.getCamundaName()).isEqualTo("yoo");
    assertThat(camundaInputParameter.getTextContent()).isEqualTo("hoo");

    final List<CamundaOutputParameter> camundaOutputParameters =
        new ArrayList<>(camundaInputOutput.getCamundaOutputParameters());
    assertThat(camundaOutputParameters).hasSize(2);

    CamundaOutputParameter camundaOutputParameter = camundaOutputParameters.get(0);
    assertThat(camundaOutputParameter.getCamundaName()).isEqualTo("one");
    assertThat(camundaOutputParameter.getTextContent()).isEqualTo("two");

    camundaOutputParameter = camundaOutputParameters.get(1);
    assertThat(camundaOutputParameter.getCamundaName()).isEqualTo("three");
    assertThat(camundaOutputParameter.getTextContent()).isEqualTo("four");
  }

  protected void assertTimerWithDate(String elementId, String timerDate) {
    final TimerEventDefinition timerEventDefinition =
        assertAndGetSingleEventDefinition(elementId, TimerEventDefinition.class);
    final TimeDate timeDate = timerEventDefinition.getTimeDate();
    assertThat(timeDate).isNotNull();
    assertThat(timeDate.getTextContent()).isEqualTo(timerDate);
  }

  protected void assertTimerWithDuration(String elementId, String timerDuration) {
    final TimerEventDefinition timerEventDefinition =
        assertAndGetSingleEventDefinition(elementId, TimerEventDefinition.class);
    final TimeDuration timeDuration = timerEventDefinition.getTimeDuration();
    assertThat(timeDuration).isNotNull();
    assertThat(timeDuration.getTextContent()).isEqualTo(timerDuration);
  }

  protected void assertTimerWithCycle(String elementId, String timerCycle) {
    final TimerEventDefinition timerEventDefinition =
        assertAndGetSingleEventDefinition(elementId, TimerEventDefinition.class);
    final TimeCycle timeCycle = timerEventDefinition.getTimeCycle();
    assertThat(timeCycle).isNotNull();
    assertThat(timeCycle.getTextContent()).isEqualTo(timerCycle);
  }

  @SuppressWarnings("unchecked")
  protected <T extends EventDefinition> T assertAndGetSingleEventDefinition(
      String elementId, Class<T> eventDefinitionType) {
    final BpmnModelElementInstance element = modelInstance.getModelElementById(elementId);
    assertThat(element).isNotNull();
    final Collection<EventDefinition> eventDefinitions =
        element.getChildElementsByType(EventDefinition.class);
    assertThat(eventDefinitions).hasSize(1);

    final EventDefinition eventDefinition = eventDefinitions.iterator().next();
    assertThat(eventDefinition).isNotNull().isInstanceOf(eventDefinitionType);
    return (T) eventDefinition;
  }

  protected void assertCamundaFormField(BaseElement element) {
    assertThat(element.getExtensionElements()).isNotNull();

    final CamundaFormData camundaFormData =
        element
            .getExtensionElements()
            .getElementsQuery()
            .filterByType(CamundaFormData.class)
            .singleResult();
    assertThat(camundaFormData).isNotNull();

    final List<CamundaFormField> camundaFormFields =
        new ArrayList<>(camundaFormData.getCamundaFormFields());
    assertThat(camundaFormFields).hasSize(2);

    CamundaFormField camundaFormField = camundaFormFields.get(0);
    assertThat(camundaFormField.getCamundaId()).isEqualTo("myFormField_1");
    assertThat(camundaFormField.getCamundaLabel()).isEqualTo("Form Field One");
    assertThat(camundaFormField.getCamundaType()).isEqualTo("string");
    assertThat(camundaFormField.getCamundaDefaultValue()).isEqualTo("myDefaultVal_1");

    camundaFormField = camundaFormFields.get(1);
    assertThat(camundaFormField.getCamundaId()).isEqualTo("myFormField_2");
    assertThat(camundaFormField.getCamundaLabel()).isEqualTo("Form Field Two");
    assertThat(camundaFormField.getCamundaType()).isEqualTo("integer");
    assertThat(camundaFormField.getCamundaDefaultValue()).isEqualTo("myDefaultVal_2");
  }

  protected void assertCamundaFailedJobRetryTimeCycle(BaseElement element) {
    assertThat(element.getExtensionElements()).isNotNull();

    final CamundaFailedJobRetryTimeCycle camundaFailedJobRetryTimeCycle =
        element
            .getExtensionElements()
            .getElementsQuery()
            .filterByType(CamundaFailedJobRetryTimeCycle.class)
            .singleResult();
    assertThat(camundaFailedJobRetryTimeCycle).isNotNull();
    assertThat(camundaFailedJobRetryTimeCycle.getTextContent())
        .isEqualTo(FAILED_JOB_RETRY_TIME_CYCLE);
  }

  @Test
  public void testCreateEventSubProcess() {
    final ProcessBuilder process = Bpmn.createProcess();
    modelInstance = process.startEvent().sendTask().endEvent().done();

    final EventSubProcessBuilder eventSubProcess = process.eventSubProcess();
    eventSubProcess.startEvent().userTask().endEvent();

    final SubProcess subProcess = eventSubProcess.getElement();

    // no input or output from the sub process
    assertThat(subProcess.getIncoming().isEmpty());
    assertThat(subProcess.getOutgoing().isEmpty());

    // subProcess was triggered by event
    assertThat(eventSubProcess.getElement().triggeredByEvent());

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
    assertThat(eventSubProcess.getIncoming().isEmpty());
    assertThat(eventSubProcess.getOutgoing().isEmpty());

    // subProcess was triggered by event
    assertThat(eventSubProcess.triggeredByEvent());

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
    assertEquals("start", startName);
    final String userName = ((FlowElement) instance.getModelElementById("user")).getName();
    assertEquals("user", userName);
    final String endName = ((FlowElement) instance.getModelElementById("end")).getName();
    assertEquals("name", endName);
  }
}
