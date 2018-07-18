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

package io.zeebe.model.bpmn;

import static io.zeebe.model.bpmn.BpmnTestConstants.BUSINESS_RULE_TASK;
import static io.zeebe.model.bpmn.BpmnTestConstants.CALL_ACTIVITY_ID;
import static io.zeebe.model.bpmn.BpmnTestConstants.END_EVENT_ID;
import static io.zeebe.model.bpmn.BpmnTestConstants.PROCESS_ID;
import static io.zeebe.model.bpmn.BpmnTestConstants.SCRIPT_TASK_ID;
import static io.zeebe.model.bpmn.BpmnTestConstants.SEND_TASK_ID;
import static io.zeebe.model.bpmn.BpmnTestConstants.SEQUENCE_FLOW_ID;
import static io.zeebe.model.bpmn.BpmnTestConstants.SERVICE_TASK_ID;
import static io.zeebe.model.bpmn.BpmnTestConstants.START_EVENT_ID;
import static io.zeebe.model.bpmn.BpmnTestConstants.TEST_CLASS_API;
import static io.zeebe.model.bpmn.BpmnTestConstants.TEST_CLASS_XML;
import static io.zeebe.model.bpmn.BpmnTestConstants.TEST_DELEGATE_EXPRESSION_API;
import static io.zeebe.model.bpmn.BpmnTestConstants.TEST_DELEGATE_EXPRESSION_XML;
import static io.zeebe.model.bpmn.BpmnTestConstants.TEST_DUE_DATE_API;
import static io.zeebe.model.bpmn.BpmnTestConstants.TEST_DUE_DATE_XML;
import static io.zeebe.model.bpmn.BpmnTestConstants.TEST_EXECUTION_EVENT_API;
import static io.zeebe.model.bpmn.BpmnTestConstants.TEST_EXECUTION_EVENT_XML;
import static io.zeebe.model.bpmn.BpmnTestConstants.TEST_EXPRESSION_API;
import static io.zeebe.model.bpmn.BpmnTestConstants.TEST_EXPRESSION_XML;
import static io.zeebe.model.bpmn.BpmnTestConstants.TEST_FLOW_NODE_JOB_PRIORITY;
import static io.zeebe.model.bpmn.BpmnTestConstants.TEST_GROUPS_API;
import static io.zeebe.model.bpmn.BpmnTestConstants.TEST_GROUPS_LIST_API;
import static io.zeebe.model.bpmn.BpmnTestConstants.TEST_GROUPS_LIST_XML;
import static io.zeebe.model.bpmn.BpmnTestConstants.TEST_GROUPS_XML;
import static io.zeebe.model.bpmn.BpmnTestConstants.TEST_HISTORY_TIME_TO_LIVE;
import static io.zeebe.model.bpmn.BpmnTestConstants.TEST_PRIORITY_API;
import static io.zeebe.model.bpmn.BpmnTestConstants.TEST_PRIORITY_XML;
import static io.zeebe.model.bpmn.BpmnTestConstants.TEST_PROCESS_JOB_PRIORITY;
import static io.zeebe.model.bpmn.BpmnTestConstants.TEST_PROCESS_TASK_PRIORITY;
import static io.zeebe.model.bpmn.BpmnTestConstants.TEST_SERVICE_TASK_PRIORITY;
import static io.zeebe.model.bpmn.BpmnTestConstants.TEST_STRING_API;
import static io.zeebe.model.bpmn.BpmnTestConstants.TEST_STRING_XML;
import static io.zeebe.model.bpmn.BpmnTestConstants.TEST_TASK_EVENT_API;
import static io.zeebe.model.bpmn.BpmnTestConstants.TEST_TASK_EVENT_XML;
import static io.zeebe.model.bpmn.BpmnTestConstants.TEST_TYPE_API;
import static io.zeebe.model.bpmn.BpmnTestConstants.TEST_TYPE_XML;
import static io.zeebe.model.bpmn.BpmnTestConstants.TEST_USERS_API;
import static io.zeebe.model.bpmn.BpmnTestConstants.TEST_USERS_LIST_API;
import static io.zeebe.model.bpmn.BpmnTestConstants.TEST_USERS_LIST_XML;
import static io.zeebe.model.bpmn.BpmnTestConstants.TEST_USERS_XML;
import static io.zeebe.model.bpmn.BpmnTestConstants.USER_TASK_ID;
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.ACTIVITI_NS;
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.CAMUNDA_ATTRIBUTE_ERROR_CODE_VARIABLE;
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.CAMUNDA_ATTRIBUTE_ERROR_MESSAGE_VARIABLE;
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.CAMUNDA_NS;
import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.model.bpmn.instance.BaseElement;
import io.zeebe.model.bpmn.instance.BpmnModelElementInstance;
import io.zeebe.model.bpmn.instance.BusinessRuleTask;
import io.zeebe.model.bpmn.instance.CallActivity;
import io.zeebe.model.bpmn.instance.EndEvent;
import io.zeebe.model.bpmn.instance.ErrorEventDefinition;
import io.zeebe.model.bpmn.instance.Expression;
import io.zeebe.model.bpmn.instance.MessageEventDefinition;
import io.zeebe.model.bpmn.instance.ParallelGateway;
import io.zeebe.model.bpmn.instance.Process;
import io.zeebe.model.bpmn.instance.ScriptTask;
import io.zeebe.model.bpmn.instance.SendTask;
import io.zeebe.model.bpmn.instance.SequenceFlow;
import io.zeebe.model.bpmn.instance.ServiceTask;
import io.zeebe.model.bpmn.instance.StartEvent;
import io.zeebe.model.bpmn.instance.UserTask;
import io.zeebe.model.bpmn.instance.camunda.CamundaConnector;
import io.zeebe.model.bpmn.instance.camunda.CamundaConnectorId;
import io.zeebe.model.bpmn.instance.camunda.CamundaConstraint;
import io.zeebe.model.bpmn.instance.camunda.CamundaEntry;
import io.zeebe.model.bpmn.instance.camunda.CamundaExecutionListener;
import io.zeebe.model.bpmn.instance.camunda.CamundaFailedJobRetryTimeCycle;
import io.zeebe.model.bpmn.instance.camunda.CamundaField;
import io.zeebe.model.bpmn.instance.camunda.CamundaFormData;
import io.zeebe.model.bpmn.instance.camunda.CamundaFormField;
import io.zeebe.model.bpmn.instance.camunda.CamundaFormProperty;
import io.zeebe.model.bpmn.instance.camunda.CamundaIn;
import io.zeebe.model.bpmn.instance.camunda.CamundaInputOutput;
import io.zeebe.model.bpmn.instance.camunda.CamundaInputParameter;
import io.zeebe.model.bpmn.instance.camunda.CamundaList;
import io.zeebe.model.bpmn.instance.camunda.CamundaMap;
import io.zeebe.model.bpmn.instance.camunda.CamundaOut;
import io.zeebe.model.bpmn.instance.camunda.CamundaOutputParameter;
import io.zeebe.model.bpmn.instance.camunda.CamundaPotentialStarter;
import io.zeebe.model.bpmn.instance.camunda.CamundaProperties;
import io.zeebe.model.bpmn.instance.camunda.CamundaProperty;
import io.zeebe.model.bpmn.instance.camunda.CamundaScript;
import io.zeebe.model.bpmn.instance.camunda.CamundaTaskListener;
import io.zeebe.model.bpmn.instance.camunda.CamundaValue;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

/**
 * @author Sebastian Menski
 * @author Ronny Br�unlich
 */
@RunWith(Parameterized.class)
public class CamundaExtensionsTest {

  private Process process;
  private StartEvent startEvent;
  private SequenceFlow sequenceFlow;
  private UserTask userTask;
  private ServiceTask serviceTask;
  private SendTask sendTask;
  private ScriptTask scriptTask;
  private CallActivity callActivity;
  private BusinessRuleTask businessRuleTask;
  private EndEvent endEvent;
  private MessageEventDefinition messageEventDefinition;
  private ParallelGateway parallelGateway;
  private final String namespace;
  private final BpmnModelInstance originalModelInstance;
  private BpmnModelInstance modelInstance;

