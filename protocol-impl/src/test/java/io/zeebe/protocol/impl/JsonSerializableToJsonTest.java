/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.protocol.impl;

import static io.zeebe.util.buffer.BufferUtil.wrapArray;
import static io.zeebe.util.buffer.BufferUtil.wrapString;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.zeebe.protocol.impl.record.CopiedRecord;
import io.zeebe.protocol.impl.record.RecordMetadata;
import io.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.zeebe.protocol.impl.record.VersionInfo;
import io.zeebe.protocol.impl.record.value.deployment.DeploymentDistributionRecord;
import io.zeebe.protocol.impl.record.value.deployment.DeploymentRecord;
import io.zeebe.protocol.impl.record.value.error.ErrorRecord;
import io.zeebe.protocol.impl.record.value.incident.IncidentRecord;
import io.zeebe.protocol.impl.record.value.job.JobBatchRecord;
import io.zeebe.protocol.impl.record.value.job.JobRecord;
import io.zeebe.protocol.impl.record.value.message.MessageRecord;
import io.zeebe.protocol.impl.record.value.message.MessageStartEventSubscriptionRecord;
import io.zeebe.protocol.impl.record.value.message.MessageSubscriptionRecord;
import io.zeebe.protocol.impl.record.value.message.WorkflowInstanceSubscriptionRecord;
import io.zeebe.protocol.impl.record.value.timer.TimerRecord;
import io.zeebe.protocol.impl.record.value.variable.VariableDocumentRecord;
import io.zeebe.protocol.impl.record.value.variable.VariableRecord;
import io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceCreationRecord;
import io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceRecord;
import io.zeebe.protocol.record.JsonSerializable;
import io.zeebe.protocol.record.RecordType;
import io.zeebe.protocol.record.RejectionType;
import io.zeebe.protocol.record.ValueType;
import io.zeebe.protocol.record.intent.DeploymentIntent;
import io.zeebe.protocol.record.value.BpmnElementType;
import io.zeebe.protocol.record.value.ErrorType;
import io.zeebe.protocol.record.value.VariableDocumentUpdateSemantic;
import io.zeebe.protocol.record.value.deployment.ResourceType;
import io.zeebe.test.util.JsonUtil;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public final class JsonSerializableToJsonTest {

  private static final String VARIABLES_JSON = "{'foo':'bar'}";
  private static final DirectBuffer VARIABLES_MSGPACK =
      new UnsafeBuffer(MsgPackConverter.convertToMsgPack(VARIABLES_JSON));

  private static final RuntimeException RUNTIME_EXCEPTION = new RuntimeException("test");

  private static final String STACK_TRACE;

  static {
    final StringWriter stringWriter = new StringWriter();
    final PrintWriter pw = new PrintWriter(stringWriter);
    RUNTIME_EXCEPTION.printStackTrace(pw);

    STACK_TRACE = stringWriter.toString();
  }

  @Parameter public String testName;

  @Parameter(1)
  public Supplier<JsonSerializable> actualRecordSupplier;

  @Parameter(2)
  public String expectedJson;

  @Parameters(name = "{index}: {0}")
  public static Object[][] records() {
    return new Object[][] {
      /////////////////////////////////////////////////////////////////////////////////////////////
      //////////////////////////////////////// Record /////////////////////////////////////////////
      /////////////////////////////////////////////////////////////////////////////////////////////
      new Object[] {
        "Record",
        (Supplier<JsonSerializable>)
            () -> {
              final RecordMetadata recordMetadata = new RecordMetadata();

              final DeploymentIntent intent = DeploymentIntent.CREATE;
              final int protocolVersion = 1;
              final VersionInfo brokerVersion = new VersionInfo(1, 2, 3);
              final ValueType valueType = ValueType.DEPLOYMENT;

              final RecordType recordType = RecordType.COMMAND;
              final String rejectionReason = "fails";
              final RejectionType rejectionType = RejectionType.INVALID_ARGUMENT;
              final int requestId = 23;
              final int requestStreamId = 1;

              recordMetadata
                  .intent(intent)
                  .protocolVersion(protocolVersion)
                  .brokerVersion(brokerVersion)
                  .valueType(valueType)
                  .recordType(recordType)
                  .rejectionReason(rejectionReason)
                  .rejectionType(rejectionType)
                  .requestId(requestId)
                  .requestStreamId(requestStreamId);

              final String resourceName = "resource";
              final ResourceType resourceType = ResourceType.BPMN_XML;
              final DirectBuffer resource = wrapString("contents");
              final String bpmnProcessId = "testProcess";
              final long workflowKey = 123;
              final int workflowVersion = 12;
              final DirectBuffer checksum = wrapString("checksum");

              final DeploymentRecord record = new DeploymentRecord();
              record
                  .resources()
                  .add()
                  .setResourceName(wrapString(resourceName))
                  .setResourceType(resourceType)
                  .setResource(resource);
              record
                  .workflows()
                  .add()
                  .setBpmnProcessId(wrapString(bpmnProcessId))
                  .setKey(workflowKey)
                  .setResourceName(wrapString(resourceName))
                  .setVersion(workflowVersion)
                  .setChecksum(checksum)
                  .setResource(resource);

              final int key = 1234;
              final int position = 4321;
              final int sourcePosition = 231;
              final long timestamp = 2191L;

              return new CopiedRecord<>(
                  record, recordMetadata, key, 0, position, sourcePosition, timestamp);
            },
        "{'valueType':'DEPLOYMENT','key':1234,'position':4321,'timestamp':2191,'recordType':'COMMAND','intent':'CREATE','partitionId':0,'rejectionType':'INVALID_ARGUMENT','rejectionReason':'fails','brokerVersion':'1.2.3','sourceRecordPosition':231,'value':{'deployedWorkflows':[{'resource':'Y29udGVudHM=','version':12,'bpmnProcessId':'testProcess','resourceName':'resource','checksum':'Y2hlY2tzdW0=','workflowKey':123}],'resources':[{'resourceName':'resource','resourceType':'BPMN_XML','resource':'Y29udGVudHM='}]}}"
      },
      /////////////////////////////////////////////////////////////////////////////////////////////
      //////////////////////////////////// DeploymentRecord ///////////////////////////////////////
      /////////////////////////////////////////////////////////////////////////////////////////////
      new Object[] {
        "DeploymentRecord",
        (Supplier<UnifiedRecordValue>)
            () -> {
              final String resourceName = "resource";
              final ResourceType resourceType = ResourceType.BPMN_XML;
              final DirectBuffer resource = wrapString("contents");
              final String bpmnProcessId = "testProcess";
              final long workflowKey = 123;
              final int workflowVersion = 12;
              final DirectBuffer checksum = wrapString("checksum");
              final DeploymentRecord record = new DeploymentRecord();
              record
                  .resources()
                  .add()
                  .setResourceName(wrapString(resourceName))
                  .setResourceType(resourceType)
                  .setResource(resource);
              record
                  .workflows()
                  .add()
                  .setBpmnProcessId(wrapString(bpmnProcessId))
                  .setKey(workflowKey)
                  .setResourceName(wrapString(resourceName))
                  .setVersion(workflowVersion)
                  .setChecksum(checksum)
                  .setResource(resource);
              return record;
            },
        "{'resources':[{'resourceType':'BPMN_XML','resourceName':'resource','resource':'Y29udGVudHM='}],'deployedWorkflows':[{'resource':'Y29udGVudHM=','checksum':'Y2hlY2tzdW0=','bpmnProcessId':'testProcess','version':12,'workflowKey':123,'resourceName':'resource'}]}"
      },
      /////////////////////////////////////////////////////////////////////////////////////////////
      ////////////////////////////// DeploymentDistributionRecord /////////////////////////////////
      /////////////////////////////////////////////////////////////////////////////////////////////
      new Object[] {
        "DeploymentDistributionRecord",
        (Supplier<UnifiedRecordValue>)
            () -> {
              final var record = new DeploymentDistributionRecord();
              record.setPartition(2);
              return record;
            },
        "{'partitionId':2}"
      },
      /////////////////////////////////////////////////////////////////////////////////////////////
      //////////////////////////////////// Empty DeploymentRecord /////////////////////////////////
      /////////////////////////////////////////////////////////////////////////////////////////////
      new Object[] {
        "Empty DeploymentRecord",
        (Supplier<UnifiedRecordValue>) DeploymentRecord::new,
        "{'resources':[],'deployedWorkflows':[]}"
      },
      /////////////////////////////////////////////////////////////////////////////////////////////
      ///////////////////////////////////// ErrorRecord ///////////////////////////////////////////
      /////////////////////////////////////////////////////////////////////////////////////////////
      new Object[] {
        "ErrorRecord",
        (Supplier<UnifiedRecordValue>)
            () -> {
              final ErrorRecord record = new ErrorRecord();
              record.initErrorRecord(RUNTIME_EXCEPTION, 123);
              record.setWorkflowInstanceKey(4321);
              return record;
            },
        errorRecordAsJson(4321, STACK_TRACE)
      },
      /////////////////////////////////////////////////////////////////////////////////////////////
      ///////////////////////////////////// Empty ErrorRecord /////////////////////////////////////
      /////////////////////////////////////////////////////////////////////////////////////////////
      new Object[] {
        "Empty ErrorRecord",
        (Supplier<UnifiedRecordValue>)
            () -> {
              final ErrorRecord record = new ErrorRecord();
              record.initErrorRecord(RUNTIME_EXCEPTION, 123);
              return record;
            },
        errorRecordAsJson(-1, STACK_TRACE)
      },
      /////////////////////////////////////////////////////////////////////////////////////////////
      //////////////////////////////////// IncidentRecord /////////////////////////////////////////
      /////////////////////////////////////////////////////////////////////////////////////////////
      new Object[] {
        "IncidentRecord",
        (Supplier<UnifiedRecordValue>)
            () -> {
              final long elementInstanceKey = 34;
              final long workflowKey = 134;
              final long workflowInstanceKey = 10;
              final String elementId = "activity";
              final String bpmnProcessId = "process";
              final String errorMessage = "error";
              final ErrorType errorType = ErrorType.IO_MAPPING_ERROR;
              final long jobKey = 123;

              return new IncidentRecord()
                  .setElementInstanceKey(elementInstanceKey)
                  .setWorkflowKey(workflowKey)
                  .setWorkflowInstanceKey(workflowInstanceKey)
                  .setElementId(wrapString(elementId))
                  .setBpmnProcessId(wrapString(bpmnProcessId))
                  .setErrorMessage(errorMessage)
                  .setErrorType(errorType)
                  .setJobKey(jobKey)
                  .setVariableScopeKey(elementInstanceKey);
            },
        "{'errorType':'IO_MAPPING_ERROR','errorMessage':'error','bpmnProcessId':'process','workflowKey':134,'workflowInstanceKey':10,'elementId':'activity','elementInstanceKey':34,'jobKey':123,'variableScopeKey':34}"
      },
      /////////////////////////////////////////////////////////////////////////////////////////////
      //////////////////////////////////// Empty IncidentRecord ///////////////////////////////////
      /////////////////////////////////////////////////////////////////////////////////////////////
      new Object[] {
        "Empty IncidentRecord",
        (Supplier<UnifiedRecordValue>) IncidentRecord::new,
        "{'errorType':'UNKNOWN','errorMessage':'','bpmnProcessId':'','workflowKey':-1,'workflowInstanceKey':-1,'elementId':'','elementInstanceKey':-1,'jobKey':-1,'variableScopeKey':-1}"
      },

      /////////////////////////////////////////////////////////////////////////////////////////////
      ///////////////////////////////// JobBatchRecord ////////////////////////////////////////////
      /////////////////////////////////////////////////////////////////////////////////////////////
      {
        "JobBatchRecord",
        (Supplier<UnifiedRecordValue>)
            () -> {
              final int amount = 1;
              final long timeout = 2L;
              final String type = "type";
              final String worker = "worker";

              final JobBatchRecord record =
                  new JobBatchRecord()
                      .setMaxJobsToActivate(amount)
                      .setTimeout(timeout)
                      .setType(type)
                      .setWorker(worker)
                      .setTruncated(true);

              record.jobKeys().add().setValue(3L);
              final JobRecord jobRecord = record.jobs().add();

              final String bpmnProcessId = "test-process";
              final int workflowKey = 13;
              final int workflowDefinitionVersion = 12;
              final int workflowInstanceKey = 1234;
              final String activityId = "activity";
              final int activityInstanceKey = 123;

              jobRecord
                  .setWorker(wrapString(worker))
                  .setType(wrapString(type))
                  .setVariables(VARIABLES_MSGPACK)
                  .setRetries(3)
                  .setErrorMessage("failed message")
                  .setErrorCode(wrapString("error"))
                  .setDeadline(1000L)
                  .setBpmnProcessId(wrapString(bpmnProcessId))
                  .setWorkflowKey(workflowKey)
                  .setWorkflowDefinitionVersion(workflowDefinitionVersion)
                  .setWorkflowInstanceKey(workflowInstanceKey)
                  .setElementId(wrapString(activityId))
                  .setElementInstanceKey(activityInstanceKey);

              return record;
            },
        "{'maxJobsToActivate':1,'type':'type','worker':'worker','truncated':true,'jobKeys':[3],'jobs':[{'bpmnProcessId':'test-process','workflowKey':13,'workflowDefinitionVersion':12,'workflowInstanceKey':1234,'elementId':'activity','elementInstanceKey':123,'type':'type','worker':'worker','variables':{'foo':'bar'},'retries':3,'errorMessage':'failed message','errorCode':'error','customHeaders':{},'deadline':1000}],'timeout':2}"
      },
      /////////////////////////////////////////////////////////////////////////////////////////////
      ///////////////////////////////// Empty JobBatchRecord //////////////////////////////////////
      /////////////////////////////////////////////////////////////////////////////////////////////
      {
        "Empty JobBatchRecord",
        (Supplier<UnifiedRecordValue>)
            () -> {
              final String type = "type";
              return new JobBatchRecord().setType(type);
            },
        "{'worker':'','type':'type','maxJobsToActivate':-1,'truncated':false,'jobKeys':[],'jobs':[],'timeout':-1}"
      },
      /////////////////////////////////////////////////////////////////////////////////////////////
      ///////////////////////////////////// JobRecord /////////////////////////////////////////////
      /////////////////////////////////////////////////////////////////////////////////////////////
      {
        "JobRecord",
        (Supplier<UnifiedRecordValue>)
            () -> {
              final String worker = "myWorker";
              final String type = "myType";
              final int retries = 12;
              final int deadline = 13;

              final String bpmnProcessId = "test-process";
              final int workflowKey = 13;
              final int workflowDefinitionVersion = 12;
              final int workflowInstanceKey = 1234;
              final String elementId = "activity";
              final int activityInstanceKey = 123;

              final Map<String, String> customHeaders =
                  Collections.singletonMap("workerVersion", "42");

              final JobRecord record =
                  new JobRecord()
                      .setWorker(wrapString(worker))
                      .setType(wrapString(type))
                      .setVariables(VARIABLES_MSGPACK)
                      .setRetries(retries)
                      .setDeadline(deadline)
                      .setErrorMessage("failed message")
                      .setErrorCode(wrapString("error"))
                      .setBpmnProcessId(wrapString(bpmnProcessId))
                      .setWorkflowKey(workflowKey)
                      .setWorkflowDefinitionVersion(workflowDefinitionVersion)
                      .setWorkflowInstanceKey(workflowInstanceKey)
                      .setElementId(wrapString(elementId))
                      .setElementInstanceKey(activityInstanceKey);

              record.setCustomHeaders(wrapArray(MsgPackConverter.convertToMsgPack(customHeaders)));
              return record;
            },
        "{'bpmnProcessId':'test-process','workflowKey':13,'workflowDefinitionVersion':12,'workflowInstanceKey':1234,'elementId':'activity','elementInstanceKey':123,'worker':'myWorker','type':'myType','variables':{'foo':'bar'},'retries':12,'errorMessage':'failed message','errorCode':'error','customHeaders':{'workerVersion':'42'},'deadline':13}"
      },

      /////////////////////////////////////////////////////////////////////////////////////////////
      ///////////////////////////////////// Empty JobRecord ///////////////////////////////////////
      /////////////////////////////////////////////////////////////////////////////////////////////
      {
        "Empty JobRecord",
        (Supplier<UnifiedRecordValue>) JobRecord::new,
        "{'type':'','workflowDefinitionVersion':-1,'elementId':'','bpmnProcessId':'','workflowKey':-1,'workflowInstanceKey':-1,'elementInstanceKey':-1,'variables':{},'worker':'','retries':-1,'errorMessage':'','errorCode':'','customHeaders':{},'deadline':-1}"
      },
      /////////////////////////////////////////////////////////////////////////////////////////////
      ///////////////////////////////// MessageRecord /////////////////////////////////////////////
      /////////////////////////////////////////////////////////////////////////////////////////////
      {
        "MessageRecord",
        (Supplier<UnifiedRecordValue>)
            () -> {
              final String correlationKey = "test-key";
              final String messageName = "test-message";
              final long timeToLive = 12;
              final String messageId = "test-id";

              return new MessageRecord()
                  .setCorrelationKey(wrapString(correlationKey))
                  .setName(wrapString(messageName))
                  .setVariables(VARIABLES_MSGPACK)
                  .setTimeToLive(timeToLive)
                  .setMessageId(wrapString(messageId));
            },
        "{'timeToLive':12,'correlationKey':'test-key','variables':{'foo':'bar'},'messageId':'test-id','name':'test-message'}"
      },
      /////////////////////////////////////////////////////////////////////////////////////////////
      ///////////////////////////////// Empty MessageRecord ///////////////////////////////////////
      /////////////////////////////////////////////////////////////////////////////////////////////
      {
        "Empty MessageRecord",
        (Supplier<UnifiedRecordValue>)
            () -> {
              final String correlationKey = "test-key";
              final String messageName = "test-message";
              final long timeToLive = 12;

              return new MessageRecord()
                  .setTimeToLive(timeToLive)
                  .setCorrelationKey(correlationKey)
                  .setName(messageName);
            },
        "{'timeToLive':12,'correlationKey':'test-key','variables':{},'messageId':'','name':'test-message'}"
      },

      /////////////////////////////////////////////////////////////////////////////////////////////
      ///////////////////////////////// MessageStartEventSubscriptionRecord ///////////////////////
      /////////////////////////////////////////////////////////////////////////////////////////////
      {
        "MessageStartEventSubscriptionRecord",
        (Supplier<UnifiedRecordValue>)
            () -> {
              final String messageName = "name";
              final String startEventId = "startEvent";
              final int workflowKey = 22334;
              final String bpmnProcessId = "workflow";

              return new MessageStartEventSubscriptionRecord()
                  .setMessageName(wrapString(messageName))
                  .setStartEventId(wrapString(startEventId))
                  .setWorkflowKey(workflowKey)
                  .setBpmnProcessId(wrapString(bpmnProcessId));
            },
        "{'workflowKey':22334,'messageName':'name','startEventId':'startEvent','bpmnProcessId':'workflow'}"
      },

      /////////////////////////////////////////////////////////////////////////////////////////////
      ///////////////////////////////// Empty MessageStartEventSubscriptionRecord /////////////////
      /////////////////////////////////////////////////////////////////////////////////////////////
      {
        "Empty MessageStartEventSubscriptionRecord",
        (Supplier<UnifiedRecordValue>)
            () -> {
              final int workflowKey = 22334;

              return new MessageStartEventSubscriptionRecord().setWorkflowKey(workflowKey);
            },
        "{'workflowKey':22334,'messageName':'','startEventId':'','bpmnProcessId':''}"
      },

      /////////////////////////////////////////////////////////////////////////////////////////////
      ///////////////////////////////// MessageSubscriptionRecord /////////////////////////////////
      /////////////////////////////////////////////////////////////////////////////////////////////
      {
        "MessageSubscriptionRecord",
        (Supplier<UnifiedRecordValue>)
            () -> {
              final long elementInstanceKey = 1L;
              final String bpmnProcessId = "workflow";
              final String messageName = "name";
              final long workflowInstanceKey = 2L;
              final String correlationKey = "key";
              final long messageKey = 3L;

              return new MessageSubscriptionRecord()
                  .setElementInstanceKey(elementInstanceKey)
                  .setBpmnProcessId(wrapString(bpmnProcessId))
                  .setMessageKey(messageKey)
                  .setMessageName(wrapString(messageName))
                  .setWorkflowInstanceKey(workflowInstanceKey)
                  .setCorrelationKey(wrapString(correlationKey));
            },
        "{'workflowInstanceKey':2,'elementInstanceKey':1,'messageName':'name','correlationKey':'key','bpmnProcessId':'workflow','messageKey':3}"
      },
      /////////////////////////////////////////////////////////////////////////////////////////////
      ///////////////////////////////// Empty MessageSubscriptionRecord
      // /////////////////////////////////
      /////////////////////////////////////////////////////////////////////////////////////////////
      {
        "Empty MessageSubscriptionRecord",
        (Supplier<UnifiedRecordValue>)
            () -> {
              final long elementInstanceKey = 13L;
              final long workflowInstanceKey = 1L;

              return new MessageSubscriptionRecord()
                  .setWorkflowInstanceKey(workflowInstanceKey)
                  .setElementInstanceKey(elementInstanceKey);
            },
        "{'workflowInstanceKey':1,'elementInstanceKey':13,'messageName':'','correlationKey':'','bpmnProcessId':'','messageKey':-1}"
      },

      /////////////////////////////////////////////////////////////////////////////////////////////
      //////////////////////////// WorkflowInstanceSubscriptionRecord /////////////////////////////
      /////////////////////////////////////////////////////////////////////////////////////////////
      {
        "WorkflowInstanceSubscriptionRecord",
        (Supplier<UnifiedRecordValue>)
            () -> {
              final long elementInstanceKey = 123;
              final String bpmnProcessId = "workflow";
              final String messageName = "test-message";
              final int subscriptionPartitionId = 2;
              final int messageKey = 3;
              final long workflowInstanceKey = 1345;
              final String correlationKey = "key";

              return new WorkflowInstanceSubscriptionRecord()
                  .setElementInstanceKey(elementInstanceKey)
                  .setBpmnProcessId(wrapString(bpmnProcessId))
                  .setMessageName(wrapString(messageName))
                  .setMessageKey(messageKey)
                  .setSubscriptionPartitionId(subscriptionPartitionId)
                  .setWorkflowInstanceKey(workflowInstanceKey)
                  .setVariables(VARIABLES_MSGPACK)
                  .setCorrelationKey(wrapString(correlationKey));
            },
        "{'elementInstanceKey':123,'messageName':'test-message','workflowInstanceKey':1345,'variables':{'foo':'bar'},'bpmnProcessId':'workflow','messageKey':3,'correlationKey':'key'}"
      },

      /////////////////////////////////////////////////////////////////////////////////////////////
      //////////////////////////// Empty WorkflowInstanceSubscriptionRecord ///////////////////////
      /////////////////////////////////////////////////////////////////////////////////////////////
      {
        "Empty WorkflowInstanceSubscriptionRecord",
        (Supplier<UnifiedRecordValue>)
            () -> {
              final long elementInstanceKey = 123;
              final long workflowInstanceKey = 1345;

              return new WorkflowInstanceSubscriptionRecord()
                  .setWorkflowInstanceKey(workflowInstanceKey)
                  .setElementInstanceKey(elementInstanceKey);
            },
        "{'elementInstanceKey':123,'messageName':'','workflowInstanceKey':1345,'variables':{},'bpmnProcessId':'','messageKey':-1,'correlationKey':''}"
      },

      /////////////////////////////////////////////////////////////////////////////////////////////
      /////////////////////////////////// TimerRecord /////////////////////////////////////////////
      /////////////////////////////////////////////////////////////////////////////////////////////
      {
        "TimerRecord",
        (Supplier<UnifiedRecordValue>)
            () -> {
              final int workflowKey = 13;
              final int workflowInstanceKey = 1234;
              final int dueDate = 1234;
              final int elementInstanceKey = 567;
              final String handlerNodeId = "node1";
              final int repetitions = 3;

              return new TimerRecord()
                  .setDueDate(dueDate)
                  .setElementInstanceKey(elementInstanceKey)
                  .setTargetElementId(wrapString(handlerNodeId))
                  .setRepetitions(repetitions)
                  .setWorkflowInstanceKey(workflowInstanceKey)
                  .setWorkflowKey(workflowKey);
            },
        "{'elementInstanceKey':567,'workflowInstanceKey':1234,'dueDate':1234,'targetElementId':'node1','repetitions':3,'workflowKey':13}"
      },

      /////////////////////////////////////////////////////////////////////////////////////////////
      ///////////////////////////////// VariableRecord ////////////////////////////////////////////
      /////////////////////////////////////////////////////////////////////////////////////////////
      {
        "VariableRecord",
        (Supplier<UnifiedRecordValue>)
            () -> {
              final String name = "x";
              final String value = "1";
              final long scopeKey = 3;
              final long workflowInstanceKey = 2;
              final long workflowKey = 4;

              return new VariableRecord()
                  .setName(wrapString(name))
                  .setValue(new UnsafeBuffer(MsgPackConverter.convertToMsgPack(value)))
                  .setScopeKey(scopeKey)
                  .setWorkflowInstanceKey(workflowInstanceKey)
                  .setWorkflowKey(workflowKey);
            },
        "{'scopeKey':3,'workflowInstanceKey':2,'workflowKey':4,'name':'x','value':'1'}"
      },

      /////////////////////////////////////////////////////////////////////////////////////////////
      ///////////////////////////////// VariableDocumentRecord ////////////////////////////////////
      /////////////////////////////////////////////////////////////////////////////////////////////
      {
        "VariableDocumentRecord",
        (Supplier<UnifiedRecordValue>)
            () -> {
              final String value = "{'foo':1}";
              final long scopeKey = 3;

              return new VariableDocumentRecord()
                  .setUpdateSemantics(VariableDocumentUpdateSemantic.LOCAL)
                  .setVariables(new UnsafeBuffer(MsgPackConverter.convertToMsgPack(value)))
                  .setScopeKey(scopeKey);
            },
        "{'updateSemantics':'LOCAL','variables':{'foo':1},'scopeKey':3}"
      },

      /////////////////////////////////////////////////////////////////////////////////////////////
      ///////////////////////////////// Empty VariableDocumentRecord //////////////////////////////
      /////////////////////////////////////////////////////////////////////////////////////////////
      {
        "Empty VariableDocumentRecord",
        (Supplier<UnifiedRecordValue>)
            () -> {
              final long scopeKey = 3;

              return new VariableDocumentRecord().setScopeKey(scopeKey);
            },
        "{'updateSemantics':'PROPAGATE','variables':{},'scopeKey':3}"
      },

      /////////////////////////////////////////////////////////////////////////////////////////////
      ///////////////////////////////// WorkflowInstanceCreationRecord ////////////////////////////
      /////////////////////////////////////////////////////////////////////////////////////////////
      {
        "WorkflowInstanceCreationRecord",
        (Supplier<UnifiedRecordValue>)
            () -> {
              final String processId = "process";
              final long key = 1L;
              final int version = 1;
              final long instanceKey = 2L;

              return new WorkflowInstanceCreationRecord()
                  .setBpmnProcessId(processId)
                  .setWorkflowKey(key)
                  .setVersion(version)
                  .setVariables(
                      new UnsafeBuffer(
                          MsgPackConverter.convertToMsgPack("{'foo':'bar','baz':'boz'}")))
                  .setWorkflowInstanceKey(instanceKey);
            },
        "{'variables':{'foo':'bar','baz':'boz'},'bpmnProcessId':'process','workflowKey':1,'version':1,'workflowInstanceKey':2}"
      },

      /////////////////////////////////////////////////////////////////////////////////////////////
      ///////////////////////////////// Empty WorkflowInstanceCreationRecord //////////////////////
      /////////////////////////////////////////////////////////////////////////////////////////////
      {
        "Empty WorkflowInstanceCreationRecord",
        (Supplier<UnifiedRecordValue>) WorkflowInstanceCreationRecord::new,
        "{'variables':{},'bpmnProcessId':'','workflowKey':-1,'version':-1,'workflowInstanceKey':-1}"
      },

      /////////////////////////////////////////////////////////////////////////////////////////////
      ///////////////////////////////// WorkflowInstanceRecord ////////////////////////////////////
      /////////////////////////////////////////////////////////////////////////////////////////////
      {
        "WorkflowInstanceRecord",
        (Supplier<UnifiedRecordValue>)
            () -> {
              final String bpmnProcessId = "test-process";
              final int workflowKey = 13;
              final int version = 12;
              final int workflowInstanceKey = 1234;
              final String elementId = "activity";
              final int flowScopeKey = 123;
              final BpmnElementType bpmnElementType = BpmnElementType.SERVICE_TASK;

              return new WorkflowInstanceRecord()
                  .setElementId(elementId)
                  .setBpmnElementType(bpmnElementType)
                  .setBpmnProcessId(wrapString(bpmnProcessId))
                  .setVersion(version)
                  .setWorkflowKey(workflowKey)
                  .setWorkflowInstanceKey(workflowInstanceKey)
                  .setFlowScopeKey(flowScopeKey)
                  .setParentWorkflowInstanceKey(11)
                  .setParentElementInstanceKey(22);
            },
        "{'bpmnProcessId':'test-process','version':12,'workflowKey':13,'workflowInstanceKey':1234,'elementId':'activity','flowScopeKey':123,'bpmnElementType':'SERVICE_TASK','parentWorkflowInstanceKey':11,'parentElementInstanceKey':22}"
      },

      /////////////////////////////////////////////////////////////////////////////////////////////
      ///////////////////////////////// Empty WorkflowInstanceRecord //////////////////////////////
      /////////////////////////////////////////////////////////////////////////////////////////////
      {
        "Empty WorkflowInstanceRecord",
        (Supplier<UnifiedRecordValue>) WorkflowInstanceRecord::new,
        "{'bpmnProcessId':'','version':-1,'workflowKey':-1,'workflowInstanceKey':-1,'elementId':'','flowScopeKey':-1,'bpmnElementType':'UNSPECIFIED','parentWorkflowInstanceKey':-1,'parentElementInstanceKey':-1}"
      },
    };
  }

  @Test
  public void shouldConvertJsonSerializableToJson() {
    // given

    // when
    final String json = actualRecordSupplier.get().toJson();

    // then
    JsonUtil.assertEquality(json, expectedJson);
  }

  private static String errorRecordAsJson(final long workflowInstanceKey, final String stacktrace) {
    final Map<String, Object> params = new HashMap<>();
    params.put("exceptionMessage", "test");
    params.put("workflowInstanceKey", workflowInstanceKey);
    params.put("errorEventPosition", 123);
    params.put("stacktrace", stacktrace);

    try {
      return new ObjectMapper().writeValueAsString(params);
    } catch (final JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }
}