  @Parameters(name = "Namespace: {0}")
  public static Collection<Object[]> parameters() {
    return Arrays.asList(
        new Object[][] {
          {
            CAMUNDA_NS,
            Bpmn.readModelFromStream(
                CamundaExtensionsTest.class.getResourceAsStream("CamundaExtensionsTest.xml"))
          },
          // for compatability reasons we gotta check the old namespace, too
          {
            ACTIVITI_NS,
            Bpmn.readModelFromStream(
                CamundaExtensionsTest.class.getResourceAsStream(
                    "CamundaExtensionsCompatabilityTest.xml"))
          }
        });
  }

  public CamundaExtensionsTest(String namespace, BpmnModelInstance modelInstance) {
    this.namespace = namespace;
    this.originalModelInstance = modelInstance;
  }

  @Before
  public void setUp() {
    modelInstance = originalModelInstance.clone();
    process = modelInstance.getModelElementById(PROCESS_ID);
    startEvent = modelInstance.getModelElementById(START_EVENT_ID);
    sequenceFlow = modelInstance.getModelElementById(SEQUENCE_FLOW_ID);
    userTask = modelInstance.getModelElementById(USER_TASK_ID);
    serviceTask = modelInstance.getModelElementById(SERVICE_TASK_ID);
    sendTask = modelInstance.getModelElementById(SEND_TASK_ID);
    scriptTask = modelInstance.getModelElementById(SCRIPT_TASK_ID);
    callActivity = modelInstance.getModelElementById(CALL_ACTIVITY_ID);
    businessRuleTask = modelInstance.getModelElementById(BUSINESS_RULE_TASK);
    endEvent = modelInstance.getModelElementById(END_EVENT_ID);
    messageEventDefinition =
        (MessageEventDefinition) endEvent.getEventDefinitions().iterator().next();
    parallelGateway = modelInstance.getModelElementById("parallelGateway");
  }

  @Test
  public void testAssignee() {
    assertThat(userTask.getCamundaAssignee()).isEqualTo(TEST_STRING_XML);
    userTask.setCamundaAssignee(TEST_STRING_API);
    assertThat(userTask.getCamundaAssignee()).isEqualTo(TEST_STRING_API);
  }

  @Test
  public void testAsync() {
    assertThat(startEvent.isCamundaAsync()).isFalse();
    assertThat(userTask.isCamundaAsync()).isTrue();
    assertThat(parallelGateway.isCamundaAsync()).isTrue();

    startEvent.setCamundaAsync(true);
    userTask.setCamundaAsync(false);
    parallelGateway.setCamundaAsync(false);

    assertThat(startEvent.isCamundaAsync()).isTrue();
    assertThat(userTask.isCamundaAsync()).isFalse();
    assertThat(parallelGateway.isCamundaAsync()).isFalse();
  }

  @Test
  public void testAsyncBefore() {
    assertThat(startEvent.isCamundaAsyncBefore()).isTrue();
    assertThat(endEvent.isCamundaAsyncBefore()).isTrue();
    assertThat(userTask.isCamundaAsyncBefore()).isTrue();
    assertThat(parallelGateway.isCamundaAsyncBefore()).isTrue();

    startEvent.setCamundaAsyncBefore(false);
    endEvent.setCamundaAsyncBefore(false);
    userTask.setCamundaAsyncBefore(false);
    parallelGateway.setCamundaAsyncBefore(false);

    assertThat(startEvent.isCamundaAsyncBefore()).isFalse();
    assertThat(endEvent.isCamundaAsyncBefore()).isFalse();
    assertThat(userTask.isCamundaAsyncBefore()).isFalse();
    assertThat(parallelGateway.isCamundaAsyncBefore()).isFalse();
  }

  @Test
  public void testAsyncAfter() {
    assertThat(startEvent.isCamundaAsyncAfter()).isTrue();
    assertThat(endEvent.isCamundaAsyncAfter()).isTrue();
    assertThat(userTask.isCamundaAsyncAfter()).isTrue();
    assertThat(parallelGateway.isCamundaAsyncAfter()).isTrue();

    startEvent.setCamundaAsyncAfter(false);
    endEvent.setCamundaAsyncAfter(false);
    userTask.setCamundaAsyncAfter(false);
    parallelGateway.setCamundaAsyncAfter(false);

    assertThat(startEvent.isCamundaAsyncAfter()).isFalse();
    assertThat(endEvent.isCamundaAsyncAfter()).isFalse();
    assertThat(userTask.isCamundaAsyncAfter()).isFalse();
    assertThat(parallelGateway.isCamundaAsyncAfter()).isFalse();
  }

  @Test
  public void testFlowNodeJobPriority() {
    assertThat(startEvent.getCamundaJobPriority()).isEqualTo(TEST_FLOW_NODE_JOB_PRIORITY);
    assertThat(endEvent.getCamundaJobPriority()).isEqualTo(TEST_FLOW_NODE_JOB_PRIORITY);
    assertThat(userTask.getCamundaJobPriority()).isEqualTo(TEST_FLOW_NODE_JOB_PRIORITY);
    assertThat(parallelGateway.getCamundaJobPriority()).isEqualTo(TEST_FLOW_NODE_JOB_PRIORITY);
  }

  @Test
  public void testProcessJobPriority() {
    assertThat(process.getCamundaJobPriority()).isEqualTo(TEST_PROCESS_JOB_PRIORITY);
  }

  @Test
  public void testProcessTaskPriority() {
    assertThat(process.getCamundaTaskPriority()).isEqualTo(TEST_PROCESS_TASK_PRIORITY);
  }

  @Test
  public void testHistoryTimeToLive() {
    assertThat(process.getCamundaHistoryTimeToLive()).isEqualTo(TEST_HISTORY_TIME_TO_LIVE);
  }

  @Test
  public void testIsStartableInTasklist() {
    assertThat(process.isCamundaStartableInTasklist()).isEqualTo(false);
  }

  @Test
  public void testVersionTag() {
    assertThat(process.getCamundaVersionTag()).isEqualTo("v1.0.0");
  }

  @Test
  public void testServiceTaskPriority() {
    assertThat(serviceTask.getCamundaTaskPriority()).isEqualTo(TEST_SERVICE_TASK_PRIORITY);
  }

  @Test
  public void testCalledElementBinding() {
    assertThat(callActivity.getCamundaCalledElementBinding()).isEqualTo(TEST_STRING_XML);
    callActivity.setCamundaCalledElementBinding(TEST_STRING_API);
    assertThat(callActivity.getCamundaCalledElementBinding()).isEqualTo(TEST_STRING_API);
  }

  @Test
  public void testCalledElementVersion() {
    assertThat(callActivity.getCamundaCalledElementVersion()).isEqualTo(TEST_STRING_XML);
    callActivity.setCamundaCalledElementVersion(TEST_STRING_API);
    assertThat(callActivity.getCamundaCalledElementVersion()).isEqualTo(TEST_STRING_API);
  }

  @Test
  public void testCalledElementVersionTag() {
    assertThat(callActivity.getCamundaCalledElementVersionTag()).isEqualTo(TEST_STRING_XML);
    callActivity.setCamundaCalledElementVersionTag(TEST_STRING_API);
    assertThat(callActivity.getCamundaCalledElementVersionTag()).isEqualTo(TEST_STRING_API);
  }

  @Test
  public void testCalledElementTenantId() {
    assertThat(callActivity.getCamundaCalledElementTenantId()).isEqualTo(TEST_STRING_XML);
    callActivity.setCamundaCalledElementTenantId(TEST_STRING_API);
    assertThat(callActivity.getCamundaCalledElementTenantId()).isEqualTo(TEST_STRING_API);
  }

  @Test
  public void testCaseRef() {
    assertThat(callActivity.getCamundaCaseRef()).isEqualTo(TEST_STRING_XML);
    callActivity.setCamundaCaseRef(TEST_STRING_API);
    assertThat(callActivity.getCamundaCaseRef()).isEqualTo(TEST_STRING_API);
  }

  @Test
  public void testCaseBinding() {
    assertThat(callActivity.getCamundaCaseBinding()).isEqualTo(TEST_STRING_XML);
    callActivity.setCamundaCaseBinding(TEST_STRING_API);
    assertThat(callActivity.getCamundaCaseBinding()).isEqualTo(TEST_STRING_API);
  }

  @Test
  public void testCaseVersion() {
    assertThat(callActivity.getCamundaCaseVersion()).isEqualTo(TEST_STRING_XML);
    callActivity.setCamundaCaseVersion(TEST_STRING_API);
    assertThat(callActivity.getCamundaCaseVersion()).isEqualTo(TEST_STRING_API);
  }

  @Test
  public void testCaseTenantId() {
    assertThat(callActivity.getCamundaCaseTenantId()).isEqualTo(TEST_STRING_XML);
    callActivity.setCamundaCaseTenantId(TEST_STRING_API);
    assertThat(callActivity.getCamundaCaseTenantId()).isEqualTo(TEST_STRING_API);
  }

  @Test
  public void testDecisionRef() {
    assertThat(businessRuleTask.getCamundaDecisionRef()).isEqualTo(TEST_STRING_XML);
    businessRuleTask.setCamundaDecisionRef(TEST_STRING_API);
    assertThat(businessRuleTask.getCamundaDecisionRef()).isEqualTo(TEST_STRING_API);
  }

  @Test
  public void testDecisionRefBinding() {
    assertThat(businessRuleTask.getCamundaDecisionRefBinding()).isEqualTo(TEST_STRING_XML);
    businessRuleTask.setCamundaDecisionRefBinding(TEST_STRING_API);
    assertThat(businessRuleTask.getCamundaDecisionRefBinding()).isEqualTo(TEST_STRING_API);
  }

  @Test
  public void testDecisionRefVersion() {
    assertThat(businessRuleTask.getCamundaDecisionRefVersion()).isEqualTo(TEST_STRING_XML);
    businessRuleTask.setCamundaDecisionRefVersion(TEST_STRING_API);
    assertThat(businessRuleTask.getCamundaDecisionRefVersion()).isEqualTo(TEST_STRING_API);
  }

  @Test
  public void testDecisionRefVersionTag() {
    assertThat(businessRuleTask.getCamundaDecisionRefVersionTag()).isEqualTo(TEST_STRING_XML);
    businessRuleTask.setCamundaDecisionRefVersionTag(TEST_STRING_API);
    assertThat(businessRuleTask.getCamundaDecisionRefVersionTag()).isEqualTo(TEST_STRING_API);
  }

  @Test
  public void testDecisionRefTenantId() {
    assertThat(businessRuleTask.getCamundaDecisionRefTenantId()).isEqualTo(TEST_STRING_XML);
    businessRuleTask.setCamundaDecisionRefTenantId(TEST_STRING_API);
    assertThat(businessRuleTask.getCamundaDecisionRefTenantId()).isEqualTo(TEST_STRING_API);
  }

  @Test
  public void testMapDecisionResult() {
    assertThat(businessRuleTask.getCamundaMapDecisionResult()).isEqualTo(TEST_STRING_XML);
    businessRuleTask.setCamundaMapDecisionResult(TEST_STRING_API);
    assertThat(businessRuleTask.getCamundaMapDecisionResult()).isEqualTo(TEST_STRING_API);
  }

  @Test
  public void testTaskPriority() {
    assertThat(businessRuleTask.getCamundaTaskPriority()).isEqualTo(TEST_STRING_XML);
    businessRuleTask.setCamundaTaskPriority(TEST_SERVICE_TASK_PRIORITY);
    assertThat(businessRuleTask.getCamundaTaskPriority()).isEqualTo(TEST_SERVICE_TASK_PRIORITY);
  }

  @Test
  public void testCandidateGroups() {
    assertThat(userTask.getCamundaCandidateGroups()).isEqualTo(TEST_GROUPS_XML);
    assertThat(userTask.getCamundaCandidateGroupsList()).containsAll(TEST_GROUPS_LIST_XML);
    userTask.setCamundaCandidateGroups(TEST_GROUPS_API);
    assertThat(userTask.getCamundaCandidateGroups()).isEqualTo(TEST_GROUPS_API);
    assertThat(userTask.getCamundaCandidateGroupsList()).containsAll(TEST_GROUPS_LIST_API);
    userTask.setCamundaCandidateGroupsList(TEST_GROUPS_LIST_XML);
    assertThat(userTask.getCamundaCandidateGroups()).isEqualTo(TEST_GROUPS_XML);
    assertThat(userTask.getCamundaCandidateGroupsList()).containsAll(TEST_GROUPS_LIST_XML);
  }

  @Test
  public void testCandidateStarterGroups() {
    assertThat(process.getCamundaCandidateStarterGroups()).isEqualTo(TEST_GROUPS_XML);
    assertThat(process.getCamundaCandidateStarterGroupsList()).containsAll(TEST_GROUPS_LIST_XML);
    process.setCamundaCandidateStarterGroups(TEST_GROUPS_API);
    assertThat(process.getCamundaCandidateStarterGroups()).isEqualTo(TEST_GROUPS_API);
    assertThat(process.getCamundaCandidateStarterGroupsList()).containsAll(TEST_GROUPS_LIST_API);
    process.setCamundaCandidateStarterGroupsList(TEST_GROUPS_LIST_XML);
    assertThat(process.getCamundaCandidateStarterGroups()).isEqualTo(TEST_GROUPS_XML);
    assertThat(process.getCamundaCandidateStarterGroupsList()).containsAll(TEST_GROUPS_LIST_XML);
  }

  @Test
  public void testCandidateStarterUsers() {
    assertThat(process.getCamundaCandidateStarterUsers()).isEqualTo(TEST_USERS_XML);
    assertThat(process.getCamundaCandidateStarterUsersList()).containsAll(TEST_USERS_LIST_XML);
    process.setCamundaCandidateStarterUsers(TEST_USERS_API);
    assertThat(process.getCamundaCandidateStarterUsers()).isEqualTo(TEST_USERS_API);
    assertThat(process.getCamundaCandidateStarterUsersList()).containsAll(TEST_USERS_LIST_API);
    process.setCamundaCandidateStarterUsersList(TEST_USERS_LIST_XML);
    assertThat(process.getCamundaCandidateStarterUsers()).isEqualTo(TEST_USERS_XML);
    assertThat(process.getCamundaCandidateStarterUsersList()).containsAll(TEST_USERS_LIST_XML);
  }

  @Test
  public void testCandidateUsers() {
    assertThat(userTask.getCamundaCandidateUsers()).isEqualTo(TEST_USERS_XML);
    assertThat(userTask.getCamundaCandidateUsersList()).containsAll(TEST_USERS_LIST_XML);
    userTask.setCamundaCandidateUsers(TEST_USERS_API);
    assertThat(userTask.getCamundaCandidateUsers()).isEqualTo(TEST_USERS_API);
    assertThat(userTask.getCamundaCandidateUsersList()).containsAll(TEST_USERS_LIST_API);
    userTask.setCamundaCandidateUsersList(TEST_USERS_LIST_XML);
    assertThat(userTask.getCamundaCandidateUsers()).isEqualTo(TEST_USERS_XML);
    assertThat(userTask.getCamundaCandidateUsersList()).containsAll(TEST_USERS_LIST_XML);
  }

  @Test
  public void testClass() {
    assertThat(serviceTask.getCamundaClass()).isEqualTo(TEST_CLASS_XML);
    assertThat(messageEventDefinition.getCamundaClass()).isEqualTo(TEST_CLASS_XML);

    serviceTask.setCamundaClass(TEST_CLASS_API);
    messageEventDefinition.setCamundaClass(TEST_CLASS_API);

    assertThat(serviceTask.getCamundaClass()).isEqualTo(TEST_CLASS_API);
    assertThat(messageEventDefinition.getCamundaClass()).isEqualTo(TEST_CLASS_API);
  }

  @Test
  public void testDelegateExpression() {
    assertThat(serviceTask.getCamundaDelegateExpression()).isEqualTo(TEST_DELEGATE_EXPRESSION_XML);
    assertThat(messageEventDefinition.getCamundaDelegateExpression())
        .isEqualTo(TEST_DELEGATE_EXPRESSION_XML);

    serviceTask.setCamundaDelegateExpression(TEST_DELEGATE_EXPRESSION_API);
    messageEventDefinition.setCamundaDelegateExpression(TEST_DELEGATE_EXPRESSION_API);

    assertThat(serviceTask.getCamundaDelegateExpression()).isEqualTo(TEST_DELEGATE_EXPRESSION_API);
    assertThat(messageEventDefinition.getCamundaDelegateExpression())
        .isEqualTo(TEST_DELEGATE_EXPRESSION_API);
  }

  @Test
  public void testDueDate() {
    assertThat(userTask.getCamundaDueDate()).isEqualTo(TEST_DUE_DATE_XML);
    userTask.setCamundaDueDate(TEST_DUE_DATE_API);
    assertThat(userTask.getCamundaDueDate()).isEqualTo(TEST_DUE_DATE_API);
  }

  @Test
  public void testErrorCodeVariable() {
    final ErrorEventDefinition errorEventDefinition =
        startEvent.getChildElementsByType(ErrorEventDefinition.class).iterator().next();
    assertThat(
            errorEventDefinition.getAttributeValueNs(
                namespace, CAMUNDA_ATTRIBUTE_ERROR_CODE_VARIABLE))
        .isEqualTo("errorVariable");
  }

  @Test
  public void testErrorMessageVariable() {
    final ErrorEventDefinition errorEventDefinition =
        startEvent.getChildElementsByType(ErrorEventDefinition.class).iterator().next();
    assertThat(
            errorEventDefinition.getAttributeValueNs(
                namespace, CAMUNDA_ATTRIBUTE_ERROR_MESSAGE_VARIABLE))
        .isEqualTo("errorMessageVariable");
  }

  @Test
  public void testExclusive() {
    assertThat(startEvent.isCamundaExclusive()).isTrue();
    assertThat(userTask.isCamundaExclusive()).isFalse();
    userTask.setCamundaExclusive(true);
    assertThat(userTask.isCamundaExclusive()).isTrue();
    assertThat(parallelGateway.isCamundaExclusive()).isTrue();
    parallelGateway.setCamundaExclusive(false);
    assertThat(parallelGateway.isCamundaExclusive()).isFalse();

    assertThat(callActivity.isCamundaExclusive()).isFalse();
    callActivity.setCamundaExclusive(true);
    assertThat(callActivity.isCamundaExclusive()).isTrue();
  }

  @Test
  public void testExpression() {
    assertThat(serviceTask.getCamundaExpression()).isEqualTo(TEST_EXPRESSION_XML);
    assertThat(messageEventDefinition.getCamundaExpression()).isEqualTo(TEST_EXPRESSION_XML);
    serviceTask.setCamundaExpression(TEST_EXPRESSION_API);
    messageEventDefinition.setCamundaExpression(TEST_EXPRESSION_API);
    assertThat(serviceTask.getCamundaExpression()).isEqualTo(TEST_EXPRESSION_API);
    assertThat(messageEventDefinition.getCamundaExpression()).isEqualTo(TEST_EXPRESSION_API);
  }

  @Test
  public void testFormHandlerClass() {
    assertThat(startEvent.getCamundaFormHandlerClass()).isEqualTo(TEST_CLASS_XML);
    assertThat(userTask.getCamundaFormHandlerClass()).isEqualTo(TEST_CLASS_XML);
    startEvent.setCamundaFormHandlerClass(TEST_CLASS_API);
    userTask.setCamundaFormHandlerClass(TEST_CLASS_API);
    assertThat(startEvent.getCamundaFormHandlerClass()).isEqualTo(TEST_CLASS_API);
    assertThat(userTask.getCamundaFormHandlerClass()).isEqualTo(TEST_CLASS_API);
  }

  @Test
  public void testFormKey() {
    assertThat(startEvent.getCamundaFormKey()).isEqualTo(TEST_STRING_XML);
    assertThat(userTask.getCamundaFormKey()).isEqualTo(TEST_STRING_XML);
    startEvent.setCamundaFormKey(TEST_STRING_API);
    userTask.setCamundaFormKey(TEST_STRING_API);
    assertThat(startEvent.getCamundaFormKey()).isEqualTo(TEST_STRING_API);
    assertThat(userTask.getCamundaFormKey()).isEqualTo(TEST_STRING_API);
  }

  @Test
  public void testInitiator() {
    assertThat(startEvent.getCamundaInitiator()).isEqualTo(TEST_STRING_XML);
    startEvent.setCamundaInitiator(TEST_STRING_API);
    assertThat(startEvent.getCamundaInitiator()).isEqualTo(TEST_STRING_API);
  }

  @Test
  public void testPriority() {
    assertThat(userTask.getCamundaPriority()).isEqualTo(TEST_PRIORITY_XML);
    userTask.setCamundaPriority(TEST_PRIORITY_API);
    assertThat(userTask.getCamundaPriority()).isEqualTo(TEST_PRIORITY_API);
  }

  @Test
  public void testResultVariable() {
    assertThat(serviceTask.getCamundaResultVariable()).isEqualTo(TEST_STRING_XML);
    assertThat(messageEventDefinition.getCamundaResultVariable()).isEqualTo(TEST_STRING_XML);
    serviceTask.setCamundaResultVariable(TEST_STRING_API);
    messageEventDefinition.setCamundaResultVariable(TEST_STRING_API);
    assertThat(serviceTask.getCamundaResultVariable()).isEqualTo(TEST_STRING_API);
    assertThat(messageEventDefinition.getCamundaResultVariable()).isEqualTo(TEST_STRING_API);
  }

  @Test
  public void testType() {
    assertThat(serviceTask.getCamundaType()).isEqualTo(TEST_TYPE_XML);
    assertThat(messageEventDefinition.getCamundaType()).isEqualTo(TEST_STRING_XML);
    serviceTask.setCamundaType(TEST_TYPE_API);
    messageEventDefinition.setCamundaType(TEST_STRING_API);
    assertThat(serviceTask.getCamundaType()).isEqualTo(TEST_TYPE_API);
    assertThat(messageEventDefinition.getCamundaType()).isEqualTo(TEST_STRING_API);
  }

  @Test
  public void testTopic() {
    assertThat(serviceTask.getCamundaTopic()).isEqualTo(TEST_STRING_XML);
    assertThat(messageEventDefinition.getCamundaTopic()).isEqualTo(TEST_STRING_XML);
    serviceTask.setCamundaTopic(TEST_TYPE_API);
    messageEventDefinition.setCamundaTopic(TEST_STRING_API);
    assertThat(serviceTask.getCamundaTopic()).isEqualTo(TEST_TYPE_API);
    assertThat(messageEventDefinition.getCamundaTopic()).isEqualTo(TEST_STRING_API);
  }

  @Test
  public void testVariableMappingClass() {
    assertThat(callActivity.getCamundaVariableMappingClass()).isEqualTo(TEST_CLASS_XML);
    callActivity.setCamundaVariableMappingClass(TEST_CLASS_API);
    assertThat(callActivity.getCamundaVariableMappingClass()).isEqualTo(TEST_CLASS_API);
  }

  @Test
  public void testVariableMappingDelegateExpression() {
    assertThat(callActivity.getCamundaVariableMappingDelegateExpression())
        .isEqualTo(TEST_DELEGATE_EXPRESSION_XML);
    callActivity.setCamundaVariableMappingDelegateExpression(TEST_DELEGATE_EXPRESSION_API);
    assertThat(callActivity.getCamundaVariableMappingDelegateExpression())
        .isEqualTo(TEST_DELEGATE_EXPRESSION_API);
  }

  @Test
  public void testExecutionListenerExtension() {
    final CamundaExecutionListener processListener =
        process
            .getExtensionElements()
            .getElementsQuery()
            .filterByType(CamundaExecutionListener.class)
            .singleResult();
    final CamundaExecutionListener startEventListener =
        startEvent
            .getExtensionElements()
            .getElementsQuery()
            .filterByType(CamundaExecutionListener.class)
            .singleResult();
    final CamundaExecutionListener serviceTaskListener =
        serviceTask
            .getExtensionElements()
            .getElementsQuery()
            .filterByType(CamundaExecutionListener.class)
            .singleResult();
    assertThat(processListener.getCamundaClass()).isEqualTo(TEST_CLASS_XML);
    assertThat(processListener.getCamundaEvent()).isEqualTo(TEST_EXECUTION_EVENT_XML);
    assertThat(startEventListener.getCamundaExpression()).isEqualTo(TEST_EXPRESSION_XML);
    assertThat(startEventListener.getCamundaEvent()).isEqualTo(TEST_EXECUTION_EVENT_XML);
    assertThat(serviceTaskListener.getCamundaDelegateExpression())
        .isEqualTo(TEST_DELEGATE_EXPRESSION_XML);
    assertThat(serviceTaskListener.getCamundaEvent()).isEqualTo(TEST_EXECUTION_EVENT_XML);
    processListener.setCamundaClass(TEST_CLASS_API);
    processListener.setCamundaEvent(TEST_EXECUTION_EVENT_API);
    startEventListener.setCamundaExpression(TEST_EXPRESSION_API);
    startEventListener.setCamundaEvent(TEST_EXECUTION_EVENT_API);
    serviceTaskListener.setCamundaDelegateExpression(TEST_DELEGATE_EXPRESSION_API);
    serviceTaskListener.setCamundaEvent(TEST_EXECUTION_EVENT_API);
    assertThat(processListener.getCamundaClass()).isEqualTo(TEST_CLASS_API);
    assertThat(processListener.getCamundaEvent()).isEqualTo(TEST_EXECUTION_EVENT_API);
    assertThat(startEventListener.getCamundaExpression()).isEqualTo(TEST_EXPRESSION_API);
    assertThat(startEventListener.getCamundaEvent()).isEqualTo(TEST_EXECUTION_EVENT_API);
    assertThat(serviceTaskListener.getCamundaDelegateExpression())
        .isEqualTo(TEST_DELEGATE_EXPRESSION_API);
    assertThat(serviceTaskListener.getCamundaEvent()).isEqualTo(TEST_EXECUTION_EVENT_API);
  }

  @Test
  public void testCamundaScriptExecutionListener() {
    final CamundaExecutionListener sequenceFlowListener =
        sequenceFlow
            .getExtensionElements()
            .getElementsQuery()
            .filterByType(CamundaExecutionListener.class)
            .singleResult();

    CamundaScript script = sequenceFlowListener.getCamundaScript();
    assertThat(script.getCamundaScriptFormat()).isEqualTo("groovy");
    assertThat(script.getCamundaResource()).isNull();
    assertThat(script.getTextContent()).isEqualTo("println 'Hello World'");

    final CamundaScript newScript = modelInstance.newInstance(CamundaScript.class);
    newScript.setCamundaScriptFormat("groovy");
    newScript.setCamundaResource("test.groovy");
    sequenceFlowListener.setCamundaScript(newScript);

    script = sequenceFlowListener.getCamundaScript();
    assertThat(script.getCamundaScriptFormat()).isEqualTo("groovy");
    assertThat(script.getCamundaResource()).isEqualTo("test.groovy");
    assertThat(script.getTextContent()).isEmpty();
  }

  @Test
  public void testFailedJobRetryTimeCycleExtension() {
    final CamundaFailedJobRetryTimeCycle timeCycle =
        sendTask
            .getExtensionElements()
            .getElementsQuery()
            .filterByType(CamundaFailedJobRetryTimeCycle.class)
            .singleResult();
    assertThat(timeCycle.getTextContent()).isEqualTo(TEST_STRING_XML);
    timeCycle.setTextContent(TEST_STRING_API);
    assertThat(timeCycle.getTextContent()).isEqualTo(TEST_STRING_API);
  }

  @Test
  public void testFieldExtension() {
    final CamundaField field =
        sendTask
            .getExtensionElements()
            .getElementsQuery()
            .filterByType(CamundaField.class)
            .singleResult();
    assertThat(field.getCamundaName()).isEqualTo(TEST_STRING_XML);
    assertThat(field.getCamundaExpression()).isEqualTo(TEST_EXPRESSION_XML);
    assertThat(field.getCamundaStringValue()).isEqualTo(TEST_STRING_XML);
    assertThat(field.getCamundaExpressionChild().getTextContent()).isEqualTo(TEST_EXPRESSION_XML);
    assertThat(field.getCamundaString().getTextContent()).isEqualTo(TEST_STRING_XML);
    field.setCamundaName(TEST_STRING_API);
    field.setCamundaExpression(TEST_EXPRESSION_API);
    field.setCamundaStringValue(TEST_STRING_API);
    field.getCamundaExpressionChild().setTextContent(TEST_EXPRESSION_API);
    field.getCamundaString().setTextContent(TEST_STRING_API);
    assertThat(field.getCamundaName()).isEqualTo(TEST_STRING_API);
    assertThat(field.getCamundaExpression()).isEqualTo(TEST_EXPRESSION_API);
    assertThat(field.getCamundaStringValue()).isEqualTo(TEST_STRING_API);
    assertThat(field.getCamundaExpressionChild().getTextContent()).isEqualTo(TEST_EXPRESSION_API);
    assertThat(field.getCamundaString().getTextContent()).isEqualTo(TEST_STRING_API);
  }

  @Test
  public void testFormData() {
    final CamundaFormData formData =
        userTask
            .getExtensionElements()
            .getElementsQuery()
            .filterByType(CamundaFormData.class)
            .singleResult();
    final CamundaFormField formField = formData.getCamundaFormFields().iterator().next();
    assertThat(formField.getCamundaId()).isEqualTo(TEST_STRING_XML);
    assertThat(formField.getCamundaLabel()).isEqualTo(TEST_STRING_XML);
    assertThat(formField.getCamundaType()).isEqualTo(TEST_STRING_XML);
    assertThat(formField.getCamundaDatePattern()).isEqualTo(TEST_STRING_XML);
    assertThat(formField.getCamundaDefaultValue()).isEqualTo(TEST_STRING_XML);
    formField.setCamundaId(TEST_STRING_API);
    formField.setCamundaLabel(TEST_STRING_API);
    formField.setCamundaType(TEST_STRING_API);
    formField.setCamundaDatePattern(TEST_STRING_API);
    formField.setCamundaDefaultValue(TEST_STRING_API);
    assertThat(formField.getCamundaId()).isEqualTo(TEST_STRING_API);
    assertThat(formField.getCamundaLabel()).isEqualTo(TEST_STRING_API);
    assertThat(formField.getCamundaType()).isEqualTo(TEST_STRING_API);
    assertThat(formField.getCamundaDatePattern()).isEqualTo(TEST_STRING_API);
    assertThat(formField.getCamundaDefaultValue()).isEqualTo(TEST_STRING_API);

    final CamundaProperty property =
        formField.getCamundaProperties().getCamundaProperties().iterator().next();
    assertThat(property.getCamundaId()).isEqualTo(TEST_STRING_XML);
    assertThat(property.getCamundaValue()).isEqualTo(TEST_STRING_XML);
    property.setCamundaId(TEST_STRING_API);
    property.setCamundaValue(TEST_STRING_API);
    assertThat(property.getCamundaId()).isEqualTo(TEST_STRING_API);
    assertThat(property.getCamundaValue()).isEqualTo(TEST_STRING_API);

    final CamundaConstraint constraint =
        formField.getCamundaValidation().getCamundaConstraints().iterator().next();
    assertThat(constraint.getCamundaName()).isEqualTo(TEST_STRING_XML);
    assertThat(constraint.getCamundaConfig()).isEqualTo(TEST_STRING_XML);
    constraint.setCamundaName(TEST_STRING_API);
    constraint.setCamundaConfig(TEST_STRING_API);
    assertThat(constraint.getCamundaName()).isEqualTo(TEST_STRING_API);
    assertThat(constraint.getCamundaConfig()).isEqualTo(TEST_STRING_API);

    final CamundaValue value = formField.getCamundaValues().iterator().next();
    assertThat(value.getCamundaId()).isEqualTo(TEST_STRING_XML);
    assertThat(value.getCamundaName()).isEqualTo(TEST_STRING_XML);
    value.setCamundaId(TEST_STRING_API);
    value.setCamundaName(TEST_STRING_API);
    assertThat(value.getCamundaId()).isEqualTo(TEST_STRING_API);
    assertThat(value.getCamundaName()).isEqualTo(TEST_STRING_API);
  }

  @Test
  public void testFormProperty() {
    final CamundaFormProperty formProperty =
        startEvent
            .getExtensionElements()
            .getElementsQuery()
            .filterByType(CamundaFormProperty.class)
            .singleResult();
    assertThat(formProperty.getCamundaId()).isEqualTo(TEST_STRING_XML);
    assertThat(formProperty.getCamundaName()).isEqualTo(TEST_STRING_XML);
    assertThat(formProperty.getCamundaType()).isEqualTo(TEST_STRING_XML);
    assertThat(formProperty.isCamundaRequired()).isFalse();
    assertThat(formProperty.isCamundaReadable()).isTrue();
    assertThat(formProperty.isCamundaWriteable()).isTrue();
    assertThat(formProperty.getCamundaVariable()).isEqualTo(TEST_STRING_XML);
    assertThat(formProperty.getCamundaExpression()).isEqualTo(TEST_EXPRESSION_XML);
    assertThat(formProperty.getCamundaDatePattern()).isEqualTo(TEST_STRING_XML);
    assertThat(formProperty.getCamundaDefault()).isEqualTo(TEST_STRING_XML);
    formProperty.setCamundaId(TEST_STRING_API);
    formProperty.setCamundaName(TEST_STRING_API);
    formProperty.setCamundaType(TEST_STRING_API);
    formProperty.setCamundaRequired(true);
    formProperty.setCamundaReadable(false);
    formProperty.setCamundaWriteable(false);
    formProperty.setCamundaVariable(TEST_STRING_API);
    formProperty.setCamundaExpression(TEST_EXPRESSION_API);
    formProperty.setCamundaDatePattern(TEST_STRING_API);
    formProperty.setCamundaDefault(TEST_STRING_API);
    assertThat(formProperty.getCamundaId()).isEqualTo(TEST_STRING_API);
    assertThat(formProperty.getCamundaName()).isEqualTo(TEST_STRING_API);
    assertThat(formProperty.getCamundaType()).isEqualTo(TEST_STRING_API);
    assertThat(formProperty.isCamundaRequired()).isTrue();
    assertThat(formProperty.isCamundaReadable()).isFalse();
    assertThat(formProperty.isCamundaWriteable()).isFalse();
    assertThat(formProperty.getCamundaVariable()).isEqualTo(TEST_STRING_API);
    assertThat(formProperty.getCamundaExpression()).isEqualTo(TEST_EXPRESSION_API);
    assertThat(formProperty.getCamundaDatePattern()).isEqualTo(TEST_STRING_API);
    assertThat(formProperty.getCamundaDefault()).isEqualTo(TEST_STRING_API);
  }

  @Test
  public void testInExtension() {
    final CamundaIn in =
        callActivity
            .getExtensionElements()
            .getElementsQuery()
            .filterByType(CamundaIn.class)
            .singleResult();
    assertThat(in.getCamundaSource()).isEqualTo(TEST_STRING_XML);
    assertThat(in.getCamundaSourceExpression()).isEqualTo(TEST_EXPRESSION_XML);
    assertThat(in.getCamundaVariables()).isEqualTo(TEST_STRING_XML);
    assertThat(in.getCamundaTarget()).isEqualTo(TEST_STRING_XML);
    assertThat(in.getCamundaBusinessKey()).isEqualTo(TEST_EXPRESSION_XML);
    assertThat(in.getCamundaLocal()).isTrue();
    in.setCamundaSource(TEST_STRING_API);
    in.setCamundaSourceExpression(TEST_EXPRESSION_API);
    in.setCamundaVariables(TEST_STRING_API);
    in.setCamundaTarget(TEST_STRING_API);
    in.setCamundaBusinessKey(TEST_EXPRESSION_API);
    in.setCamundaLocal(false);
    assertThat(in.getCamundaSource()).isEqualTo(TEST_STRING_API);
    assertThat(in.getCamundaSourceExpression()).isEqualTo(TEST_EXPRESSION_API);
    assertThat(in.getCamundaVariables()).isEqualTo(TEST_STRING_API);
    assertThat(in.getCamundaTarget()).isEqualTo(TEST_STRING_API);
    assertThat(in.getCamundaBusinessKey()).isEqualTo(TEST_EXPRESSION_API);
    assertThat(in.getCamundaLocal()).isFalse();
  }

  @Test
  public void testOutExtension() {
    final CamundaOut out =
        callActivity
            .getExtensionElements()
            .getElementsQuery()
            .filterByType(CamundaOut.class)
            .singleResult();
    assertThat(out.getCamundaSource()).isEqualTo(TEST_STRING_XML);
    assertThat(out.getCamundaSourceExpression()).isEqualTo(TEST_EXPRESSION_XML);
    assertThat(out.getCamundaVariables()).isEqualTo(TEST_STRING_XML);
    assertThat(out.getCamundaTarget()).isEqualTo(TEST_STRING_XML);
    assertThat(out.getCamundaLocal()).isTrue();
    out.setCamundaSource(TEST_STRING_API);
    out.setCamundaSourceExpression(TEST_EXPRESSION_API);
    out.setCamundaVariables(TEST_STRING_API);
    out.setCamundaTarget(TEST_STRING_API);
    out.setCamundaLocal(false);
    assertThat(out.getCamundaSource()).isEqualTo(TEST_STRING_API);
    assertThat(out.getCamundaSourceExpression()).isEqualTo(TEST_EXPRESSION_API);
    assertThat(out.getCamundaVariables()).isEqualTo(TEST_STRING_API);
    assertThat(out.getCamundaTarget()).isEqualTo(TEST_STRING_API);
    assertThat(out.getCamundaLocal()).isFalse();
  }

  @Test
  public void testPotentialStarter() {
    final CamundaPotentialStarter potentialStarter =
        startEvent
            .getExtensionElements()
            .getElementsQuery()
            .filterByType(CamundaPotentialStarter.class)
            .singleResult();
    final Expression expression =
        potentialStarter.getResourceAssignmentExpression().getExpression();
    assertThat(expression.getTextContent()).isEqualTo(TEST_GROUPS_XML);
    expression.setTextContent(TEST_GROUPS_API);
    assertThat(expression.getTextContent()).isEqualTo(TEST_GROUPS_API);
  }

  @Test
  public void testTaskListener() {
    final CamundaTaskListener taskListener =
        userTask
            .getExtensionElements()
            .getElementsQuery()
            .filterByType(CamundaTaskListener.class)
            .list()
            .get(0);
    assertThat(taskListener.getCamundaEvent()).isEqualTo(TEST_TASK_EVENT_XML);
    assertThat(taskListener.getCamundaClass()).isEqualTo(TEST_CLASS_XML);
    assertThat(taskListener.getCamundaExpression()).isEqualTo(TEST_EXPRESSION_XML);
    assertThat(taskListener.getCamundaDelegateExpression()).isEqualTo(TEST_DELEGATE_EXPRESSION_XML);
    taskListener.setCamundaEvent(TEST_TASK_EVENT_API);
    taskListener.setCamundaClass(TEST_CLASS_API);
    taskListener.setCamundaExpression(TEST_EXPRESSION_API);
    taskListener.setCamundaDelegateExpression(TEST_DELEGATE_EXPRESSION_API);
    assertThat(taskListener.getCamundaEvent()).isEqualTo(TEST_TASK_EVENT_API);
    assertThat(taskListener.getCamundaClass()).isEqualTo(TEST_CLASS_API);
    assertThat(taskListener.getCamundaExpression()).isEqualTo(TEST_EXPRESSION_API);
    assertThat(taskListener.getCamundaDelegateExpression()).isEqualTo(TEST_DELEGATE_EXPRESSION_API);

    final CamundaField field = taskListener.getCamundaFields().iterator().next();
    assertThat(field.getCamundaName()).isEqualTo(TEST_STRING_XML);
    assertThat(field.getCamundaString().getTextContent()).isEqualTo(TEST_STRING_XML);
  }

  @Test
  public void testCamundaScriptTaskListener() {
    final CamundaTaskListener taskListener =
        userTask
            .getExtensionElements()
            .getElementsQuery()
            .filterByType(CamundaTaskListener.class)
            .list()
            .get(1);

    CamundaScript script = taskListener.getCamundaScript();
    assertThat(script.getCamundaScriptFormat()).isEqualTo("groovy");
    assertThat(script.getCamundaResource()).isEqualTo("test.groovy");
    assertThat(script.getTextContent()).isEmpty();

    final CamundaScript newScript = modelInstance.newInstance(CamundaScript.class);
    newScript.setCamundaScriptFormat("groovy");
    newScript.setTextContent("println 'Hello World'");
    taskListener.setCamundaScript(newScript);

    script = taskListener.getCamundaScript();
    assertThat(script.getCamundaScriptFormat()).isEqualTo("groovy");
    assertThat(script.getCamundaResource()).isNull();
    assertThat(script.getTextContent()).isEqualTo("println 'Hello World'");
  }

  @Test
  public void testCamundaModelerProperties() {
    final CamundaProperties camundaProperties =
        endEvent
            .getExtensionElements()
            .getElementsQuery()
            .filterByType(CamundaProperties.class)
            .singleResult();
    assertThat(camundaProperties).isNotNull();
    assertThat(camundaProperties.getCamundaProperties()).hasSize(2);

    for (final CamundaProperty camundaProperty : camundaProperties.getCamundaProperties()) {
      assertThat(camundaProperty.getCamundaId()).isNull();
      assertThat(camundaProperty.getCamundaName()).startsWith("name");
      assertThat(camundaProperty.getCamundaValue()).startsWith("value");
    }
  }

  @Test
  public void testGetNonExistingCamundaCandidateUsers() {
    userTask.removeAttributeNs(namespace, "candidateUsers");
    assertThat(userTask.getCamundaCandidateUsers()).isNull();
    assertThat(userTask.getCamundaCandidateUsersList()).isEmpty();
  }

  @Test
  public void testSetNullCamundaCandidateUsers() {
    assertThat(userTask.getCamundaCandidateUsers()).isNotEmpty();
    assertThat(userTask.getCamundaCandidateUsersList()).isNotEmpty();
    userTask.setCamundaCandidateUsers(null);
    assertThat(userTask.getCamundaCandidateUsers()).isNull();
    assertThat(userTask.getCamundaCandidateUsersList()).isEmpty();
  }

  @Test
  public void testEmptyCamundaCandidateUsers() {
    assertThat(userTask.getCamundaCandidateUsers()).isNotEmpty();
    assertThat(userTask.getCamundaCandidateUsersList()).isNotEmpty();
    userTask.setCamundaCandidateUsers("");
    assertThat(userTask.getCamundaCandidateUsers()).isNull();
    assertThat(userTask.getCamundaCandidateUsersList()).isEmpty();
  }

  @Test
  public void testSetNullCamundaCandidateUsersList() {
    assertThat(userTask.getCamundaCandidateUsers()).isNotEmpty();
    assertThat(userTask.getCamundaCandidateUsersList()).isNotEmpty();
    userTask.setCamundaCandidateUsersList(null);
    assertThat(userTask.getCamundaCandidateUsers()).isNull();
    assertThat(userTask.getCamundaCandidateUsersList()).isEmpty();
  }

  @Test
  public void testEmptyCamundaCandidateUsersList() {
    assertThat(userTask.getCamundaCandidateUsers()).isNotEmpty();
    assertThat(userTask.getCamundaCandidateUsersList()).isNotEmpty();
    userTask.setCamundaCandidateUsersList(Collections.<String>emptyList());
    assertThat(userTask.getCamundaCandidateUsers()).isNull();
    assertThat(userTask.getCamundaCandidateUsersList()).isEmpty();
  }

  @Test
  public void testScriptResource() {
    assertThat(scriptTask.getScriptFormat()).isEqualTo("groovy");
    assertThat(scriptTask.getCamundaResource()).isEqualTo("test.groovy");
  }

  @Test
  public void testCamundaConnector() {
    final CamundaConnector camundaConnector =
        serviceTask
            .getExtensionElements()
            .getElementsQuery()
            .filterByType(CamundaConnector.class)
            .singleResult();
    assertThat(camundaConnector).isNotNull();

    final CamundaConnectorId camundaConnectorId = camundaConnector.getCamundaConnectorId();
    assertThat(camundaConnectorId).isNotNull();
    assertThat(camundaConnectorId.getTextContent()).isEqualTo("soap-http-connector");

    final CamundaInputOutput camundaInputOutput = camundaConnector.getCamundaInputOutput();

    final Collection<CamundaInputParameter> inputParameters =
        camundaInputOutput.getCamundaInputParameters();
    assertThat(inputParameters).hasSize(1);

    final CamundaInputParameter inputParameter = inputParameters.iterator().next();
    assertThat(inputParameter.getCamundaName()).isEqualTo("endpointUrl");
    assertThat(inputParameter.getTextContent()).isEqualTo("http://example.com/webservice");

    final Collection<CamundaOutputParameter> outputParameters =
        camundaInputOutput.getCamundaOutputParameters();
    assertThat(outputParameters).hasSize(1);

    final CamundaOutputParameter outputParameter = outputParameters.iterator().next();
    assertThat(outputParameter.getCamundaName()).isEqualTo("result");
    assertThat(outputParameter.getTextContent()).isEqualTo("output");
  }

  @Test
  public void testCamundaInputOutput() {
    final CamundaInputOutput camundaInputOutput =
        serviceTask
            .getExtensionElements()
            .getElementsQuery()
            .filterByType(CamundaInputOutput.class)
            .singleResult();
    assertThat(camundaInputOutput).isNotNull();
    assertThat(camundaInputOutput.getCamundaInputParameters()).hasSize(6);
    assertThat(camundaInputOutput.getCamundaOutputParameters()).hasSize(1);
  }

  @Test
  public void testCamundaInputParameter() {
    // find existing
    CamundaInputParameter inputParameter =
        findInputParameterByName(serviceTask, "shouldBeConstant");

    // modify existing
    inputParameter.setCamundaName("hello");
    inputParameter.setTextContent("world");
    inputParameter = findInputParameterByName(serviceTask, "hello");
    assertThat(inputParameter.getTextContent()).isEqualTo("world");

    // add new one
    inputParameter = modelInstance.newInstance(CamundaInputParameter.class);
    inputParameter.setCamundaName("abc");
    inputParameter.setTextContent("def");
    serviceTask
        .getExtensionElements()
        .getElementsQuery()
        .filterByType(CamundaInputOutput.class)
        .singleResult()
        .addChildElement(inputParameter);

    // search for new one
    inputParameter = findInputParameterByName(serviceTask, "abc");
    assertThat(inputParameter.getCamundaName()).isEqualTo("abc");
    assertThat(inputParameter.getTextContent()).isEqualTo("def");
  }

  @Test
  public void testCamundaNullInputParameter() {
    final CamundaInputParameter inputParameter =
        findInputParameterByName(serviceTask, "shouldBeNull");
    assertThat(inputParameter.getCamundaName()).isEqualTo("shouldBeNull");
    assertThat(inputParameter.getTextContent()).isEmpty();
  }

  @Test
  public void testCamundaConstantInputParameter() {
    final CamundaInputParameter inputParameter =
        findInputParameterByName(serviceTask, "shouldBeConstant");
    assertThat(inputParameter.getCamundaName()).isEqualTo("shouldBeConstant");
    assertThat(inputParameter.getTextContent()).isEqualTo("foo");
  }

  @Test
  public void testCamundaExpressionInputParameter() {
    final CamundaInputParameter inputParameter =
        findInputParameterByName(serviceTask, "shouldBeExpression");
    assertThat(inputParameter.getCamundaName()).isEqualTo("shouldBeExpression");
    assertThat(inputParameter.getTextContent()).isEqualTo("${1 + 1}");
  }

  @Test
  public void testCamundaListInputParameter() {
    final CamundaInputParameter inputParameter =
        findInputParameterByName(serviceTask, "shouldBeList");
    assertThat(inputParameter.getCamundaName()).isEqualTo("shouldBeList");
    assertThat(inputParameter.getTextContent()).isNotEmpty();
    assertThat(inputParameter.getUniqueChildElementByNameNs(CAMUNDA_NS, "list")).isNotNull();

    CamundaList list = inputParameter.getValue();
    assertThat(list.getValues()).hasSize(3);
    for (final BpmnModelElementInstance values : list.getValues()) {
      assertThat(values.getTextContent()).isIn("a", "b", "c");
    }

    list = modelInstance.newInstance(CamundaList.class);
    for (int i = 0; i < 4; i++) {
      final CamundaValue value = modelInstance.newInstance(CamundaValue.class);
      value.setTextContent("test");
      list.getValues().add(value);
    }
    final Collection<CamundaValue> testValues =
        Arrays.asList(
            modelInstance.newInstance(CamundaValue.class),
            modelInstance.newInstance(CamundaValue.class));
    list.getValues().addAll(testValues);
    inputParameter.setValue(list);

    list = inputParameter.getValue();
    assertThat(list.getValues()).hasSize(6);
    list.getValues().removeAll(testValues);
    final ArrayList<BpmnModelElementInstance> camundaValues = new ArrayList<>(list.getValues());
    assertThat(camundaValues).hasSize(4);
    for (final BpmnModelElementInstance value : camundaValues) {
      assertThat(value.getTextContent()).isEqualTo("test");
    }

    list.getValues().remove(camundaValues.get(1));
    assertThat(list.getValues()).hasSize(3);

    list.getValues().removeAll(Arrays.asList(camundaValues.get(0), camundaValues.get(3)));
    assertThat(list.getValues()).hasSize(1);

    list.getValues().clear();
    assertThat(list.getValues()).isEmpty();

    // test standard list interactions
    final Collection<BpmnModelElementInstance> elements = list.getValues();

    final CamundaValue value = modelInstance.newInstance(CamundaValue.class);
    elements.add(value);

    final List<CamundaValue> newValues = new ArrayList<>();
    newValues.add(modelInstance.newInstance(CamundaValue.class));
    newValues.add(modelInstance.newInstance(CamundaValue.class));
    elements.addAll(newValues);
    assertThat(elements).hasSize(3);

    assertThat(elements).doesNotContain(modelInstance.newInstance(CamundaValue.class));
    assertThat(elements.containsAll(Arrays.asList(modelInstance.newInstance(CamundaValue.class))))
        .isFalse();

    assertThat(elements.remove(modelInstance.newInstance(CamundaValue.class))).isFalse();
    assertThat(elements).hasSize(3);

    assertThat(elements.remove(value)).isTrue();
    assertThat(elements).hasSize(2);

    assertThat(elements.removeAll(newValues)).isTrue();
    assertThat(elements).isEmpty();

    elements.add(modelInstance.newInstance(CamundaValue.class));
    elements.clear();
    assertThat(elements).isEmpty();

    inputParameter.removeValue();
    assertThat(inputParameter.<BpmnModelElementInstance>getValue()).isNull();
  }

  @Test
  public void testCamundaMapInputParameter() {
    final CamundaInputParameter inputParameter =
        findInputParameterByName(serviceTask, "shouldBeMap");
    assertThat(inputParameter.getCamundaName()).isEqualTo("shouldBeMap");
    assertThat(inputParameter.getTextContent()).isNotEmpty();
    assertThat(inputParameter.getUniqueChildElementByNameNs(CAMUNDA_NS, "map")).isNotNull();

    CamundaMap map = inputParameter.getValue();
    assertThat(map.getCamundaEntries()).hasSize(2);
    for (final CamundaEntry entry : map.getCamundaEntries()) {
      if (entry.getCamundaKey().equals("foo")) {
        assertThat(entry.getTextContent()).isEqualTo("bar");
      } else {
        assertThat(entry.getCamundaKey()).isEqualTo("hello");
        assertThat(entry.getTextContent()).isEqualTo("world");
      }
    }

    map = modelInstance.newInstance(CamundaMap.class);
    CamundaEntry entry = modelInstance.newInstance(CamundaEntry.class);
    entry.setCamundaKey("test");
    entry.setTextContent("value");
    map.getCamundaEntries().add(entry);

    inputParameter.setValue(map);
    map = inputParameter.getValue();
    assertThat(map.getCamundaEntries()).hasSize(1);
    entry = map.getCamundaEntries().iterator().next();
    assertThat(entry.getCamundaKey()).isEqualTo("test");
    assertThat(entry.getTextContent()).isEqualTo("value");

    final Collection<CamundaEntry> entries = map.getCamundaEntries();
    entries.add(modelInstance.newInstance(CamundaEntry.class));
    assertThat(entries).hasSize(2);

    inputParameter.removeValue();
    assertThat(inputParameter.<BpmnModelElementInstance>getValue()).isNull();
  }

  @Test
  public void testCamundaScriptInputParameter() {
    final CamundaInputParameter inputParameter =
        findInputParameterByName(serviceTask, "shouldBeScript");
    assertThat(inputParameter.getCamundaName()).isEqualTo("shouldBeScript");
    assertThat(inputParameter.getTextContent()).isNotEmpty();
    assertThat(inputParameter.getUniqueChildElementByNameNs(CAMUNDA_NS, "script")).isNotNull();
    assertThat(inputParameter.getUniqueChildElementByType(CamundaScript.class)).isNotNull();

    CamundaScript script = inputParameter.getValue();
    assertThat(script.getCamundaScriptFormat()).isEqualTo("groovy");
    assertThat(script.getCamundaResource()).isNull();
    assertThat(script.getTextContent()).isEqualTo("1 + 1");

    script = modelInstance.newInstance(CamundaScript.class);
    script.setCamundaScriptFormat("python");
    script.setCamundaResource("script.py");

    inputParameter.setValue(script);

    script = inputParameter.getValue();
    assertThat(script.getCamundaScriptFormat()).isEqualTo("python");
    assertThat(script.getCamundaResource()).isEqualTo("script.py");
    assertThat(script.getTextContent()).isEmpty();

    inputParameter.removeValue();
    assertThat(inputParameter.<BpmnModelElementInstance>getValue()).isNull();
  }

  @Test
  public void testCamundaNestedOutputParameter() {
    final CamundaOutputParameter camundaOutputParameter =
        serviceTask
            .getExtensionElements()
            .getElementsQuery()
            .filterByType(CamundaInputOutput.class)
            .singleResult()
            .getCamundaOutputParameters()
            .iterator()
            .next();

    assertThat(camundaOutputParameter).isNotNull();
    assertThat(camundaOutputParameter.getCamundaName()).isEqualTo("nested");
    final CamundaList list = camundaOutputParameter.getValue();
    assertThat(list).isNotNull();
    assertThat(list.getValues()).hasSize(2);
    final Iterator<BpmnModelElementInstance> iterator = list.getValues().iterator();

    // nested list
    final CamundaList nestedList =
        (CamundaList) iterator.next().getUniqueChildElementByType(CamundaList.class);
    assertThat(nestedList).isNotNull();
    assertThat(nestedList.getValues()).hasSize(2);
    for (final BpmnModelElementInstance value : nestedList.getValues()) {
      assertThat(value.getTextContent()).isEqualTo("list");
    }

    // nested map
    final CamundaMap nestedMap =
        (CamundaMap) iterator.next().getUniqueChildElementByType(CamundaMap.class);
    assertThat(nestedMap).isNotNull();
    assertThat(nestedMap.getCamundaEntries()).hasSize(2);
    final Iterator<CamundaEntry> mapIterator = nestedMap.getCamundaEntries().iterator();

    // nested list in nested map
    final CamundaEntry nestedListEntry = mapIterator.next();
    assertThat(nestedListEntry).isNotNull();
    assertThat(nestedListEntry.getCamundaKey()).isEqualTo("list");
    final CamundaList nestedNestedList = nestedListEntry.getValue();
    for (final BpmnModelElementInstance value : nestedNestedList.getValues()) {
      assertThat(value.getTextContent()).isEqualTo("map");
    }

    // nested map in nested map
    final CamundaEntry nestedMapEntry = mapIterator.next();
    assertThat(nestedMapEntry).isNotNull();
    assertThat(nestedMapEntry.getCamundaKey()).isEqualTo("map");
    final CamundaMap nestedNestedMap = nestedMapEntry.getValue();
    final CamundaEntry entry = nestedNestedMap.getCamundaEntries().iterator().next();
    assertThat(entry.getCamundaKey()).isEqualTo("so");
    assertThat(entry.getTextContent()).isEqualTo("nested");
  }

  protected CamundaInputParameter findInputParameterByName(BaseElement baseElement, String name) {
    final Collection<CamundaInputParameter> camundaInputParameters =
        baseElement
            .getExtensionElements()
            .getElementsQuery()
            .filterByType(CamundaInputOutput.class)
            .singleResult()
            .getCamundaInputParameters();
    for (final CamundaInputParameter camundaInputParameter : camundaInputParameters) {
      if (camundaInputParameter.getCamundaName().equals(name)) {
        return camundaInputParameter;
      }
    }
    throw new BpmnModelException(
        "Unable to find camunda:inputParameter with name '"
            + name
            + "' for element with id '"
            + baseElement.getId()
            + "'");
  }

  @After
  public void validateModel() {
    Bpmn.validateModel(modelInstance);
  }
}
