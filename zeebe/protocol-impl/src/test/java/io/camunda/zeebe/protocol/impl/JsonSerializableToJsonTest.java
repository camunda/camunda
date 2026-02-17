/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl;

import static io.camunda.zeebe.util.buffer.BufferUtil.wrapArray;
import static io.camunda.zeebe.util.buffer.BufferUtil.wrapString;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.zeebe.protocol.impl.encoding.AgentInfo;
import io.camunda.zeebe.protocol.impl.encoding.AuthInfo;
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.protocol.impl.record.CopiedRecord;
import io.camunda.zeebe.protocol.impl.record.RecordMetadata;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.impl.record.VersionInfo;
import io.camunda.zeebe.protocol.impl.record.value.adhocsubprocess.AdHocSubProcessInstructionRecord;
import io.camunda.zeebe.protocol.impl.record.value.authorization.AuthorizationRecord;
import io.camunda.zeebe.protocol.impl.record.value.authorization.IdentitySetupRecord;
import io.camunda.zeebe.protocol.impl.record.value.authorization.MappingRuleRecord;
import io.camunda.zeebe.protocol.impl.record.value.authorization.RoleRecord;
import io.camunda.zeebe.protocol.impl.record.value.batchoperation.BatchOperationChunkRecord;
import io.camunda.zeebe.protocol.impl.record.value.batchoperation.BatchOperationCreationRecord;
import io.camunda.zeebe.protocol.impl.record.value.batchoperation.BatchOperationExecutionRecord;
import io.camunda.zeebe.protocol.impl.record.value.batchoperation.BatchOperationItem;
import io.camunda.zeebe.protocol.impl.record.value.batchoperation.BatchOperationLifecycleManagementRecord;
import io.camunda.zeebe.protocol.impl.record.value.batchoperation.BatchOperationProcessInstanceMigrationPlan;
import io.camunda.zeebe.protocol.impl.record.value.batchoperation.BatchOperationProcessInstanceModificationPlan;
import io.camunda.zeebe.protocol.impl.record.value.clock.ClockRecord;
import io.camunda.zeebe.protocol.impl.record.value.compensation.CompensationSubscriptionRecord;
import io.camunda.zeebe.protocol.impl.record.value.conditional.ConditionalEvaluationRecord;
import io.camunda.zeebe.protocol.impl.record.value.conditional.ConditionalSubscriptionRecord;
import io.camunda.zeebe.protocol.impl.record.value.decision.DecisionEvaluationRecord;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DecisionRecord;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DecisionRequirementsRecord;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DeploymentDistributionRecord;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DeploymentRecord;
import io.camunda.zeebe.protocol.impl.record.value.deployment.ProcessRecord;
import io.camunda.zeebe.protocol.impl.record.value.distribution.CommandDistributionRecord;
import io.camunda.zeebe.protocol.impl.record.value.error.ErrorRecord;
import io.camunda.zeebe.protocol.impl.record.value.escalation.EscalationRecord;
import io.camunda.zeebe.protocol.impl.record.value.expression.ExpressionRecord;
import io.camunda.zeebe.protocol.impl.record.value.globallistener.GlobalListenerBatchRecord;
import io.camunda.zeebe.protocol.impl.record.value.globallistener.GlobalListenerRecord;
import io.camunda.zeebe.protocol.impl.record.value.group.GroupRecord;
import io.camunda.zeebe.protocol.impl.record.value.history.HistoryDeletionRecord;
import io.camunda.zeebe.protocol.impl.record.value.incident.IncidentRecord;
import io.camunda.zeebe.protocol.impl.record.value.job.JobBatchRecord;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.impl.record.value.job.JobResult;
import io.camunda.zeebe.protocol.impl.record.value.job.JobResultActivateElement;
import io.camunda.zeebe.protocol.impl.record.value.job.JobResultCorrections;
import io.camunda.zeebe.protocol.impl.record.value.jobmetrics.JobMetrics;
import io.camunda.zeebe.protocol.impl.record.value.jobmetrics.JobMetricsBatchRecord;
import io.camunda.zeebe.protocol.impl.record.value.jobmetrics.StatusMetrics;
import io.camunda.zeebe.protocol.impl.record.value.management.CheckpointRecord;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageBatchRecord;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageCorrelationRecord;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageRecord;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageStartEventSubscriptionRecord;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageSubscriptionRecord;
import io.camunda.zeebe.protocol.impl.record.value.message.ProcessMessageSubscriptionRecord;
import io.camunda.zeebe.protocol.impl.record.value.metrics.UsageMetricRecord;
import io.camunda.zeebe.protocol.impl.record.value.multiinstance.MultiInstanceRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceBatchRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceCreationRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceCreationStartInstruction;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceMigrationMappingInstruction;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceMigrationRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceModificationActivateInstruction;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceModificationMoveInstruction;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceModificationRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceModificationTerminateInstruction;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceModificationVariableInstruction;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.RuntimeInstructionRecord;
import io.camunda.zeebe.protocol.impl.record.value.resource.ResourceDeletionRecord;
import io.camunda.zeebe.protocol.impl.record.value.scaling.ScaleRecord;
import io.camunda.zeebe.protocol.impl.record.value.signal.SignalRecord;
import io.camunda.zeebe.protocol.impl.record.value.signal.SignalSubscriptionRecord;
import io.camunda.zeebe.protocol.impl.record.value.tenant.TenantRecord;
import io.camunda.zeebe.protocol.impl.record.value.timer.TimerRecord;
import io.camunda.zeebe.protocol.impl.record.value.user.UserRecord;
import io.camunda.zeebe.protocol.impl.record.value.usertask.UserTaskRecord;
import io.camunda.zeebe.protocol.impl.record.value.variable.VariableDocumentRecord;
import io.camunda.zeebe.protocol.impl.record.value.variable.VariableRecord;
import io.camunda.zeebe.protocol.record.JsonSerializable;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.DeploymentIntent;
import io.camunda.zeebe.protocol.record.intent.HistoryDeletionIntent;
import io.camunda.zeebe.protocol.record.value.AuthorizationOwnerType;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceMatcher;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.BatchOperationType;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.BpmnEventType;
import io.camunda.zeebe.protocol.record.value.EntityType;
import io.camunda.zeebe.protocol.record.value.ErrorType;
import io.camunda.zeebe.protocol.record.value.GlobalListenerSource;
import io.camunda.zeebe.protocol.record.value.HistoryDeletionType;
import io.camunda.zeebe.protocol.record.value.JobResultType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.protocol.record.value.ResourceType;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.protocol.record.value.UsageMetricRecordValue.EventType;
import io.camunda.zeebe.protocol.record.value.UsageMetricRecordValue.IntervalType;
import io.camunda.zeebe.protocol.record.value.VariableDocumentUpdateSemantic;
import io.camunda.zeebe.protocol.record.value.management.CheckpointType;
import io.camunda.zeebe.test.util.JsonUtil;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

final class JsonSerializableToJsonTest {

  private static final String VARIABLES_JSON = "{'foo':'bar'}";
  private static final DirectBuffer VARIABLES_MSGPACK =
      new UnsafeBuffer(MsgPackConverter.convertToMsgPack(VARIABLES_JSON));

  private static final String USAGE_METRICS_JSON = "{'tenant1': 5}";
  private static final DirectBuffer USAGE_METRICS_MSGPACK =
      new UnsafeBuffer(MsgPackConverter.convertToMsgPack(USAGE_METRICS_JSON));

  private static final RuntimeException RUNTIME_EXCEPTION = new RuntimeException("test");
  private static final String STACK_TRACE;

  static {
    final StringWriter stringWriter = new StringWriter();
    final PrintWriter pw = new PrintWriter(stringWriter);
    RUNTIME_EXCEPTION.printStackTrace(pw);

    STACK_TRACE = stringWriter.toString();
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("records")
  void shouldConvertJsonSerializableToJson(
      @SuppressWarnings("unused") final String testDisplayName,
      final Supplier<JsonSerializable> actualRecordSupplier,
      final String expectedJson) {
    // given

    // when
    final String json = actualRecordSupplier.get().toJson();

    // then
    JsonUtil.assertEquality(json, expectedJson);
  }

  private static Object[][] records() {
    return new Object[][] {
      /////////////////////////////////////////////////////////////////////////////////////////////
      //////////////////////////////////////// Record
      /////////////////////////////////////////////////////////////////////////////////////////////
      // /////////////////////////////////////////////
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

              final AuthInfo authInfo = new AuthInfo().setClaims(Map.of("foo", "bar"));
              final AgentInfo agentInfo = new AgentInfo().setElementId("agent-element");

              recordMetadata
                  .intent(intent)
                  .protocolVersion(protocolVersion)
                  .brokerVersion(brokerVersion)
                  .recordVersion(10)
                  .valueType(valueType)
                  .recordType(recordType)
                  .rejectionReason(rejectionReason)
                  .rejectionType(rejectionType)
                  .requestId(requestId)
                  .requestStreamId(requestStreamId)
                  .authorization(authInfo)
                  .agent(agentInfo)
                  .operationReference(1234)
                  .batchOperationReference(5678);

              final String resourceName = "resource";
              final DirectBuffer resource = wrapString("contents");
              final String bpmnProcessId = "testProcess";
              final long processDefinitionKey = 123;
              final int processVersion = 12;
              final DirectBuffer checksum = wrapString("checksum");

              final DeploymentRecord record = new DeploymentRecord();
              record
                  .resources()
                  .add()
                  .setResourceName(wrapString(resourceName))
                  .setResource(resource);
              record
                  .processesMetadata()
                  .add()
                  .setBpmnProcessId(wrapString(bpmnProcessId))
                  .setKey(processDefinitionKey)
                  .setResourceName(wrapString(resourceName))
                  .setVersion(processVersion)
                  .setChecksum(checksum);

              final int key = 1234;
              final int position = 4321;
              final int sourcePosition = 231;
              final long timestamp = 2191L;

              return new CopiedRecord<>(
                  record, recordMetadata, key, 0, position, sourcePosition, timestamp);
            },
        """
                {
                  "valueType": "DEPLOYMENT",
                  "key": 1234,
                  "position": 4321,
                  "timestamp": 2191,
                  "recordType": "COMMAND",
                  "intent": "CREATE",
                  "partitionId": 0,
                  "rejectionType": "INVALID_ARGUMENT",
                  "rejectionReason": "fails",
                  "brokerVersion": "1.2.3",
                  "authorizations": {
                    "foo" : "bar"
                  },
                  "agent": {
                    "elementId": "agent-element"
                  },
                  "recordVersion": 10,
                  "operationReference": 1234,
                  "batchOperationReference": 5678,
                  "sourceRecordPosition": 231,
                  "value": {
                    "processesMetadata": [
                      {
                        "version": 12,
                        "bpmnProcessId": "testProcess",
                        "resourceName": "resource",
                        "checksum": "Y2hlY2tzdW0=",
                        "processDefinitionKey": 123,
                        "duplicate": false,
                        "tenantId": "<default>",
                        "deploymentKey": -1,
                        "versionTag": ""
                      }
                    ],
                    "resources": [
                      {
                        "resourceName": "resource",
                        "resource": "Y29udGVudHM="
                      }
                    ],
                    "decisionsMetadata": [],
                    "resourceMetadata":[],
                    "decisionRequirementsMetadata": [],
                    "formMetadata": [],
                    "tenantId": "<default>",
                    "deploymentKey": -1
                  }
                }
                """
      },
      /////////////////////////////////////////////////////////////////////////////////////////////
      /////////////////////////////////////// Empty Record
      /////////////////////////////////////////////////////////////////////////////////////////////
      // ////////////////////////////////////////
      /////////////////////////////////////////////////////////////////////////////////////////////
      new Object[] {
        "Empty Record",
        (Supplier<JsonSerializable>)
            () -> {
              final var record = new DeploymentRecord();
              final var metadata = new RecordMetadata().brokerVersion(new VersionInfo(0, 0, 0));
              final int key = -1;
              final int partitionId = -1;
              final int position = -1;
              final int sourcePosition = -1;
              final int timestamp = -1;
              return new CopiedRecord<>(
                  record, metadata, key, partitionId, position, sourcePosition, timestamp);
            },
        """
                {
                  "key": -1,
                  "position": -1,
                  "sourceRecordPosition": -1,
                  "partitionId": -1,
                  "timestamp": -1,
                  "recordType": "NULL_VAL",
                  "valueType": "NULL_VAL",
                  "intent": null,
                  "rejectionType": "NULL_VAL",
                  "rejectionReason": "",
                  "brokerVersion": "0.0.0",
                  "authorizations": {},
                  "agent": null,
                  "recordVersion": 1,
                  "operationReference": -1,
                  "batchOperationReference": -1,
                  "value": {
                      "resources": [],
                      "decisionRequirementsMetadata": [],
                      "processesMetadata": [],
                      "resourceMetadata":[],
                      "decisionsMetadata": [],
                      "formMetadata": [],
                      "tenantId": "<default>",
                      "deploymentKey":-1
                  }
                }
                """
      },
      /////////////////////////////////////////////////////////////////////////////////////////////
      //////////////////////////////////// DeploymentRecord
      /////////////////////////////////////////////////////////////////////////////////////////////
      // ///////////////////////////////////////
      /////////////////////////////////////////////////////////////////////////////////////////////
      new Object[] {
        "DeploymentRecord",
        (Supplier<UnifiedRecordValue>)
            () -> {
              final String resourceName = "resource";
              final DirectBuffer resource = wrapString("contents");
              final String bpmnProcessId = "testProcess";
              final long processDefinitionKey = 123;
              final int processVersion = 12;
              final DirectBuffer checksum = wrapString("checksum");
              final long deploymentKey = 1234;
              final String versionTag = "v1.0";
              final DeploymentRecord record = new DeploymentRecord();
              record
                  .setDeploymentKey(deploymentKey)
                  .resources()
                  .add()
                  .setResourceName(wrapString(resourceName))
                  .setResource(resource);
              record
                  .processesMetadata()
                  .add()
                  .setBpmnProcessId(wrapString(bpmnProcessId))
                  .setKey(processDefinitionKey)
                  .setResourceName(wrapString(resourceName))
                  .setVersion(processVersion)
                  .setChecksum(checksum)
                  .setDuplicate(true)
                  .setDeploymentKey(deploymentKey)
                  .setVersionTag(versionTag);
              record
                  .decisionRequirementsMetadata()
                  .add()
                  .setDecisionRequirementsId("drg-id")
                  .setDecisionRequirementsName("drg-name")
                  .setDecisionRequirementsVersion(1)
                  .setDecisionRequirementsKey(1L)
                  .setNamespace("namespace")
                  .setResourceName("resource-name")
                  .setChecksum(checksum)
                  .setDuplicate(true)
                  .setDeploymentKey(deploymentKey);
              record
                  .decisionsMetadata()
                  .add()
                  .setDecisionId("decision-id")
                  .setDecisionName("decision-name")
                  .setVersion(1)
                  .setDecisionKey(2L)
                  .setDecisionRequirementsKey(1L)
                  .setDecisionRequirementsId("drg-id")
                  .setDeploymentKey(deploymentKey)
                  .setVersionTag(versionTag)
                  .setDuplicate(true);
              record
                  .formMetadata()
                  .add()
                  .setFormId("form-id")
                  .setVersion(1)
                  .setFormKey(1L)
                  .setResourceName("form1.form")
                  .setChecksum(checksum)
                  .setDeploymentKey(deploymentKey)
                  .setDuplicate(true)
                  .setVersionTag(versionTag);
              record.setTenantId("tenant-23").setReconstructionKey(123);
              return record;
            },
        """
                {
                  "resources": [
                    {
                      "resourceName": "resource",
                      "resource": "Y29udGVudHM="
                    }
                  ],
                  "processesMetadata": [
                    {
                      "checksum": "Y2hlY2tzdW0=",
                      "bpmnProcessId": "testProcess",
                      "version": 12,
                      "processDefinitionKey": 123,
                      "resourceName": "resource",
                      "duplicate": true,
                      "tenantId": "<default>",
                      "deploymentKey": 1234,
                      "versionTag": "v1.0"
                    }
                  ],
                  "decisionsMetadata": [
                    {
                      "version": 1,
                      "decisionRequirementsId": "drg-id",
                      "decisionRequirementsKey": 1,
                      "decisionId": "decision-id",
                      "decisionName": "decision-name",
                      "decisionKey": 2,
                      "duplicate": true,
                      "tenantId": "<default>",
                      "deploymentKey": 1234,
                      "versionTag": "v1.0"
                    }
                  ],
                  "decisionRequirementsMetadata": [
                    {
                      "decisionRequirementsId": "drg-id",
                      "decisionRequirementsName": "drg-name",
                      "decisionRequirementsVersion": 1,
                      "decisionRequirementsKey": 1,
                      "namespace": "namespace",
                      "resourceName": "resource-name",
                      "checksum": "Y2hlY2tzdW0=",
                      "duplicate": true,
                      "tenantId": "<default>",
                      "deploymentKey": 1234
                    }
                  ],
                  "formMetadata": [
                  {
                      "checksum": "Y2hlY2tzdW0=",
                      "formId": "form-id",
                      "version": 1,
                      "formKey": 1,
                      "resourceName": "form1.form",
                      "duplicate": true,
                      "tenantId": "<default>",
                      "deploymentKey": 1234,
                      "versionTag": "v1.0"
                    }
                  ],
                  "resourceMetadata":[],
                  "tenantId": "tenant-23",
                  "deploymentKey": 1234
                }
                """
      },
      /////////////////////////////////////////////////////////////////////////////////////////////
      ////////////////////////////// DeploymentDistributionRecord
      /////////////////////////////////////////////////////////////////////////////////////////////
      // /////////////////////////////////
      /////////////////////////////////////////////////////////////////////////////////////////////
      new Object[] {
        "DeploymentDistributionRecord",
        (Supplier<UnifiedRecordValue>)
            () -> {
              final var record = new DeploymentDistributionRecord();
              record.setPartition(2);
              return record;
            },
        """
                {
                  "partitionId": 2
                }
                """
      },
      /////////////////////////////////////////////////////////////////////////////////////////////
      //////////////////////////////////// Empty DeploymentRecord
      /////////////////////////////////////////////////////////////////////////////////////////////
      // /////////////////////////////////
      /////////////////////////////////////////////////////////////////////////////////////////////
      new Object[] {
        "Empty DeploymentRecord",
        (Supplier<UnifiedRecordValue>) DeploymentRecord::new,
        """
                {
                  "resources": [],
                  "processesMetadata": [],
                  "decisionsMetadata": [],
                  "decisionRequirementsMetadata": [],
                  "formMetadata": [],
                  "resourceMetadata":[],
                  "tenantId": "<default>",
                  "deploymentKey": -1
                }
                """
      },
      /////////////////////////////////////////////////////////////////////////////////////////////
      //////////////////////////////////// ProcessRecord
      /////////////////////////////////////////////////////////////////////////////////////////////
      // ///////////////////////////////////////
      /////////////////////////////////////////////////////////////////////////////////////////////
      new Object[] {
        "ProcessRecord",
        (Supplier<UnifiedRecordValue>)
            () -> {
              final String resourceName = "resource";
              final DirectBuffer resource = wrapString("contents");
              final String bpmnProcessId = "testProcess";
              final long processDefinitionKey = 123;
              final int processVersion = 12;
              final DirectBuffer checksum = wrapString("checksum");
              final long deploymentKey = 1234;
              final String versionTag = "v1.0";

              final ProcessRecord record = new ProcessRecord();
              record
                  .setResourceName(wrapString(resourceName))
                  .setResource(resource)
                  .setBpmnProcessId(wrapString(bpmnProcessId))
                  .setKey(processDefinitionKey)
                  .setResourceName(wrapString(resourceName))
                  .setVersion(processVersion)
                  .setChecksum(checksum)
                  .setDeploymentKey(deploymentKey)
                  .setVersionTag(versionTag);

              return record;
            },
        """
                {
                  "resourceName": "resource",
                  "resource": "Y29udGVudHM=",
                  "checksum": "Y2hlY2tzdW0=",
                  "bpmnProcessId": "testProcess",
                  "version": 12,
                  "processDefinitionKey": 123,
                  "resourceName": "resource",
                  "duplicate": false,
                  "tenantId": "<default>",
                  "deploymentKey": 1234,
                  "versionTag": "v1.0"
                }
                """
      },
      new Object[] {
        "ProcessRecord (with empty deployment key and version tag)",
        (Supplier<UnifiedRecordValue>)
            () -> {
              final String resourceName = "resource";
              final DirectBuffer resource = wrapString("contents");
              final String bpmnProcessId = "testProcess";
              final long processDefinitionKey = 123;
              final int processVersion = 12;
              final DirectBuffer checksum = wrapString("checksum");

              final ProcessRecord record = new ProcessRecord();
              record
                  .setResourceName(wrapString(resourceName))
                  .setResource(resource)
                  .setBpmnProcessId(wrapString(bpmnProcessId))
                  .setKey(processDefinitionKey)
                  .setResourceName(wrapString(resourceName))
                  .setVersion(processVersion)
                  .setChecksum(checksum);

              return record;
            },
        """
                {
                  "resourceName": "resource",
                  "resource": "Y29udGVudHM=",
                  "checksum": "Y2hlY2tzdW0=",
                  "bpmnProcessId": "testProcess",
                  "version": 12,
                  "processDefinitionKey": 123,
                  "resourceName": "resource",
                  "duplicate": false,
                  "tenantId": "<default>",
                  "deploymentKey": -1,
                  "versionTag": ""
                }
                """
      },
      /////////////////////////////////////////////////////////////////////////////////////////////
      ///////////////////////////////////// ErrorRecord
      /////////////////////////////////////////////////////////////////////////////////////////////
      // ///////////////////////////////////////////
      /////////////////////////////////////////////////////////////////////////////////////////////
      new Object[] {
        "ErrorRecord",
        (Supplier<UnifiedRecordValue>)
            () -> {
              final ErrorRecord record = new ErrorRecord();
              record.initErrorRecord(RUNTIME_EXCEPTION, 123);
              record.setProcessInstanceKey(4321);
              return record;
            },
        errorRecordAsJson(4321)
      },
      /////////////////////////////////////////////////////////////////////////////////////////////
      ///////////////////////////////////// Empty ErrorRecord
      /////////////////////////////////////////////////////////////////////////////////////////////
      // /////////////////////////////////////
      /////////////////////////////////////////////////////////////////////////////////////////////
      new Object[] {
        "Empty ErrorRecord",
        (Supplier<UnifiedRecordValue>)
            () -> {
              final ErrorRecord record = new ErrorRecord();
              record.initErrorRecord(RUNTIME_EXCEPTION, 123);
              return record;
            },
        errorRecordAsJson(-1)
      },
      /////////////////////////////////////////////////////////////////////////////////////////////
      ///////////////////////////////////// ExpressionRecord
      /////////////////////////////////////////////////////////////////////////////////////////////
      // ///////////////////////////////////////////
      /////////////////////////////////////////////////////////////////////////////////////////////
      new Object[] {
        "ExpressionRecord",
        (Supplier<UnifiedRecordValue>)
            () -> {
              final ExpressionRecord record = new ExpressionRecord();
              record
                  .setExpression("=10 + 5")
                  .setResultValue(wrapString("15"))
                  .setTenantId("test-tenant")
                  .setWarnings(List.of("warning1", "warning2"));
              return record;
            },
        """
                {
                  "tenantId":"test-tenant",
                  "expression":"=10 + 5",
                  "resultValue":49,
                  "warnings":["warning1","warning2"]
                }
                """
      },
      /////////////////////////////////////////////////////////////////////////////////////////////
      //////////////////////////////////// IncidentRecord
      /////////////////////////////////////////////////////////////////////////////////////////////
      // /////////////////////////////////////////
      /////////////////////////////////////////////////////////////////////////////////////////////
      new Object[] {
        "IncidentRecord",
        (Supplier<UnifiedRecordValue>)
            () -> {
              final long elementInstanceKey = 34;
              final long processDefinitionKey = 134;
              final long processInstanceKey = 10;
              final String elementId = "activity";
              final String bpmnProcessId = "process";
              final String errorMessage = "error";
              final ErrorType errorType = ErrorType.IO_MAPPING_ERROR;
              final long jobKey = 123;
              final var elementInstancePath = List.of(List.of(101L, 102L), List.of(103L, 104L));
              final var processDefinitionPath = List.of(101L, 102L);
              final var callingElementPath = List.of(12345, 67890);
              return new IncidentRecord()
                  .setElementInstanceKey(elementInstanceKey)
                  .setProcessDefinitionKey(processDefinitionKey)
                  .setProcessInstanceKey(processInstanceKey)
                  .setElementId(wrapString(elementId))
                  .setBpmnProcessId(wrapString(bpmnProcessId))
                  .setErrorMessage(errorMessage)
                  .setErrorType(errorType)
                  .setJobKey(jobKey)
                  .setVariableScopeKey(elementInstanceKey)
                  .setElementInstancePath(elementInstancePath)
                  .setProcessDefinitionPath(processDefinitionPath)
                  .setCallingElementPath(callingElementPath);
            },
        """
                {
                  "errorType": "IO_MAPPING_ERROR",
                  "errorMessage": "error",
                  "bpmnProcessId": "process",
                  "processDefinitionKey": 134,
                  "processInstanceKey": 10,
                  "elementId": "activity",
                  "elementInstanceKey": 34,
                  "jobKey": 123,
                  "variableScopeKey": 34,
                  "tenantId": "<default>",
                  "elementInstancePath":[[101, 102], [103, 104]],
                  "processDefinitionPath": [101, 102],
                  "callingElementPath": [12345, 67890],
                  "rootProcessInstanceKey": 101
                }
                """
      },
      /////////////////////////////////////////////////////////////////////////////////////////////
      //////////////////////////////////// Empty IncidentRecord
      /////////////////////////////////////////////////////////////////////////////////////////////
      // ///////////////////////////////////
      /////////////////////////////////////////////////////////////////////////////////////////////
      new Object[] {
        "Empty IncidentRecord",
        (Supplier<UnifiedRecordValue>) IncidentRecord::new,
        """
                {
                  "errorType": "UNKNOWN",
                  "errorMessage": "",
                  "bpmnProcessId": "",
                  "processDefinitionKey": -1,
                  "processInstanceKey": -1,
                  "elementId": "",
                  "elementInstanceKey": -1,
                  "jobKey": -1,
                  "variableScopeKey": -1,
                  "tenantId": "<default>",
                  "elementInstancePath":[],
                  "processDefinitionPath":[],
                  "callingElementPath":[],
                  "rootProcessInstanceKey": -1
                }
                """
      },

      /////////////////////////////////////////////////////////////////////////////////////////////
      ///////////////////////////////// JobBatchRecord
      /////////////////////////////////////////////////////////////////////////////////////////////
      // ////////////////////////////////////////////
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
              final int processDefinitionKey = 13;
              final int processDefinitionVersion = 12;
              final int processInstanceKey = 1234;
              final int rootProcessInstanceKey = 4321;
              final String activityId = "activity";
              final int activityInstanceKey = 123;
              final Set<String> changedAttributes = Set.of("bar", "foo");
              final JobResult result =
                  new JobResult()
                      .setType(JobResultType.USER_TASK)
                      .setDenied(true)
                      .setDeniedReason("Reason to deny lifecycle transition")
                      .setCorrections(
                          new JobResultCorrections()
                              .setAssignee("frodo")
                              .setDueDate("today")
                              .setFollowUpDate("tomorrow")
                              .setCandidateGroupsList(List.of("fellowship", "eagles"))
                              .setCandidateUsersList(List.of("frodo", "sam", "gollum"))
                              .setPriority(1))
                      .setCorrectedAttributes(
                          List.of(
                              "assignee",
                              "dueDate",
                              "followUpDate",
                              "candidateGroupsList",
                              "candidateUsersList",
                              "priority"))
                      .setActivateElements(
                          List.of(
                              new JobResultActivateElement()
                                  .setElementId("gandalf")
                                  .setVariables(VARIABLES_MSGPACK),
                              new JobResultActivateElement()
                                  .setElementId("sauron")
                                  .setVariables(VARIABLES_MSGPACK)))
                      .setCompletionConditionFulfilled(true)
                      .setCancelRemainingInstances(true);

              jobRecord
                  .setWorker(wrapString(worker))
                  .setType(wrapString(type))
                  .setVariables(VARIABLES_MSGPACK)
                  .setRetries(3)
                  .setRecurringTime(1001L)
                  .setRetryBackoff(1002L)
                  .setErrorMessage("failed message")
                  .setErrorCode(wrapString("error"))
                  .setDeadline(1000L)
                  .setBpmnProcessId(wrapString(bpmnProcessId))
                  .setProcessDefinitionKey(processDefinitionKey)
                  .setProcessDefinitionVersion(processDefinitionVersion)
                  .setProcessInstanceKey(processInstanceKey)
                  .setRootProcessInstanceKey(rootProcessInstanceKey)
                  .setElementId(wrapString(activityId))
                  .setElementInstanceKey(activityInstanceKey)
                  .setChangedAttributes(changedAttributes)
                  .setResult(result)
                  .setTags(Set.of("tag1", "tag2"))
                  .setIsJobToUserTaskMigration(true);

              return record;
            },
        """
                {
                  "maxJobsToActivate": 1,
                  "type": "type",
                  "worker": "worker",
                  "truncated": true,
                  "jobKeys": [
                    3
                  ],
                  "jobs": [
                    {
                      "bpmnProcessId": "test-process",
                      "processDefinitionKey": 13,
                      "processDefinitionVersion": 12,
                      "processInstanceKey": 1234,
                      "elementId": "activity",
                      "elementInstanceKey": 123,
                      "type": "type",
                      "worker": "worker",
                      "variables": {
                        "foo": "bar"
                      },
                      "retries": 3,
                      "jobKind": "BPMN_ELEMENT",
                      "jobListenerEventType": "UNSPECIFIED",
                      "retryBackoff": 1002,
                      "recurringTime": 1001,
                      "errorMessage": "failed message",
                      "errorCode": "error",
                      "customHeaders": {},
                      "deadline": 1000,
                      "timeout": -1,
                      "tenantId": "<default>",
                      "rootProcessInstanceKey": 4321,
                      "changedAttributes": ["bar", "foo"],
                      "tags": ["tag1", "tag2"],
                      "jobToUserTaskMigration": true,
                      "result": {
                        "type": "USER_TASK",
                        "denied": true,
                        "deniedReason": "Reason to deny lifecycle transition",
                        "correctedAttributes": [
                          "assignee",
                          "dueDate",
                          "followUpDate",
                          "candidateGroupsList",
                          "candidateUsersList",
                          "priority"
                        ],
                        "corrections": {
                          "assignee": "frodo",
                          "dueDate": "today",
                          "followUpDate": "tomorrow",
                          "candidateGroupsList": ["fellowship", "eagles"],
                          "candidateUsersList": ["frodo", "sam", "gollum"],
                          "priority": 1
                        },
                       "activateElements": [
                          {
                            "elementId": "gandalf",
                            "variables": {
                              "foo": "bar"
                            }
                          },
                          {
                            "elementId": "sauron",
                            "variables": {
                              "foo": "bar"
                            }
                          }
                        ],
                        "completionConditionFulfilled": true,
                        "cancelRemainingInstances": true
                      }
                    }
                  ],
                  "timeout": 2,
                  "tenantIds": [],
                  "tenantFilter": "PROVIDED"
                }
                """
      },
      /////////////////////////////////////////////////////////////////////////////////////////////
      ///////////////////////////////// Empty JobBatchRecord
      /////////////////////////////////////////////////////////////////////////////////////////////
      // //////////////////////////////////////
      /////////////////////////////////////////////////////////////////////////////////////////////
      {
        "Empty JobBatchRecord",
        (Supplier<UnifiedRecordValue>)
            () -> {
              final String type = "type";
              return new JobBatchRecord().setType(type);
            },
        """
                {
                  "worker": "",
                  "type": "type",
                  "maxJobsToActivate": -1,
                  "truncated": false,
                  "jobKeys": [],
                  "jobs": [],
                  "timeout": -1,
                  "tenantIds": [],
                  "tenantFilter": "PROVIDED"
                }
                """
      },
      /////////////////////////////////////////////////////////////////////////////////////////////
      ///////////////////////////////////// JobRecord
      /////////////////////////////////////////////////////////////////////////////////////////////
      // /////////////////////////////////////////////
      /////////////////////////////////////////////////////////////////////////////////////////////
      {
        "JobRecord",
        (Supplier<UnifiedRecordValue>)
            () -> {
              final String worker = "myWorker";
              final String type = "myType";
              final int retries = 12;
              final int deadline = 13;
              final int timeout = 14;

              final String bpmnProcessId = "test-process";
              final int processDefinitionKey = 13;
              final int processDefinitionVersion = 12;
              final int processInstanceKey = 1234;
              final int rootProcessInstanceKey = 4321;
              final String elementId = "activity";
              final int activityInstanceKey = 123;
              final Set<String> changedAttributes = Set.of("bar", "foo");
              final JobResult result =
                  new JobResult()
                      .setType(JobResultType.AD_HOC_SUB_PROCESS)
                      .setDenied(true)
                      .setDeniedReason("Reason to deny lifecycle transition")
                      .setCorrections(
                          new JobResultCorrections()
                              .setAssignee("frodo")
                              .setDueDate("today")
                              .setFollowUpDate("tomorrow")
                              .setCandidateGroupsList(List.of("fellowship", "eagles"))
                              .setCandidateUsersList(List.of("frodo", "sam", "gollum"))
                              .setPriority(1))
                      .setCorrectedAttributes(
                          List.of(
                              "assignee",
                              "dueDate",
                              "followUpDate",
                              "candidateGroupsList",
                              "candidateUsersList",
                              "priority"))
                      .setActivateElements(
                          List.of(
                              new JobResultActivateElement()
                                  .setElementId("gandalf")
                                  .setVariables(VARIABLES_MSGPACK),
                              new JobResultActivateElement()
                                  .setElementId("sauron")
                                  .setVariables(VARIABLES_MSGPACK)))
                      .setCompletionConditionFulfilled(true)
                      .setCancelRemainingInstances(true);

              final Map<String, String> customHeaders =
                  Collections.singletonMap("workerVersion", "42");

              final JobRecord record =
                  new JobRecord()
                      .setWorker(wrapString(worker))
                      .setType(wrapString(type))
                      .setVariables(VARIABLES_MSGPACK)
                      .setRetries(retries)
                      .setRetryBackoff(1003)
                      .setRecurringTime(1004)
                      .setDeadline(deadline)
                      .setTimeout(timeout)
                      .setErrorMessage("failed message")
                      .setErrorCode(wrapString("error"))
                      .setBpmnProcessId(wrapString(bpmnProcessId))
                      .setProcessDefinitionKey(processDefinitionKey)
                      .setProcessDefinitionVersion(processDefinitionVersion)
                      .setProcessInstanceKey(processInstanceKey)
                      .setElementId(wrapString(elementId))
                      .setElementInstanceKey(activityInstanceKey)
                      .setChangedAttributes(changedAttributes)
                      .setResult(result)
                      .setTags(Set.of("tag1", "tag2"))
                      .setRootProcessInstanceKey(rootProcessInstanceKey)
                      .setIsJobToUserTaskMigration(true);

              record.setCustomHeaders(wrapArray(MsgPackConverter.convertToMsgPack(customHeaders)));
              return record;
            },
        """
                {
                  "bpmnProcessId": "test-process",
                  "processDefinitionKey": 13,
                  "processDefinitionVersion": 12,
                  "processInstanceKey": 1234,
                  "elementId": "activity",
                  "elementInstanceKey": 123,
                  "worker": "myWorker",
                  "type": "myType",
                  "variables": {
                    "foo": "bar"
                  },
                  "retries": 12,
                  "jobKind": "BPMN_ELEMENT",
                  "jobListenerEventType": "UNSPECIFIED",
                  "retryBackoff": 1003,
                  "recurringTime": 1004,
                  "errorMessage": "failed message",
                  "errorCode": "error",
                  "customHeaders": {
                    "workerVersion": "42"
                  },
                  "deadline": 13,
                  "timeout": 14,
                  "tenantId": "<default>",
                  "rootProcessInstanceKey": 4321,
                  "tags": ["tag1", "tag2"],
                  "jobToUserTaskMigration": true,
                  "changedAttributes": ["bar", "foo"],
                  "result": {
                    "type": "AD_HOC_SUB_PROCESS",
                    "denied": true,
                    "deniedReason": "Reason to deny lifecycle transition",
                    "correctedAttributes": [
                      "assignee",
                      "dueDate",
                      "followUpDate",
                      "candidateGroupsList",
                      "candidateUsersList",
                      "priority"
                    ],
                    "corrections": {
                      "assignee": "frodo",
                      "dueDate": "today",
                      "followUpDate": "tomorrow",
                      "candidateGroupsList": ["fellowship", "eagles"],
                      "candidateUsersList": ["frodo", "sam", "gollum"],
                      "priority": 1
                    },
                    "activateElements": [
                      {
                        "elementId": "gandalf",
                        "variables": {
                          "foo": "bar"
                        }
                      },
                      {
                        "elementId": "sauron",
                        "variables": {
                          "foo": "bar"
                        }
                      }
                    ],
                    "completionConditionFulfilled": true,
                    "cancelRemainingInstances": true
                  }
                }
                """
      },

      /////////////////////////////////////////////////////////////////////////////////////////////
      ///////////////////////////////////// Empty JobRecord
      /////////////////////////////////////////////////////////////////////////////////////////////
      // ///////////////////////////////////////
      /////////////////////////////////////////////////////////////////////////////////////////////
      {
        "Empty JobRecord",
        (Supplier<UnifiedRecordValue>) JobRecord::new,
        """
                {
                  "type": "",
                  "processDefinitionVersion": -1,
                  "elementId": "",
                  "bpmnProcessId": "",
                  "processDefinitionKey": -1,
                  "processInstanceKey": -1,
                  "elementInstanceKey": -1,
                  "variables": {},
                  "worker": "",
                  "retries": -1,
                  "jobKind": "BPMN_ELEMENT",
                  "jobListenerEventType": "UNSPECIFIED",
                  "retryBackoff": 0,
                  "recurringTime": -1,
                  "errorMessage": "",
                  "errorCode": "",
                  "customHeaders": {},
                  "deadline": -1,
                  "timeout": -1,
                  "tenantId": "<default>",
                  "rootProcessInstanceKey": -1,
                  "tags": [],
                  "jobToUserTaskMigration": false,
                  "changedAttributes": [],
                  "result": {
                    "type": "USER_TASK",
                    "denied": false,
                    "deniedReason": "",
                    "correctedAttributes": [],
                    "corrections": {
                      "assignee": "",
                      "dueDate": "",
                      "followUpDate": "",
                      "candidateGroupsList": [],
                      "candidateUsersList": [],
                      "priority": -1
                    },
                    "activateElements": [],
                    "completionConditionFulfilled": false,
                    "cancelRemainingInstances": false
                  }
                }
                """
      },
      /////////////////////////////////////////////////////////////////////////////////////////////
      ///////////////////////////////////// JobRecord with nullable variable
      /////////////////////////////////////////////////////////////////////////////////////////////
      // //////////////////////
      /////////////////////////////////////////////////////////////////////////////////////////////
      {
        "JobRecordWithNullableVariable",
        (Supplier<UnifiedRecordValue>)
            () ->
                new JobRecord()
                    .setVariables(
                        new UnsafeBuffer(MsgPackConverter.convertToMsgPack("{'foo':null}"))),
        """
                {
                  "type": "",
                  "errorMessage": "",
                  "bpmnProcessId": "",
                  "processDefinitionKey": -1,
                  "processInstanceKey": -1,
                  "elementId": "",
                  "elementInstanceKey": -1,
                  "variables": {
                    "foo": null
                  },
                  "deadline": -1,
                  "timeout": -1,
                  "worker": "",
                  "retries": -1,
                  "jobKind": "BPMN_ELEMENT",
                  "jobListenerEventType": "UNSPECIFIED",
                  "retryBackoff": 0,
                  "recurringTime": -1,
                  "errorCode": "",
                  "processDefinitionVersion": -1,
                  "customHeaders": {},
                  "tenantId": "<default>",
                  "rootProcessInstanceKey": -1,
                  "tags": [],
                  "jobToUserTaskMigration": false,
                  "changedAttributes": [],
                  "result": {
                    "type": "USER_TASK",
                    "denied": false,
                    "deniedReason": "",
                    "correctedAttributes": [],
                    "corrections": {
                      "assignee": "",
                      "dueDate": "",
                      "followUpDate": "",
                      "candidateGroupsList": [],
                      "candidateUsersList": [],
                      "priority": -1
                    },
                    "activateElements": [],
                    "completionConditionFulfilled": false,
                    "cancelRemainingInstances": false
                  }
                }
                """
      },
      /////////////////////////////////////////////////////////////////////////////////////////////
      ///////////////////////////////// MessageRecord
      /////////////////////////////////////////////////////////////////////////////////////////////
      // /////////////////////////////////////////////
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
                  .setDeadline(22L)
                  .setMessageId(wrapString(messageId))
                  .setTenantId("foo");
            },
        """
                {
                  "timeToLive": 12,
                  "correlationKey": "test-key",
                  "variables": {
                    "foo": "bar"
                  },
                  "messageId": "test-id",
                  "name": "test-message",
                  "deadline": 22,
                  "tenantId": "foo"
                }
                """
      },
      /////////////////////////////////////////////////////////////////////////////////////////////
      ///////////////////////////////// Empty MessageRecord
      /////////////////////////////////////////////////////////////////////////////////////////////
      // ///////////////////////////////////////
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
        """
                {
                  "timeToLive": 12,
                  "correlationKey": "test-key",
                  "variables": {},
                  "messageId": "",
                  "name": "test-message",
                  "deadline": -1,
                  "tenantId": "<default>"
                }
                """
      },
      /////////////////////////////////////////////////////////////////////////////////////////////
      ///////////////////////////////// MessageBatchRecord
      // /////////////////////////////////////////////
      /////////////////////////////////////////////////////////////////////////////////////////////
      {
        "MessageBatchRecord",
        (Supplier<UnifiedRecordValue>)
            () -> {
              final List<Long> messageKeys = List.of(123L, 456L);

              return new MessageBatchRecord()
                  .addMessageKey(messageKeys.get(0))
                  .addMessageKey(messageKeys.get(1));
            },
        """
                {
                  "messageKeys": [
                    123,
                    456
                  ]
                }
                """
      },
      /////////////////////////////////////////////////////////////////////////////////////////////
      ///////////////////////////////// Empty MessageBatchRecord
      // ///////////////////////////////////////
      /////////////////////////////////////////////////////////////////////////////////////////////
      {
        "Empty MessageBatchRecord",
        (Supplier<UnifiedRecordValue>)
            () -> {
              return new MessageBatchRecord();
            },
        """
                {
                  "messageKeys": []
                }
                """
      },

      /////////////////////////////////////////////////////////////////////////////////////////////
      ///////////////////////////////// MessageStartEventSubscriptionRecord
      /////////////////////////////////////////////////////////////////////////////////////////////
      // ///////////////////////
      /////////////////////////////////////////////////////////////////////////////////////////////
      {
        "MessageStartEventSubscriptionRecord",
        (Supplier<UnifiedRecordValue>)
            () -> {
              final String messageName = "name";
              final String startEventId = "startEvent";
              final int processDefinitionKey = 22334;
              final String bpmnProcessId = "process";

              return new MessageStartEventSubscriptionRecord()
                  .setMessageName(wrapString(messageName))
                  .setStartEventId(wrapString(startEventId))
                  .setProcessDefinitionKey(processDefinitionKey)
                  .setBpmnProcessId(wrapString(bpmnProcessId))
                  .setProcessInstanceKey(2L)
                  .setMessageKey(3L)
                  .setCorrelationKey(wrapString("test-key"))
                  .setVariables(VARIABLES_MSGPACK);
            },
        """
                {
                  "processDefinitionKey": 22334,
                  "messageName": "name",
                  "startEventId": "startEvent",
                  "bpmnProcessId": "process",
                  "processInstanceKey": 2,
                  "messageKey": 3,
                  "correlationKey": "test-key",
                  "variables": {
                    "foo": "bar"
                  },
                  "tenantId": "<default>"
                }
                """
      },

      /////////////////////////////////////////////////////////////////////////////////////////////
      ///////////////////////////////// Empty MessageStartEventSubscriptionRecord
      /////////////////////////////////////////////////////////////////////////////////////////////
      // /////////////////
      /////////////////////////////////////////////////////////////////////////////////////////////
      {
        "Empty MessageStartEventSubscriptionRecord",
        (Supplier<UnifiedRecordValue>)
            () -> {
              final int processDefinitionKey = 22334;

              return new MessageStartEventSubscriptionRecord()
                  .setProcessDefinitionKey(processDefinitionKey);
            },
        """
                {
                  "processDefinitionKey": 22334,
                  "messageName": "",
                  "startEventId": "",
                  "bpmnProcessId": "",
                  "processInstanceKey": -1,
                  "messageKey": -1,
                  "correlationKey": "",
                  "variables": {},
                  "tenantId": "<default>"
                }
                """
      },

      /////////////////////////////////////////////////////////////////////////////////////////////
      ///////////////////////////////// MessageSubscriptionRecord
      /////////////////////////////////////////////////////////////////////////////////////////////
      // /////////////////////////////////
      /////////////////////////////////////////////////////////////////////////////////////////////
      {
        "MessageSubscriptionRecord",
        (Supplier<UnifiedRecordValue>)
            () -> {
              final long elementInstanceKey = 1L;
              final String bpmnProcessId = "process";
              final String messageName = "name";
              final long processInstanceKey = 2L;
              final long processDefinitionKey = 6L;
              final String correlationKey = "key";
              final long messageKey = 3L;

              return new MessageSubscriptionRecord()
                  .setElementInstanceKey(elementInstanceKey)
                  .setProcessDefinitionKey(processDefinitionKey)
                  .setBpmnProcessId(wrapString(bpmnProcessId))
                  .setMessageKey(messageKey)
                  .setMessageName(wrapString(messageName))
                  .setProcessInstanceKey(processInstanceKey)
                  .setCorrelationKey(wrapString(correlationKey))
                  .setVariables(VARIABLES_MSGPACK);
            },
        """
                {
                  "processInstanceKey": 2,
                  "elementInstanceKey": 1,
                  "processDefinitionKey": 6,
                  "messageName": "name",
                  "correlationKey": "key",
                  "bpmnProcessId": "process",
                  "messageKey": 3,
                  "variables": {
                    "foo": "bar"
                  },
                  "interrupting": true,
                  "tenantId": "<default>"
                }
                """
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
              final long processInstanceKey = 1L;

              return new MessageSubscriptionRecord()
                  .setProcessInstanceKey(processInstanceKey)
                  .setElementInstanceKey(elementInstanceKey);
            },
        """
                {
                  "processInstanceKey": 1,
                  "elementInstanceKey": 13,
                  "processDefinitionKey": -1,
                  "messageName": "",
                  "correlationKey": "",
                  "bpmnProcessId": "",
                  "messageKey": -1,
                  "variables": {},
                  "interrupting": true,
                  "tenantId": "<default>"
                }
                """
      },

      /////////////////////////////////////////////////////////////////////////////////////////////
      //////////////////////////// ProcessMessageSubscriptionRecord
      /////////////////////////////////////////////////////////////////////////////////////////////
      // /////////////////////////////
      /////////////////////////////////////////////////////////////////////////////////////////////
      {
        "ProcessMessageSubscriptionRecord",
        (Supplier<UnifiedRecordValue>)
            () -> {
              final long elementInstanceKey = 123;
              final String bpmnProcessId = "process";
              final String messageName = "test-message";
              final int subscriptionPartitionId = 2;
              final int messageKey = 3;
              final long processInstanceKey = 1345;
              final long processDefinitionKey = 444;
              final String correlationKey = "key";
              final long rootProcessInstanceKey = 5678L;

              return new ProcessMessageSubscriptionRecord()
                  .setElementInstanceKey(elementInstanceKey)
                  .setProcessDefinitionKey(processDefinitionKey)
                  .setBpmnProcessId(wrapString(bpmnProcessId))
                  .setMessageName(wrapString(messageName))
                  .setMessageKey(messageKey)
                  .setSubscriptionPartitionId(subscriptionPartitionId)
                  .setProcessInstanceKey(processInstanceKey)
                  .setVariables(VARIABLES_MSGPACK)
                  .setCorrelationKey(wrapString(correlationKey))
                  .setElementId(wrapString("A"))
                  .setRootProcessInstanceKey(rootProcessInstanceKey);
            },
        """
                {
                  "elementInstanceKey": 123,
                  "messageName": "test-message",
                  "processInstanceKey": 1345,
                  "processDefinitionKey": 444,
                  "variables": {
                    "foo": "bar"
                  },
                  "bpmnProcessId": "process",
                  "messageKey": 3,
                  "correlationKey": "key",
                  "elementId": "A",
                  "interrupting": true,
                  "tenantId": "<default>",
                  "rootProcessInstanceKey": 5678
                }
                """
      },

      /////////////////////////////////////////////////////////////////////////////////////////////
      //////////////////////////// Empty ProcessMessageSubscriptionRecord
      /////////////////////////////////////////////////////////////////////////////////////////////
      // ///////////////////////
      /////////////////////////////////////////////////////////////////////////////////////////////
      {
        "Empty ProcessMessageSubscriptionRecord",
        (Supplier<UnifiedRecordValue>)
            () -> {
              final long elementInstanceKey = 123;
              final long processInstanceKey = 1345;

              return new ProcessMessageSubscriptionRecord()
                  .setProcessInstanceKey(processInstanceKey)
                  .setElementInstanceKey(elementInstanceKey);
            },
        """
                {
                  "elementInstanceKey": 123,
                  "messageName": "",
                  "processInstanceKey": 1345,
                  "processDefinitionKey": -1,
                  "variables": {},
                  "bpmnProcessId": "",
                  "messageKey": -1,
                  "correlationKey": "",
                  "elementId": "",
                  "interrupting": true,
                  "tenantId": "<default>",
                  "rootProcessInstanceKey": -1
                }
                """
      },

      /////////////////////////////////////////////////////////////////////////////////////////////
      /////////////////////////////////// TimerRecord
      /////////////////////////////////////////////////////////////////////////////////////////////
      // /////////////////////////////////////////////
      /////////////////////////////////////////////////////////////////////////////////////////////
      {
        "TimerRecord",
        (Supplier<UnifiedRecordValue>)
            () -> {
              final int processDefinitionKey = 13;
              final int processInstanceKey = 1234;
              final int dueDate = 1234;
              final int elementInstanceKey = 567;
              final String handlerNodeId = "node1";
              final int repetitions = 3;

              return new TimerRecord()
                  .setDueDate(dueDate)
                  .setElementInstanceKey(elementInstanceKey)
                  .setTargetElementId(wrapString(handlerNodeId))
                  .setRepetitions(repetitions)
                  .setProcessInstanceKey(processInstanceKey)
                  .setProcessDefinitionKey(processDefinitionKey);
            },
        """
                {
                  "elementInstanceKey": 567,
                  "processInstanceKey": 1234,
                  "dueDate": 1234,
                  "targetElementId": "node1",
                  "repetitions": 3,
                  "processDefinitionKey": 13,
                  "tenantId": "<default>"
                }
                """
      },

      /////////////////////////////////////////////////////////////////////////////////////////////
      ///////////////////////////////// VariableRecord
      /////////////////////////////////////////////////////////////////////////////////////////////
      // ////////////////////////////////////////////
      /////////////////////////////////////////////////////////////////////////////////////////////
      {
        "VariableRecord",
        (Supplier<UnifiedRecordValue>)
            () -> {
              final String name = "x";
              final String value = "1";
              final long scopeKey = 3;
              final long processInstanceKey = 2;
              final long processDefinitionKey = 4;
              final String bpmnProcessId = "process";
              final long rootProcessInstanceKey = 5;

              return new VariableRecord()
                  .setName(wrapString(name))
                  .setValue(new UnsafeBuffer(MsgPackConverter.convertToMsgPack(value)))
                  .setScopeKey(scopeKey)
                  .setProcessInstanceKey(processInstanceKey)
                  .setProcessDefinitionKey(processDefinitionKey)
                  .setBpmnProcessId(wrapString(bpmnProcessId))
                  .setRootProcessInstanceKey(rootProcessInstanceKey);
            },
        """
                {
                  "scopeKey": 3,
                  "processInstanceKey": 2,
                  "processDefinitionKey": 4,
                  "bpmnProcessId": "process",
                  "name": "x",
                  "value": "1",
                  "tenantId": "<default>",
                  "rootProcessInstanceKey": 5,
                  "elementInstanceKey": 3
                }
                """
      },

      // custom tenant
      {
        "VariableRecord",
        (Supplier<UnifiedRecordValue>)
            () -> {
              final String name = "x";
              final String value = "1";
              final long scopeKey = 3;
              final long processInstanceKey = 2;
              final long processDefinitionKey = 4;
              final String bpmnProcessId = "process";
              final long rootProcessInstanceKey = 5;

              return new VariableRecord()
                  .setName(wrapString(name))
                  .setValue(new UnsafeBuffer(MsgPackConverter.convertToMsgPack(value)))
                  .setScopeKey(scopeKey)
                  .setProcessInstanceKey(processInstanceKey)
                  .setProcessDefinitionKey(processDefinitionKey)
                  .setBpmnProcessId(wrapString(bpmnProcessId))
                  .setTenantId("tenant-test")
                  .setRootProcessInstanceKey(rootProcessInstanceKey);
            },
        """
                {
                  "scopeKey": 3,
                  "processInstanceKey": 2,
                  "processDefinitionKey": 4,
                  "bpmnProcessId": "process",
                  "name": "x",
                  "value": "1",
                  "tenantId": "tenant-test",
                  "rootProcessInstanceKey": 5,
                  "elementInstanceKey": 3
                }
                """
      },

      /////////////////////////////////////////////////////////////////////////////////////////////
      ///////////////////////////////// VariableDocumentRecord
      /////////////////////////////////////////////////////////////////////////////////////////////
      // ////////////////////////////////////
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
        """
                {
                  "updateSemantics": "LOCAL",
                  "variables": {
                    "foo": 1
                  },
                  "scopeKey": 3,
                  "tenantId": "<default>"
                }
                """
      },

      /////////////////////////////////////////////////////////////////////////////////////////////
      ///////////////////////////////// Empty VariableDocumentRecord
      /////////////////////////////////////////////////////////////////////////////////////////////
      // //////////////////////////////
      /////////////////////////////////////////////////////////////////////////////////////////////
      {
        "Empty VariableDocumentRecord",
        (Supplier<UnifiedRecordValue>)
            () -> {
              final long scopeKey = 3;

              return new VariableDocumentRecord().setScopeKey(scopeKey);
            },
        """
                {
                  "updateSemantics": "PROPAGATE",
                  "variables": {},
                  "scopeKey": 3,
                  "tenantId": "<default>"
                }
                """
      },

      /////////////////////////////////////////////////////////////////////////////////////////////
      ///////////////////////////////// ProcessInstanceCreationRecord
      /////////////////////////////////////////////////////////////////////////////////////////////
      // ////////////////////////////
      /////////////////////////////////////////////////////////////////////////////////////////////
      {
        "ProcessInstanceCreationRecord",
        (Supplier<UnifiedRecordValue>)
            () -> {
              final String processId = "process";
              final long key = 1L;
              final int version = 1;
              final long instanceKey = 2L;
              final long rootProcessInstanceKey = 3L;
              final String businessId = "business-id-456";

              return new ProcessInstanceCreationRecord()
                  .setBpmnProcessId(processId)
                  .setProcessDefinitionKey(key)
                  .setVersion(version)
                  .setTenantId("test-tenant")
                  .setVariables(
                      new UnsafeBuffer(
                          MsgPackConverter.convertToMsgPack("{'foo':'bar','baz':'boz'}")))
                  .addStartInstruction(
                      new ProcessInstanceCreationStartInstruction().setElementId("element"))
                  .setProcessInstanceKey(instanceKey)
                  .setTags(Set.of("tag1", "tag2"))
                  .setRootProcessInstanceKey(rootProcessInstanceKey)
                  .setBusinessId(businessId);
            },
        """
                {
                  "variables": {
                    "foo": "bar",
                    "baz": "boz"
                  },
                  "bpmnProcessId": "process",
                  "processDefinitionKey": 1,
                  "version": 1,
                  "processInstanceKey": 2,
                  "startInstructions": [
                    {
                      "elementId": "element"
                    }
                  ],
                  "tenantId": "test-tenant",
                  "runtimeInstructions": [],
                  "tags": ["tag1", "tag2"],
                  "rootProcessInstanceKey": 3,
                  "businessId": "business-id-456",
                  "elementInstanceKey": -1
                }
                """
      },

      /////////////////////////////////////////////////////////////////////////////////////////////
      ///////////////////////////////// Empty ProcessInstanceCreationRecord
      /////////////////////////////////////////////////////////////////////////////////////////////
      // //////////////////////
      /////////////////////////////////////////////////////////////////////////////////////////////
      {
        "Empty ProcessInstanceCreationRecord",
        (Supplier<UnifiedRecordValue>) ProcessInstanceCreationRecord::new,
        """
                {
                  "variables": {},
                  "bpmnProcessId": "",
                  "processDefinitionKey": -1,
                  "version": -1,
                  "processInstanceKey": -1,
                  "startInstructions": [],
                  "tenantId": "<default>",
                  "runtimeInstructions": [],
                  "tags": [],
                  "rootProcessInstanceKey": -1,
                  "businessId": "",
                  "elementInstanceKey": -1
                }
                """
      },

      /////////////////////////////////////////////////////////////////////////////////////////////
      ///////////////////////////////// ProcessInstanceModificationRecord
      /////////////////////////////////////////////////////////////////////////////////////////////
      // /////////////////////////
      /////////////////////////////////////////////////////////////////////////////////////////////
      {
        "ProcessInstanceModificationRecord",
        (Supplier<UnifiedRecordValue>)
            () -> {
              final long key = 1L;
              final var elementInstanceKeyToTerminate = 2L;
              final var elementIdToActivate = "activity";
              final var ancestorScopeKey = 3L;
              final var variableInstructionElementId = "sub-process";
              final var rootProcessInstanceKey = 4L;
              final var processDefinitionKey = 5L;
              final var bpmnProcessId = "bpmnProcessId";

              return new ProcessInstanceModificationRecord()
                  .setProcessInstanceKey(key)
                  .addTerminateInstruction(
                      new ProcessInstanceModificationTerminateInstruction()
                          .setElementInstanceKey(elementInstanceKeyToTerminate)
                          .setElementId(elementIdToActivate))
                  .addMoveInstruction(
                      new ProcessInstanceModificationMoveInstruction()
                          .setSourceElementId(variableInstructionElementId)
                          .setSourceElementInstanceKey(elementInstanceKeyToTerminate)
                          .setTargetElementId(elementIdToActivate)
                          .addVariableInstruction(
                              new ProcessInstanceModificationVariableInstruction()
                                  .setVariables(VARIABLES_MSGPACK)
                                  .setElementId(variableInstructionElementId))
                          .setAncestorScopeKey(ancestorScopeKey)
                          .setInferAncestorScopeFromSourceHierarchy(true))
                  .addActivateInstruction(
                      new ProcessInstanceModificationActivateInstruction()
                          .setElementId(elementIdToActivate)
                          .setAncestorScopeKey(ancestorScopeKey)
                          .addVariableInstruction(
                              new ProcessInstanceModificationVariableInstruction()
                                  .setVariables(VARIABLES_MSGPACK)
                                  .setElementId(variableInstructionElementId))
                          .addAncestorScopeKeys(Set.of(key, ancestorScopeKey)))
                  .setRootProcessInstanceKey(rootProcessInstanceKey)
                  .setProcessDefinitionKey(processDefinitionKey)
                  .setBpmnProcessId(bpmnProcessId);
            },
        """
                {
                  "processInstanceKey": 1,
                  "terminateInstructions": [{
                    "elementInstanceKey": 2,
                    "elementId": "activity"
                  }],
                  "moveInstructions": [{
                    "sourceElementId": "sub-process",
                    "sourceElementInstanceKey": 2,
                    "targetElementId": "activity",
                    "variableInstructions": [{
                      "elementId": "sub-process",
                      "variables": {
                        "foo": "bar"
                      }
                    }],
                    "ancestorScopeKey": 3,
                    "inferAncestorScopeFromSourceHierarchy": true,
                    "useSourceParentKeyAsAncestorScopeKey": false
                  }],
                  "activateInstructions": [{
                    "ancestorScopeKey": 3,
                    "variableInstructions": [{
                      "elementId": "sub-process",
                      "variables": {
                        "foo": "bar"
                      }
                    }],
                    "elementId": "activity",
                    "ancestorScopeKeys": [1,3]
                  }],
                  "ancestorScopeKeys": [1,3],
                  "tenantId": "<default>",
                  "rootProcessInstanceKey": 4,
                  "processDefinitionKey": 5,
                  "bpmnProcessId": "bpmnProcessId",
                  "elementInstanceKey": -1
                }
                """
      },

      /////////////////////////////////////////////////////////////////////////////////////////////
      ///////////////////////////////// Empty ProcessInstanceModificationRecord
      /////////////////////////////////////////////////////////////////////////////////////////////
      // ///////////////////
      /////////////////////////////////////////////////////////////////////////////////////////////
      {
        "Empty ProcessInstanceModificationRecord",
        (Supplier<UnifiedRecordValue>)
            () -> new ProcessInstanceModificationRecord().setProcessInstanceKey(1L),
        """
                {
                  "processInstanceKey": 1,
                  "terminateInstructions": [],
                  "moveInstructions": [],
                  "activateInstructions": [],
                  "ancestorScopeKeys": [],
                  "tenantId": "<default>",
                  "rootProcessInstanceKey": -1,
                  "processDefinitionKey": -1,
                  "bpmnProcessId": "",
                  "elementInstanceKey": -1
                }
                """
      },

      /////////////////////////////////////////////////////////////////////////////////////////////
      ///////////////////////////////// ProcessInstanceRecord
      /////////////////////////////////////////////////////////////////////////////////////////////
      // ////////////////////////////////////
      /////////////////////////////////////////////////////////////////////////////////////////////
      {
        "ProcessInstanceRecord",
        (Supplier<UnifiedRecordValue>)
            () -> {
              final String bpmnProcessId = "test-process";
              final int processDefinitionKey = 13;
              final int version = 12;
              final int processInstanceKey = 1234;
              final String elementId = "activity";
              final int flowScopeKey = 123;
              final BpmnElementType bpmnElementType = BpmnElementType.SERVICE_TASK;
              final var elementInstancePath = List.of(List.of(101L, 102L), List.of(103L, 104L));
              final var processDefinitionPath = List.of(101L, 102L);
              final var callingElementPath = List.of(12345, 67890);
              final var rootProcessInstanceKey = 9999L;
              final var businessId = "business-id-123";

              return new ProcessInstanceRecord()
                  .setElementId(elementId)
                  .setBpmnElementType(bpmnElementType)
                  .setBpmnProcessId(wrapString(bpmnProcessId))
                  .setVersion(version)
                  .setProcessDefinitionKey(processDefinitionKey)
                  .setProcessInstanceKey(processInstanceKey)
                  .setFlowScopeKey(flowScopeKey)
                  .setParentProcessInstanceKey(11)
                  .setParentElementInstanceKey(22)
                  .setBpmnEventType(BpmnEventType.UNSPECIFIED)
                  .setElementInstancePath(elementInstancePath)
                  .setProcessDefinitionPath(processDefinitionPath)
                  .setCallingElementPath(callingElementPath)
                  .setTags(Set.of("tag1", "tag2"))
                  .setRootProcessInstanceKey(rootProcessInstanceKey)
                  .setBusinessId(businessId);
            },
        """
                {
                  "bpmnProcessId": "test-process",
                  "version": 12,
                  "processDefinitionKey": 13,
                  "processInstanceKey": 1234,
                  "elementId": "activity",
                  "flowScopeKey": 123,
                  "bpmnElementType": "SERVICE_TASK",
                  "parentProcessInstanceKey": 11,
                  "parentElementInstanceKey": 22,
                  "bpmnEventType": "UNSPECIFIED",
                  "tenantId": "<default>",
                  "elementInstancePath":[[101, 102], [103, 104]],
                  "processDefinitionPath": [101, 102],
                  "callingElementPath": [12345, 67890],
                  "tags": ["tag1", "tag2"],
                  "rootProcessInstanceKey": 9999,
                  "businessId": "business-id-123",
                  "elementInstanceKey": -1
                }
                """
      },

      /////////////////////////////////////////////////////////////////////////////////////////////
      ///////////////////////////////// Empty ProcessInstanceRecord
      /////////////////////////////////////////////////////////////////////////////////////////////
      // //////////////////////////////
      /////////////////////////////////////////////////////////////////////////////////////////////
      {
        "Empty ProcessInstanceRecord",
        (Supplier<UnifiedRecordValue>) ProcessInstanceRecord::new,
        """
                {
                  "bpmnProcessId": "",
                  "version": -1,
                  "processDefinitionKey": -1,
                  "processInstanceKey": -1,
                  "elementId": "",
                  "flowScopeKey": -1,
                  "bpmnElementType": "UNSPECIFIED",
                  "parentProcessInstanceKey": -1,
                  "parentElementInstanceKey": -1,
                  "bpmnEventType": "UNSPECIFIED",
                  "tenantId": "<default>",
                  "elementInstancePath":[],
                  "processDefinitionPath": [],
                  "callingElementPath": [],
                  "tags": [],
                  "rootProcessInstanceKey": -1,
                  "businessId": "",
                  "elementInstanceKey": -1
                }
                """
      },

      /////////////////////////////////////////////////////////////////////////////////////////////
      ///////////////////////////////// DecisionRecord
      /////////////////////////////////////////////////////////////////////////////////////////////
      // ///////////////////////////////////////////
      /////////////////////////////////////////////////////////////////////////////////////////////
      {
        "DecisionRecord",
        (Supplier<UnifiedRecordValue>)
            () ->
                new DecisionRecord()
                    .setDecisionId("decision-id")
                    .setDecisionName("decision-name")
                    .setVersion(1)
                    .setDecisionKey(2L)
                    .setDecisionRequirementsKey(3L)
                    .setDecisionRequirementsId("decision-requirements-id")
                    .setDeploymentKey(4L)
                    .setVersionTag("v1.0"),
        """
                {
                  "decisionId": "decision-id",
                  "decisionName": "decision-name",
                  "version": 1,
                  "decisionKey": 2,
                  "decisionRequirementsKey": 3,
                  "decisionRequirementsId": "decision-requirements-id",
                  "duplicate": false,
                  "tenantId": "<default>",
                  "deploymentKey": 4,
                  "versionTag": "v1.0"
                }
                """
      },
      {
        "DecisionRecord (with empty deployment key and version tag)",
        (Supplier<UnifiedRecordValue>)
            () ->
                new DecisionRecord()
                    .setDecisionId("decision-id")
                    .setDecisionName("decision-name")
                    .setVersion(1)
                    .setDecisionKey(2L)
                    .setDecisionRequirementsKey(3L)
                    .setDecisionRequirementsId("decision-requirements-id"),
        """
                {
                  "decisionId": "decision-id",
                  "decisionName": "decision-name",
                  "version": 1,
                  "decisionKey": 2,
                  "decisionRequirementsKey": 3,
                  "decisionRequirementsId": "decision-requirements-id",
                  "duplicate": false,
                  "tenantId": "<default>",
                  "deploymentKey": -1,
                  "versionTag": ""
                }
                """
      },

      /////////////////////////////////////////////////////////////////////////////////////////////
      ///////////////////////////////// DecisionRequirementsRecord
      /////////////////////////////////////////////////////////////////////////////////////////////
      // ///////////////////////////////
      /////////////////////////////////////////////////////////////////////////////////////////////
      {
        "DecisionRequirementsRecord",
        (Supplier<UnifiedRecordValue>)
            () ->
                new DecisionRequirementsRecord()
                    .setDecisionRequirementsId("decision-requirements-id")
                    .setDecisionRequirementsName("decision-requirements-name")
                    .setDecisionRequirementsVersion(1)
                    .setDecisionRequirementsKey(2L)
                    .setNamespace("namespace")
                    .setResourceName("resource-name")
                    .setResource(wrapString("resource"))
                    .setChecksum(wrapString("checksum"))
                    .setDeploymentKey(1234L),
        """
                {
                  "decisionRequirementsId": "decision-requirements-id",
                  "decisionRequirementsName": "decision-requirements-name",
                  "decisionRequirementsVersion": 1,
                  "decisionRequirementsKey": 2,
                  "namespace": "namespace",
                  "resourceName": "resource-name",
                  "resource": "cmVzb3VyY2U=",
                  "checksum": "Y2hlY2tzdW0=",
                  "duplicate": false,
                  "tenantId": "<default>",
                  "deploymentKey": 1234
                }
                """
      },

      /////////////////////////////////////////////////////////////////////////////////////////////
      ///////////////////////////////// DecisionEvaluationRecord
      /////////////////////////////////////////////////////////////////////////////////////////////
      // /////////////////////////////////
      /////////////////////////////////////////////////////////////////////////////////////////////
      {
        "DecisionEvaluationRecord",
        (Supplier<UnifiedRecordValue>)
            () -> {
              final var record =
                  new DecisionEvaluationRecord()
                      .setDecisionKey(1L)
                      .setDecisionId("decision-id")
                      .setDecisionName("decision-name")
                      .setDecisionVersion(1)
                      .setDecisionRequirementsKey(2L)
                      .setDecisionRequirementsId("decision-requirements-id")
                      .setDecisionOutput(toMessagePack("'decision-output'"))
                      .setVariables(VARIABLES_MSGPACK)
                      .setProcessDefinitionKey(3L)
                      .setBpmnProcessId("bpmn-process-id")
                      .setDecisionVersion(1)
                      .setProcessInstanceKey(4L)
                      .setElementInstanceKey(5L)
                      .setElementId("element-id")
                      .setEvaluationFailureMessage("evaluation-failure-message")
                      .setFailedDecisionId("failed-decision-id")
                      .setRootProcessInstanceKey(6L);

              final var evaluatedDecisionRecord = record.evaluatedDecisions().add();
              evaluatedDecisionRecord
                  .setDecisionId("decision-id")
                  .setDecisionEvaluationInstanceKey("decision-evaluation-instance-key")
                  .setDecisionName("decision-name")
                  .setDecisionKey(6L)
                  .setDecisionVersion(7)
                  .setDecisionType("DECISION_TABLE")
                  .setDecisionOutput(toMessagePack("'decision-output'"));

              evaluatedDecisionRecord
                  .evaluatedInputs()
                  .add()
                  .setInputId("input-id")
                  .setInputName("input-name")
                  .setInputValue(toMessagePack("'input-value'"));

              final var matchedRuleRecord = evaluatedDecisionRecord.matchedRules().add();
              matchedRuleRecord.setRuleId("rule-id").setRuleIndex(1);

              matchedRuleRecord
                  .evaluatedOutputs()
                  .add()
                  .setOutputId("output-id")
                  .setOutputName("output-name")
                  .setOutputValue(toMessagePack("'output-value'"));

              return record;
            },
        """
                {
                  "decisionKey":1,
                  "decisionId":"decision-id",
                  "decisionName":"decision-name",
                  "decisionVersion":1,
                  "decisionRequirementsKey":2,
                  "decisionRequirementsId":"decision-requirements-id",
                  "decisionOutput":'"decision-output"',
                  "variables": {
                    "foo": "bar"
                  },
                  "processDefinitionKey":3,
                  "bpmnProcessId":"bpmn-process-id",
                  "processInstanceKey":4,
                  "elementInstanceKey":5,
                  "elementId":"element-id",
                  "evaluatedDecisions":[
                    {
                      "decisionId":"decision-id",
                      "decisionName":"decision-name",
                      "decisionKey":6,
                      "decisionVersion":7,
                      "decisionOutput":'"decision-output"',
                      "decisionType":"DECISION_TABLE",
                      "tenantId": "<default>",
                      "evaluatedInputs":[
                        {
                          "inputId":"input-id",
                          "inputName":"input-name",
                          "inputValue":'"input-value"'
                        }
                      ],
                      "matchedRules":[
                        {
                          "ruleId":"rule-id",
                          "ruleIndex":1,
                          "evaluatedOutputs":[
                            {
                              "outputId":"output-id",
                              "outputName":"output-name",
                              "outputValue":'"output-value"'
                            }
                          ]
                        }
                      ]
                    }
                  ],
                  "evaluationFailureMessage":"evaluation-failure-message",
                  "failedDecisionId":"failed-decision-id",
                  "tenantId": "<default>",
                  "rootProcessInstanceKey": 6
                }
                """
      },

      // custom tenant
      {
        "DecisionEvaluationRecord",
        (Supplier<UnifiedRecordValue>)
            () -> {
              final var record =
                  new DecisionEvaluationRecord()
                      .setDecisionKey(1L)
                      .setDecisionId("decision-id")
                      .setDecisionName("decision-name")
                      .setDecisionVersion(1)
                      .setDecisionRequirementsKey(2L)
                      .setDecisionRequirementsId("decision-requirements-id")
                      .setDecisionOutput(toMessagePack("'decision-output'"))
                      .setVariables(VARIABLES_MSGPACK)
                      .setProcessDefinitionKey(3L)
                      .setBpmnProcessId("bpmn-process-id")
                      .setDecisionVersion(1)
                      .setProcessInstanceKey(4L)
                      .setElementInstanceKey(5L)
                      .setElementId("element-id")
                      .setEvaluationFailureMessage("evaluation-failure-message")
                      .setFailedDecisionId("failed-decision-id")
                      .setTenantId("tenant-test")
                      .setRootProcessInstanceKey(6L);

              final var evaluatedDecisionRecord = record.evaluatedDecisions().add();
              evaluatedDecisionRecord
                  .setDecisionId("decision-id")
                  .setDecisionEvaluationInstanceKey("decision-evaluation-instance-key")
                  .setDecisionName("decision-name")
                  .setDecisionKey(6L)
                  .setDecisionVersion(7)
                  .setDecisionType("DECISION_TABLE")
                  .setDecisionOutput(toMessagePack("'decision-output'"))
                  .setTenantId("tenant-test");

              evaluatedDecisionRecord
                  .evaluatedInputs()
                  .add()
                  .setInputId("input-id")
                  .setInputName("input-name")
                  .setInputValue(toMessagePack("'input-value'"));

              final var matchedRuleRecord = evaluatedDecisionRecord.matchedRules().add();
              matchedRuleRecord.setRuleId("rule-id").setRuleIndex(1);

              matchedRuleRecord
                  .evaluatedOutputs()
                  .add()
                  .setOutputId("output-id")
                  .setOutputName("output-name")
                  .setOutputValue(toMessagePack("'output-value'"));

              return record;
            },
        """
                {
                  "decisionKey":1,
                  "decisionId":"decision-id",
                  "decisionName":"decision-name",
                  "decisionVersion":1,
                  "decisionRequirementsKey":2,
                  "decisionRequirementsId":"decision-requirements-id",
                  "decisionOutput":'"decision-output"',
                  "variables": {
                    "foo": "bar"
                  },
                  "processDefinitionKey":3,
                  "bpmnProcessId":"bpmn-process-id",
                  "processInstanceKey":4,
                  "elementInstanceKey":5,
                  "elementId":"element-id",
                  "evaluatedDecisions":[
                    {
                      "decisionId":"decision-id",
                      "decisionName":"decision-name",
                      "decisionKey":6,
                      "decisionVersion":7,
                      "decisionOutput":'"decision-output"',
                      "decisionType":"DECISION_TABLE",
                      "tenantId": "tenant-test",
                      "evaluatedInputs":[
                        {
                          "inputId":"input-id",
                          "inputName":"input-name",
                          "inputValue":'"input-value"'
                        }
                      ],
                      "matchedRules":[
                        {
                          "ruleId":"rule-id",
                          "ruleIndex":1,
                          "evaluatedOutputs":[
                            {
                              "outputId":"output-id",
                              "outputName":"output-name",
                              "outputValue":'"output-value"'
                            }
                          ]
                        }
                      ]
                    }
                  ],
                  "evaluationFailureMessage":"evaluation-failure-message",
                  "failedDecisionId":"failed-decision-id",
                  "tenantId": "tenant-test",
                  "rootProcessInstanceKey": 6
                }
                """
      },

      /////////////////////////////////////////////////////////////////////////////////////////////
      ///////////////////////////////// Empty DecisionEvaluationRecord
      /////////////////////////////////////////////////////////////////////////////////////////////
      // ////////////////////////////
      /////////////////////////////////////////////////////////////////////////////////////////////
      {
        "Empty DecisionEvaluationRecord",
        (Supplier<UnifiedRecordValue>) () -> new DecisionEvaluationRecord(),
        """
                {
                  "decisionKey":-1,
                  "decisionId":"",
                  "decisionName":"",
                  "decisionVersion":-1,
                  "decisionRequirementsKey":-1,
                  "decisionRequirementsId":"",
                  "decisionOutput":"null",
                  "variables":{},
                  "processDefinitionKey":-1,
                  "bpmnProcessId":"",
                  "processInstanceKey":-1,
                  "elementInstanceKey":-1,
                  "elementId":"",
                  "evaluatedDecisions":[],
                  "evaluationFailureMessage":"",
                  "failedDecisionId":"",
                  "tenantId": "<default>",
                  "rootProcessInstanceKey": -1
                }
                """
      },
      /////////////////////////////////////////////////////////////////////////////////////////////
      ///////////////////////////////// Checkpoint record ////////////////////////////
      /////////////////////////////////////////////////////////////////////////////////////////////
      {
        "Checkpoint record",
        (Supplier<UnifiedRecordValue>)
            () ->
                new CheckpointRecord()
                    .setCheckpointId(1L)
                    .setCheckpointPosition(10L)
                    .setCheckpointType(CheckpointType.SCHEDULED_BACKUP)
                    .setFirstLogPosition(100L),
        """
                {
                  "checkpointId":1,
                  "checkpointPosition":10,
                  "checkpointType":"SCHEDULED_BACKUP",
                  "firstLogPosition":100
                }
                """
      },
      /////////////////////////////////////////////////////////////////////////////////////////////
      //////////////////////// Checkpoint record without type ///////////////////////
      /////////////////////////////////////////////////////////////////////////////////////////////
      {
        "Checkpoint record without timestamp",
        (Supplier<UnifiedRecordValue>)
            () -> new CheckpointRecord().setCheckpointId(1L).setCheckpointPosition(10L),
        """
                {
                  "checkpointId":1,
                  "checkpointPosition":10,
                  "checkpointType":"MANUAL_BACKUP",
                  "firstLogPosition":-1
                }
                """
      },
      /////////////////////////////////////////////////////////////////////////////////////////////
      ///////////////////////////////// Escalation record
      /////////////////////////////////////////////////////////////////////////////////////////////
      // /////////////////////////////////////////
      /////////////////////////////////////////////////////////////////////////////////////////////
      {
        "Escalation record",
        (Supplier<UnifiedRecordValue>)
            () ->
                new EscalationRecord()
                    .setProcessInstanceKey(4L)
                    .setEscalationCode("escalation")
                    .setThrowElementId(wrapString("throw"))
                    .setCatchElementId(wrapString("catch")),
        """
                {
                  "processInstanceKey":4,
                  "escalationCode": "escalation",
                  "throwElementId": "throw",
                  "catchElementId": "catch",
                  "tenantId": "<default>"
                }
                """
      },

      /////////////////////////////////////////////////////////////////////////////////////////////
      ///////////////////////////////// Empty EscalationRecord
      /////////////////////////////////////////////////////////////////////////////////////////////
      // ////////////////////////////////////
      /////////////////////////////////////////////////////////////////////////////////////////////
      {
        "Empty EscalationRecord",
        (Supplier<UnifiedRecordValue>) EscalationRecord::new,
        """
                {
                  "processInstanceKey":-1,
                  "escalationCode": "",
                  "throwElementId": "",
                  "catchElementId": "",
                  "tenantId": "<default>"
                }
                """
      },

      /////////////////////////////////////////////////////////////////////////////////////////////
      ///////////////////////////////// SignalRecord
      /////////////////////////////////////////////////////////////////////////////////////////////
      // /////////////////////////////////////////////
      /////////////////////////////////////////////////////////////////////////////////////////////
      {
        "SignalRecord",
        (Supplier<UnifiedRecordValue>)
            () -> {
              final String signalName = "test-signal";

              return new SignalRecord()
                  .setSignalName(wrapString(signalName))
                  .setVariables(VARIABLES_MSGPACK)
                  .setTenantId("acme");
            },
        """
                {
                  "signalName":"test-signal",
                  "variables": {
                    "foo": "bar"
                  },
                  "tenantId": "acme"
                }
                """
      },

      /////////////////////////////////////////////////////////////////////////////////////////////
      ///////////////////////////////// Empty SignalRecord
      /////////////////////////////////////////////////////////////////////////////////////////////
      // ///////////////////////////////////////
      /////////////////////////////////////////////////////////////////////////////////////////////
      {
        "Empty SignalRecord",
        (Supplier<UnifiedRecordValue>)
            () -> {
              final String signalName = "test-signal";

              return new SignalRecord().setSignalName(signalName);
            },
        """
                {
                  "signalName":"test-signal",
                  "variables": {},
                  "tenantId": "<default>"
                }
                """
      },

      /////////////////////////////////////////////////////////////////////////////////////////////
      ///////////////////////////////// SignalSubscriptionRecord
      /////////////////////////////////////////////////////////////////////////////////////////////
      // ///////////////////////
      /////////////////////////////////////////////////////////////////////////////////////////////
      {
        "SignalSubscriptionRecord",
        (Supplier<UnifiedRecordValue>)
            () -> {
              final String signalName = "name";
              final String catchEventId = "startEvent";
              final int processDefinitionKey = 22334;
              final String bpmnProcessId = "process";

              return new SignalSubscriptionRecord()
                  .setSignalName(wrapString(signalName))
                  .setCatchEventId(wrapString(catchEventId))
                  .setProcessDefinitionKey(processDefinitionKey)
                  .setBpmnProcessId(wrapString(bpmnProcessId))
                  .setCatchEventInstanceKey(3L)
                  .setTenantId("acme");
            },
        """
                {
                  "processDefinitionKey":22334,
                  "signalName": "name",
                  "catchEventId": "startEvent",
                  "bpmnProcessId": "process",
                  "catchEventInstanceKey":3,
                  "tenantId": "acme"
                }
                """
      },

      /////////////////////////////////////////////////////////////////////////////////////////////
      ///////////////////////////////// Empty SignalSubscriptionRecord
      /////////////////////////////////////////////////////////////////////////////////////////////
      // /////////////////
      /////////////////////////////////////////////////////////////////////////////////////////////
      {
        "Empty SignalStartEventSubscriptionRecord",
        (Supplier<UnifiedRecordValue>)
            () -> {
              final int processDefinitionKey = 22334;

              return new SignalSubscriptionRecord().setProcessDefinitionKey(processDefinitionKey);
            },
        """
                {
                  "processDefinitionKey":22334,
                  "signalName":"",
                  "catchEventId":"",
                  "bpmnProcessId":"",
                  "catchEventInstanceKey":-1,
                  "tenantId": "<default>"
                }
                """
      },
      /////////////////////////////////////////////////////////////////////////////////////////////
      ////////////////////////////////// ResourceDeletionRecord
      /////////////////////////////////////////////////////////////////////////////////////////////
      // ///////////////////////////////////
      /////////////////////////////////////////////////////////////////////////////////////////////
      {
        "ResourceDeletionRecord",
        (Supplier<UnifiedRecordValue>)
            () -> {
              final var resourceKey = 1L;
              final var batchOperationKey = 2L;

              return new ResourceDeletionRecord()
                  .setResourceKey(resourceKey)
                  .setTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER)
                  .setDeleteHistory(true)
                  .setBatchOperationKey(batchOperationKey)
                  .setBatchOperationType(BatchOperationType.DELETE_PROCESS_INSTANCE)
                  .setResourceType(ResourceType.PROCESS_DEFINITION)
                  .setResourceId("foo");
            },
        """
                {
                  "resourceKey":1,
                  "tenantId": "<default>",
                  "deleteHistory": true,
                  "batchOperationKey": 2,
                  "batchOperationType": "DELETE_PROCESS_INSTANCE",
                  "resourceType": "PROCESS_DEFINITION",
                  "resourceId": "foo"
                }
                """
      },
      {
        "Empty ResourceDeletionRecord",
        (Supplier<UnifiedRecordValue>)
            () -> {
              final var resourceKey = 1L;

              return new ResourceDeletionRecord()
                  .setResourceKey(resourceKey)
                  .setTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
            },
        """
                {
                  "resourceKey":1,
                  "tenantId": "<default>",
                  "deleteHistory": false,
                  "batchOperationKey": -1,
                  "batchOperationType": "DELETE_PROCESS_INSTANCE",
                  "resourceType": "UNKNOWN",
                  "resourceId": ""
                }
                """
      },

      /////////////////////////////////////////////////////////////////////////////////////////////
      ////////////////////////// AdHocSubProcessInstructionRecord
      /////////////////////////////////////////////////////////////////////////////////////////////
      // //////////////////////////
      /////////////////////////////////////////////////////////////////////////////////////////////
      {
        "AdHocSubProcessInstructionRecord",
        (Supplier<UnifiedRecordValue>)
            () -> {
              final var adHocSubProcessInstructionRecord =
                  new AdHocSubProcessInstructionRecord()
                      .setAdHocSubProcessInstanceKey(1234L)
                      .setTenantId(TenantOwned.DEFAULT_TENANT_IDENTIFIER)
                      .setCompletionConditionFulfilled(true);

              adHocSubProcessInstructionRecord.activateElements().add().setElementId("123");
              adHocSubProcessInstructionRecord
                  .activateElements()
                  .add()
                  .setElementId("234")
                  .setVariables(VARIABLES_MSGPACK);

              adHocSubProcessInstructionRecord.setCancelRemainingInstances(true);

              return adHocSubProcessInstructionRecord;
            },
        """
                {
                  "adHocSubProcessInstanceKey": 1234,
                  "tenantId": "<default>",
                  "activateElements": [
                    {
                      "elementId": "123",
                      "variables": {}
                    },
                    {
                      "elementId": "234",
                      "variables": {
                        "foo": "bar"
                      }
                    }
                  ],
                  "cancelRemainingInstances": true,
                  "completionConditionFulfilled": true
                }
                """
      },

      /////////////////////////////////////////////////////////////////////////////////////////////
      /////////////////////// Empty AdHocSubProcessInstructionRecord
      /////////////////////////////////////////////////////////////////////////////////////////////
      // ///////////////////////
      /////////////////////////////////////////////////////////////////////////////////////////////
      {
        "Empty AdHocSubProcessInstructionRecord",
        (Supplier<UnifiedRecordValue>) AdHocSubProcessInstructionRecord::new,
        """
                {
                  "adHocSubProcessInstanceKey": -1,
                  "tenantId": "<default>",
                  "activateElements": [],
                  "cancelRemainingInstances": false,
                  "completionConditionFulfilled": false
                }
                """
      },

      /////////////////////////////////////////////////////////////////////////////////////////////
      ///////////////////////////////// CommandDistributionRecord
      /////////////////////////////////////////////////////////////////////////////////////////////
      // //////////////////////////////////
      /////////////////////////////////////////////////////////////////////////////////////////////
      {
        "CommandDistributionRecord",
        (Supplier<UnifiedRecordValue>)
            () -> {
              final var deploymentRecord = new DeploymentRecord();
              deploymentRecord
                  .resources()
                  .add()
                  .setResourceName("my_first_bpmn.bpmn")
                  .setResource(wrapString("This is the contents of the BPMN"));
              deploymentRecord
                  .processesMetadata()
                  .add()
                  .setKey(123)
                  .setVersion(1)
                  .setBpmnProcessId("my_first_process")
                  .setResourceName("my_first_bpmn.bpmn")
                  .setChecksum(wrapString("sha1"));

              return new CommandDistributionRecord()
                  .setPartitionId(1)
                  .setQueueId("totally-random-queue-id")
                  .setValueType(ValueType.DEPLOYMENT)
                  .setIntent(DeploymentIntent.CREATE)
                  .setCommandValue(deploymentRecord)
                  .setAuthInfo(new AuthInfo().setClaims(Map.of("claim-a", "foo")));
            },
        """
                {
                  "partitionId": 1,
                  "queueId": "totally-random-queue-id",
                  "valueType": "DEPLOYMENT",
                  "intent": "CREATE",
                  "commandValue": {
                    "resources": [{
                      "resource": "VGhpcyBpcyB0aGUgY29udGVudHMgb2YgdGhlIEJQTU4=",
                      "resourceName": "my_first_bpmn.bpmn"
                    }],
                    "processesMetadata": [{
                      "processDefinitionKey": 123,
                      "version": 1,
                      "bpmnProcessId": "my_first_process",
                      "resourceName": "my_first_bpmn.bpmn",
                      "checksum": "c2hhMQ==",
                      "duplicate": false,
                      "tenantId": "<default>",
                      "deploymentKey": -1,
                      "versionTag": ""
                    }],
                    "decisionsMetadata": [],
                    "decisionRequirementsMetadata": [],
                    "formMetadata": [],
                    "resourceMetadata":[],
                    "tenantId": "<default>",
                    "deploymentKey": -1
                  },
                  "authInfo":{"format":"UNKNOWN","claims":{"claim-a": "foo"},"authData":""}
                }
                """
      },

      /////////////////////////////////////////////////////////////////////////////////////////////
      ///////////////////////////////// Empty CommandDistributionRecord
      /////////////////////////////////////////////////////////////////////////////////////////////
      // ////////////////////////////
      /////////////////////////////////////////////////////////////////////////////////////////////
      {
        "Empty CommandDistributionRecord",
        (Supplier<UnifiedRecordValue>) () -> new CommandDistributionRecord().setPartitionId(1),
        """
                {
                  "partitionId": 1,
                  "queueId": null,
                  "valueType": "NULL_VAL",
                  "intent": "UNKNOWN",
                  "commandValue": null,
                  "authInfo":{"format":"UNKNOWN","claims":{},"authData":""}
                }
                """
      },

      /////////////////////////////////////////////////////////////////////////////////////////////
      //////////////////////////////// ProcessInstanceBatchRecord
      /////////////////////////////////////////////////////////////////////////////////////////////
      // /////////////////////////////////
      /////////////////////////////////////////////////////////////////////////////////////////////
      {
        "ProcessInstanceBatchRecord",
        (Supplier<UnifiedRecordValue>)
            () ->
                new ProcessInstanceBatchRecord()
                    .setProcessInstanceKey(123L)
                    .setProcessDefinitionKey(234L)
                    .setBatchElementInstanceKey(456L)
                    .setIndex(10L),
        """
                {
                  "processInstanceKey": 123,
                  "processDefinitionKey": 234,
                  "batchElementInstanceKey": 456,
                  "index": 10,
                  "tenantId": "<default>"
                }
                """
      },

      /////////////////////////////////////////////////////////////////////////////////////////////
      //////////////////////////////// Empty ProcessInstanceBatchRecord
      /////////////////////////////////////////////////////////////////////////////////////////////
      // ///////////////////////////
      /////////////////////////////////////////////////////////////////////////////////////////////
      {
        "Empty ProcessInstanceBatchRecord",
        (Supplier<UnifiedRecordValue>)
            () ->
                new ProcessInstanceBatchRecord()
                    .setProcessInstanceKey(123L)
                    .setBatchElementInstanceKey(456L),
        """
                {
                  "processInstanceKey": 123,
                  "processDefinitionKey": -1,
                  "batchElementInstanceKey": 456,
                  "index": -1,
                  "tenantId": "<default>"
                }
                """
      },

      /////////////////////////////////////////////////////////////////////////////////////////////
      ////////////////////////////////// UserTaskRecord
      /////////////////////////////////////////////////////////////////////////////////////////////
      // ///////////////////////////////////////////
      /////////////////////////////////////////////////////////////////////////////////////////////
      {
        "UserTaskRecord",
        (Supplier<UnifiedRecordValue>)
            () ->
                new UserTaskRecord()
                    .setUserTaskKey(123)
                    .setAssignee("myAssignee")
                    .setCandidateGroupsList(List.of("myCandidateGroups"))
                    .setCandidateUsersList(List.of("myCandidateUsers"))
                    .setCreationTimestamp(1699633748000L)
                    .setDueDate("2023-11-11T11:11:00+01:00")
                    .setFollowUpDate("2023-11-12T11:11:00+01:00")
                    .setFormKey(456)
                    .setExternalFormReference("myReference")
                    .setVariables(VARIABLES_MSGPACK)
                    .setCustomHeaders(
                        wrapArray(MsgPackConverter.convertToMsgPack(Map.of("foo", "bar"))))
                    .setChangedAttributes(List.of("foo", "bar"))
                    .setAction("complete")
                    .setBpmnProcessId("test-process")
                    .setProcessDefinitionKey(13)
                    .setProcessDefinitionVersion(12)
                    .setProcessInstanceKey(1234)
                    .setElementId("activity")
                    .setElementInstanceKey(5678)
                    .setPriority(80)
                    .setDeniedReason("Reason to deny lifecycle transition")
                    .setListenersConfigKey(42L)
                    .setRootProcessInstanceKey(4321L),
        """
                {
                  "bpmnProcessId": "test-process",
                  "processDefinitionKey": 13,
                  "processDefinitionVersion": 12,
                  "processInstanceKey": 1234,
                  "elementId": "activity",
                  "elementInstanceKey": 5678,
                  "assignee": "myAssignee",
                  "candidateGroupsList": ["myCandidateGroups"],
                  "candidateUsersList": ["myCandidateUsers"],
                  "creationTimestamp": 1699633748000,
                  "dueDate": "2023-11-11T11:11:00+01:00",
                  "followUpDate": "2023-11-12T11:11:00+01:00",
                  "changedAttributes": ["foo", "bar"],
                  "externalFormReference": "myReference",
                  "variables": {
                    "foo": "bar"
                  },
                  "customHeaders": {
                    "foo": "bar"
                  },
                  "action": "complete",
                  "formKey": 456,
                  "userTaskKey": 123,
                  "tenantId": "<default>",
                  "priority": 80,
                  "tags": [],
                  "deniedReason": "Reason to deny lifecycle transition",
                  "listenersConfigKey": 42,
                  "rootProcessInstanceKey": 4321
                }
                """
      },

      /////////////////////////////////////////////////////////////////////////////////////////////
      ////////////////////////////////// Empty
      /////////////////////////////////////////////////////////////////////////////////////////////
      // UserTaskRecord//////////////////////////////////////
      /////////////////////////////////////////////////////////////////////////////////////////////
      {
        "Empty UserTaskRecord",
        (Supplier<UnifiedRecordValue>) UserTaskRecord::new,
        """
                {
                  "bpmnProcessId": "",
                  "processDefinitionKey": -1,
                  "processDefinitionVersion": -1,
                  "processInstanceKey": -1,
                  "elementId": "",
                  "elementInstanceKey": -1,
                  "assignee": "",
                  "candidateGroupsList": [],
                  "candidateUsersList": [],
                  "creationTimestamp": -1,
                  "dueDate": "",
                  "followUpDate": "",
                  "changedAttributes": [],
                  "externalFormReference": "",
                  "variables": {},
                  "customHeaders": {},
                  "action": "",
                  "formKey": -1,
                  "userTaskKey": -1,
                  "tenantId": "<default>",
                  "priority": 50,
                  "tags": [],
                  "deniedReason": "",
                  "listenersConfigKey": -1,
                  "rootProcessInstanceKey": -1
                }
                """
      },
      /////////////////////////////////////////////////////////////////////////////////////////////
      ///////////////////////////// UserTaskRecord with nullable variable
      /////////////////////////////////////////////////////////////////////////////////////////////
      // /////////////////////////
      /////////////////////////////////////////////////////////////////////////////////////////////
      {
        "UserTaskRecord WithNullableVariable",
        (Supplier<UnifiedRecordValue>)
            () ->
                new UserTaskRecord()
                    .setVariables(
                        new UnsafeBuffer(MsgPackConverter.convertToMsgPack("{'foo':null}"))),
        """
                {
                  "bpmnProcessId": "",
                  "processDefinitionKey": -1,
                  "processDefinitionVersion": -1,
                  "processInstanceKey": -1,
                  "elementId": "",
                  "elementInstanceKey": -1,
                  "assignee": "",
                  "candidateGroupsList": [],
                  "candidateUsersList": [],
                  "creationTimestamp": -1,
                  "dueDate": "",
                  "followUpDate": "",
                  "changedAttributes": [],
                  "externalFormReference": "",
                  "variables": {
                    "foo": null
                  },
                  "customHeaders": {},
                  "action": "",
                  "formKey": -1,
                  "userTaskKey": -1,
                  "tenantId": "<default>",
                  "priority": 50,
                  "tags": [],
                  "deniedReason": "",
                  "listenersConfigKey": -1,
                  "rootProcessInstanceKey": -1
                }
                """
      },

      /////////////////////////////////////////////////////////////////////////////////////////////
      ////////////////////////////// ProcessInstanceMigrationRecord
      /////////////////////////////////////////////////////////////////////////////////////////////
      // ///////////////////////////////
      /////////////////////////////////////////////////////////////////////////////////////////////
      {
        "ProcessInstanceMigrationRecord",
        (Supplier<UnifiedRecordValue>)
            () ->
                new ProcessInstanceMigrationRecord()
                    .setTenantId("tenantId")
                    .setProcessInstanceKey(123L)
                    .setTargetProcessDefinitionKey(456L)
                    .addMappingInstruction(
                        new ProcessInstanceMigrationMappingInstruction()
                            .setSourceElementId("sourceId")
                            .setTargetElementId("targetId"))
                    .addMappingInstruction(
                        new ProcessInstanceMigrationMappingInstruction()
                            .setSourceElementId("sourceId2"))
                    .addMappingInstruction(
                        new ProcessInstanceMigrationMappingInstruction()
                            .setTargetElementId("targetId3"))
                    .setRootProcessInstanceKey(321L)
                    .setProcessDefinitionKey(234L)
                    .setBpmnProcessId("bpmnProcessId"),
        """
                {
                  "tenantId": "tenantId",
                  "processInstanceKey": 123,
                  "targetProcessDefinitionKey": 456,
                  "mappingInstructions": [{
                    "sourceElementId": "sourceId",
                    "targetElementId": "targetId"
                  }, {
                    "sourceElementId": "sourceId2",
                    "targetElementId": ""
                  }, {
                    "sourceElementId": "",
                    "targetElementId": "targetId3"
                  }],
                  "rootProcessInstanceKey": 321,
                  "processDefinitionKey": 234,
                  "bpmnProcessId": "bpmnProcessId",
                  "elementInstanceKey": -1
                }
                """
      },

      /////////////////////////////////////////////////////////////////////////////////////////////
      ////////////////////////////// Empty ProcessInstanceMigrationRecord
      /////////////////////////////////////////////////////////////////////////////////////////////
      // /////////////////////////
      /////////////////////////////////////////////////////////////////////////////////////////////
      {
        "Empty ProcessInstanceMigrationRecord",
        (Supplier<UnifiedRecordValue>)
            () ->
                new ProcessInstanceMigrationRecord()
                    .setProcessInstanceKey(123L)
                    .setTargetProcessDefinitionKey(456L),
        """
                {
                  "tenantId": "<default>",
                  "processInstanceKey": 123,
                  "targetProcessDefinitionKey": 456,
                  "mappingInstructions": [],
                  "rootProcessInstanceKey": -1,
                  "processDefinitionKey": -1,
                  "bpmnProcessId": "",
                  "elementInstanceKey": -1
                }
                """
      },

      /////////////////////////////////////////////////////////////////////////////////////////////
      ////////////////////////////// CompensationSubscriptionRecord
      /////////////////////////////////////////////////////////////////////////////////////////////
      // ///////////////////////////////
      /////////////////////////////////////////////////////////////////////////////////////////////
      {
        "CompensationSubscriptionRecord",
        (Supplier<UnifiedRecordValue>)
            () ->
                new CompensationSubscriptionRecord()
                    .setTenantId("tenantId")
                    .setProcessInstanceKey(123L)
                    .setProcessDefinitionKey(456L)
                    .setCompensableActivityId("elementActivityId")
                    .setThrowEventId("elementThrowEventId")
                    .setThrowEventInstanceKey(123L)
                    .setCompensationHandlerId("compensationActivityElementId")
                    .setCompensationHandlerInstanceKey(100L)
                    .setCompensableActivityScopeKey(789L)
                    .setCompensableActivityInstanceKey(123L)
                    .setVariables(VARIABLES_MSGPACK),
        """
                {
                  "tenantId": "tenantId",
                  "processInstanceKey": 123,
                  "processDefinitionKey": 456,
                  "compensableActivityId": "elementActivityId",
                  "throwEventId": "elementThrowEventId",
                  "throwEventInstanceKey": 123,
                  "compensationHandlerId": "compensationActivityElementId",
                  "compensationHandlerInstanceKey": 100,
                  "compensableActivityScopeKey": 789,
                  "compensableActivityInstanceKey": 123,
                  "variables": {
                    "foo": "bar"
                  }
                }
                """
      },

      /////////////////////////////////////////////////////////////////////////////////////////////
      ////////////////////////////// Empty CompensationSubscriptionRecord
      /////////////////////////////////////////////////////////////////////////////////////////////
      // /////////////////////////
      /////////////////////////////////////////////////////////////////////////////////////////////
      {
        "CompensationSubscriptionRecord",
        (Supplier<UnifiedRecordValue>) CompensationSubscriptionRecord::new,
        """
                {
                  "tenantId": "<default>",
                  "processInstanceKey": -1,
                  "processDefinitionKey": -1,
                  "compensableActivityId": "",
                  "throwEventId": "",
                  "throwEventInstanceKey": -1,
                  "compensationHandlerId": "",
                  "compensationHandlerInstanceKey": -1,
                  "compensableActivityScopeKey": -1,
                  "compensableActivityInstanceKey": -1,
                  "variables": {}
                }
                """
      },
      /////////////////////////////////////////////////////////////////////////////////////////////
      //////////////////////////// MessageCorrelationRecord
      /////////////////////////////////////////////////////////////////////////////////////////////
      // ///////////////////////////////////////
      /////////////////////////////////////////////////////////////////////////////////////////////
      {
        "MessageCorrelationRecord",
        (Supplier<UnifiedRecordValue>)
            () -> {
              final String correlationKey = "test-key";
              final String messageName = "test-message";
              final long processInstanceKey = 1L;
              final long messageKey = 2L;
              final long requestId = 3L;
              final int requestStreamId = 4;

              return new MessageCorrelationRecord()
                  .setCorrelationKey(correlationKey)
                  .setName(messageName)
                  .setVariables(VARIABLES_MSGPACK)
                  .setTenantId("foo")
                  .setProcessInstanceKey(processInstanceKey)
                  .setMessageKey(messageKey)
                  .setRequestId(requestId)
                  .setRequestStreamId(requestStreamId)
                  .setProcessDefinitionKey(5L);
            },
        """
                {
                  "correlationKey": "test-key",
                  "variables": {
                    "foo": "bar"
                  },
                  "name": "test-message",
                  "tenantId": "foo",
                  "processInstanceKey": 1,
                  "messageKey": 2,
                  "requestId": 3,
                  "requestStreamId": 4,
                  "processDefinitionKey": 5
                }
                """
      },

      /////////////////////////////////////////////////////////////////////////////////////////////
      //////////////////////////////////////// UserRecord
      /////////////////////////////////////////////////////////////////////////////////////////////
      // /////////////////////////////////////////
      /////////////////////////////////////////////////////////////////////////////////////////////
      {
        "UserRecord",
        (Supplier<UserRecord>)
            () ->
                new UserRecord()
                    .setUserKey(1L)
                    .setUsername("foobar")
                    .setName("Foo Bar")
                    .setEmail("foo@bar")
                    .setPassword("f00b4r"),
        """
                {
                  "userKey": 1,
                  "username": "foobar",
                  "name": "Foo Bar",
                  "email": "foo@bar",
                  "password": "f00b4r"
                }
                """
      },
      /////////////////////////////////////////////////////////////////////////////////////////////
      ///////////////////////////////////// Empty UserRecord
      /////////////////////////////////////////////////////////////////////////////////////////////
      // //////////////////////////////////////
      /////////////////////////////////////////////////////////////////////////////////////////////
      {
        "UserRecord",
        (Supplier<UserRecord>) () -> new UserRecord().setUsername("foobar"),
        """
                {
                  "userKey": -1,
                  "username": "foobar",
                  "name": "",
                  "email": "",
                  "password": ""
                }
                """
      },

      /////////////////////////////////////////////////////////////////////////////////////////////
      //////////////////////////////////////// ClockRecord
      /////////////////////////////////////////////////////////////////////////////////////////////
      // ////////////////////////////////////////
      /////////////////////////////////////////////////////////////////////////////////////////////
      {
        "ClockRecord (pin)",
        (Supplier<ClockRecord>) () -> new ClockRecord().pinAt(5),
        """
                {
                  "time": 5
                }
                """
      },
      {
        "ClockRecord (offset)",
        (Supplier<ClockRecord>) () -> new ClockRecord().offsetBy(30),
        """
                {
                  "time": 30
                }
                """
      },
      {
        "ClockRecord (none)",
        (Supplier<ClockRecord>)
            () -> {
              final var record = new ClockRecord().offsetBy(30);
              record.reset();
              return record;
            },
        """
                {
                  "time": 0
                }
                """
      },
      /////////////////////////////////////////////////////////////////////////////////////////////
      //////////////////////////////////// AuthorizationRecord
      /////////////////////////////////////////////////////////////////////////////////////////////
      // ////////////////////////////////////
      /////////////////////////////////////////////////////////////////////////////////////////////
      {
        "Authorization record (ID-based)",
        (Supplier<AuthorizationRecord>)
            () ->
                new AuthorizationRecord()
                    .setAuthorizationKey(1L)
                    .setOwnerId("ownerId")
                    .setOwnerType(AuthorizationOwnerType.USER)
                    .setResourceMatcher(AuthorizationResourceMatcher.ID)
                    .setResourceId("resourceId")
                    .setResourceType(AuthorizationResourceType.RESOURCE)
                    .setPermissionTypes(Set.of(PermissionType.CREATE)),
        """
                {
                  "authorizationKey": 1,
                  "ownerId": "ownerId",
                  "ownerType": "USER",
                  "resourceMatcher": "ID",
                  "resourceId": "resourceId",
                  "resourcePropertyName": "",
                  "resourceType": "RESOURCE",
                  "permissionTypes": [
                    "CREATE"
                  ]
                }
                """
      },
      {
        "Authorization record (property-based)",
        (Supplier<AuthorizationRecord>)
            () ->
                new AuthorizationRecord()
                    .setAuthorizationKey(2L)
                    .setOwnerId("ownerId")
                    .setOwnerType(AuthorizationOwnerType.USER)
                    .setResourceMatcher(AuthorizationResourceMatcher.PROPERTY)
                    .setResourcePropertyName("candidateUsers")
                    .setResourceType(AuthorizationResourceType.USER_TASK)
                    .setPermissionTypes(Set.of(PermissionType.COMPLETE)),
        """
                {
                  "authorizationKey": 2,
                  "ownerId": "ownerId",
                  "ownerType": "USER",
                  "resourceMatcher": "PROPERTY",
                  "resourceId": "",
                  "resourcePropertyName": "candidateUsers",
                  "resourceType": "USER_TASK",
                  "permissionTypes": [
                     "COMPLETE"
                  ]
                }
                """
      },
      /////////////////////////////////////////////////////////////////////////////////////////////
      ///////////////////////////////// Empty AuthorizationRecord
      /////////////////////////////////////////////////////////////////////////////////////////////
      // /////////////////////////////////
      /////////////////////////////////////////////////////////////////////////////////////////////
      {
        "Empty AuthorizationRecord",
        (Supplier<AuthorizationRecord>)
            () ->
                new AuthorizationRecord()
                    .setResourceMatcher(AuthorizationResourceMatcher.UNSPECIFIED),
        """
                {
                  "authorizationKey": -1,
                  "ownerId": "",
                  "ownerType": "UNSPECIFIED",
                  "resourceMatcher": "UNSPECIFIED",
                  "resourceId": "",
                  "resourcePropertyName": "",
                  "resourceType": "UNSPECIFIED",
                  "permissionTypes": []
                }
                """
      },
      /////////////////////////////////////////////////////////////////////////////////////////////
      //////////////////////////// Empty MessageCorrelationRecord
      /////////////////////////////////////////////////////////////////////////////////////////////
      // /////////////////////////////////
      /////////////////////////////////////////////////////////////////////////////////////////////
      {
        "Empty MessageCorrelationRecord",
        (Supplier<MessageCorrelationRecord>)
            () -> {
              final String correlationKey = "test-key";
              final String messageName = "test-message";

              return new MessageCorrelationRecord()
                  .setCorrelationKey(correlationKey)
                  .setName(messageName);
            },
        """
                {
                  "correlationKey": "test-key",
                  "variables": {},
                  "name": "test-message",
                  "tenantId": "<default>",
                  "processInstanceKey": -1,
                  "messageKey": -1,
                  "requestId": -1,
                  "requestStreamId": -1,
                  "processDefinitionKey": -1
                }
                """
      },
      /////////////////////////////////////////////////////////////////////////////////////////////
      //////////////////////////////////// RoleRecord
      /////////////////////////////////////////////////////////////////////////////////////////////
      // /////////////////////////////////////////////
      /////////////////////////////////////////////////////////////////////////////////////////////
      {
        "Role record",
        (Supplier<RoleRecord>)
            () ->
                new RoleRecord()
                    .setRoleKey(1L)
                    .setRoleId("id")
                    .setName("role")
                    .setDescription("description")
                    .setEntityId("entityId")
                    .setEntityType(EntityType.USER),
        """
                {
                  "roleKey": 1,
                  "roleId": "id",
                  "name": "role",
                  "description": "description",
                  "entityId": "entityId",
                  "entityType": "USER"
                }
                """
      },
      /////////////////////////////////////////////////////////////////////////////////////////////
      ///////////////////////////////// Empty RoleRecord
      /////////////////////////////////////////////////////////////////////////////////////////////
      // /////////////////////////////////////////
      /////////////////////////////////////////////////////////////////////////////////////////////
      {
        "Empty RoleRecord",
        (Supplier<RoleRecord>) () -> new RoleRecord().setRoleId("roleId"),
        """
                {
                  "roleKey": -1,
                  "roleId": "roleId",
                  "name": "",
                  "description": "",
                  "entityId": "",
                  "entityType": "UNSPECIFIED"
                }
                """
      },

      /////////////////////////////////////////////////////////////////////////////////////////////
      //////////////////////////////////////// TenantRecord
      /////////////////////////////////////////////////////////////////////////////////////////////
      // ///////////////////////////////////////
      /////////////////////////////////////////////////////////////////////////////////////////////
      {
        "TenantRecord",
        (Supplier<UnifiedRecordValue>)
            () ->
                new TenantRecord()
                    .setTenantKey(123L)
                    .setTenantId("tenant-abc")
                    .setName("Test Tenant")
                    .setDescription("Test Description")
                    .setEntityId("entity-xyz")
                    .setEntityType(EntityType.USER),
        """
                {
                  "tenantKey": 123,
                  "tenantId": "tenant-abc",
                  "name": "Test Tenant",
                  "description": "Test Description",
                  "entityId": "entity-xyz",
                  "entityType": "USER"
                }
                """
      },
      /////////////////////////////////////////////////////////////////////////////////////////////
      /////////////////////////////////////// Empty TenantRecord
      /////////////////////////////////////////////////////////////////////////////////////////////
      // //////////////////////////////////
      /////////////////////////////////////////////////////////////////////////////////////////////
      {
        "Empty TenantRecord",
        (Supplier<UnifiedRecordValue>) () -> new TenantRecord().setTenantId("tenantId"),
        """
                {
                  "tenantKey": -1,
                  "tenantId": "tenantId",
                  "name": "",
                  "description": "",
                  "entityId": "",
                  "entityType": "UNSPECIFIED"
                }
                """
      },

      /////////////////////////////////////////////////////////////////////////////////////////////
      //////////////////////////////////////// ScaleRecord
      /////////////////////////////////////////////////////////////////////////////////////////////
      // ////////////////////////////////////////
      /////////////////////////////////////////////////////////////////////////////////////////////
      {
        "ScaleRecord (empty)",
        (Supplier<ScaleRecord>) ScaleRecord::new,
        """
                {
                  "desiredPartitionCount": -1,
                  "redistributedPartitions": [],
                  "relocatedPartitions": [],
                  "messageCorrelationPartitions": -1,
                  "scalingPosition": -1
                }
                """
      },
      {
        "ScaleRecord",
        (Supplier<ScaleRecord>) () -> new ScaleRecord().setDesiredPartitionCount(5),
        """
                {
                 "desiredPartitionCount": 5,
                  "redistributedPartitions": [],
                  "relocatedPartitions": [],
                  "messageCorrelationPartitions": -1,
                  "scalingPosition": -1
                }
                """
      },
      {
        "ScaleRecord w/ redistributedPartitions & relocatedPartitions & scalingPosition",
        (Supplier<ScaleRecord>)
            () ->
                new ScaleRecord()
                    .setDesiredPartitionCount(5)
                    .setRelocatedPartitions(List.of(4, 5))
                    .setRedistributedPartitions(List.of(4, 5))
                    .setMessageCorrelationPartitions(5)
                    .setScalingPosition(199L),
        """
                {
                 "desiredPartitionCount": 5,
                  "redistributedPartitions": [4,5],
                  "relocatedPartitions": [4,5],
                  "messageCorrelationPartitions": 5,
                  "scalingPosition": 199
                }
                """
      },

      /////////////////////////////////////////////////////////////////////////////////////////////
      //////////////////////////////////// GroupRecord
      /////////////////////////////////////////////////////////////////////////////////////////////
      // /////////////////////////////////////////////
      /////////////////////////////////////////////////////////////////////////////////////////////
      {
        "Group record",
        (Supplier<GroupRecord>)
            () ->
                new GroupRecord()
                    .setGroupKey(1L)
                    .setGroupId("groupId")
                    .setName("group")
                    .setDescription("description")
                    .setEntityId("entityId")
                    .setEntityType(EntityType.USER),
        """
                {
                  "groupKey": 1,
                  "groupId": "groupId",
                  "name": "group",
                  "description": "description",
                  "entityId": "entityId",
                  "entityType": "USER"
                }
                """
      },
      /////////////////////////////////////////////////////////////////////////////////////////////
      ///////////////////////////////// Empty GroupRecord
      /////////////////////////////////////////////////////////////////////////////////////////////
      // /////////////////////////////////////////
      /////////////////////////////////////////////////////////////////////////////////////////////
      {
        "Empty GroupRecord",
        (Supplier<GroupRecord>) () -> new GroupRecord().setGroupId("groupId"),
        """
                {
                  "groupKey": -1,
                  "groupId": "groupId",
                  "name": "",
                  "description": "",
                  "entityId": "",
                  "entityType": "UNSPECIFIED"
                }
                """
      },
      /////////////////////////////////////////////////////////////////////////////////////////////
      //////////////////////////////////// MappingRecord
      /////////////////////////////////////////////////////////////////////////////////////////////
      // //////////////////////////////////////////
      /////////////////////////////////////////////////////////////////////////////////////////////
      {
        "Mapping rule record",
        (Supplier<MappingRuleRecord>)
            () ->
                new MappingRuleRecord()
                    .setMappingRuleKey(1L)
                    .setClaimName("claimName")
                    .setClaimValue("claimValue")
                    .setMappingRuleId("id1")
                    .setName("name"),
        """
                {
                  "mappingRuleKey": 1,
                  "claimName": "claimName",
                  "claimValue": "claimValue",
                  "mappingRuleId": "id1",
                  "name": "name"
                }
                """
      },
      /////////////////////////////////////////////////////////////////////////////////////////////
      ///////////////////////////////// Empty MappingRecord
      /////////////////////////////////////////////////////////////////////////////////////////////
      // ///////////////////////////////////////
      /////////////////////////////////////////////////////////////////////////////////////////////
      {
        "Empty MappingRuleRecord",
        (Supplier<MappingRuleRecord>)
            () -> new MappingRuleRecord().setMappingRuleId("mappingRuleId"),
        """
                {
                  "mappingRuleKey": -1,
                  "mappingRuleId": "mappingRuleId",
                  "claimName": "",
                  "claimValue": "",
                  "name": ""
                }
                """
      },
      /////////////////////////////////////////////////////////////////////////////////////////////
      ///////////////////////////////// IdentitySetupRecord
      /////////////////////////////////////////////////////////////////////////////////////////////
      // ///////////////////////////////////////
      /////////////////////////////////////////////////////////////////////////////////////////////
      {
        "IdentitySetup record",
        (Supplier<IdentitySetupRecord>)
            () ->
                new IdentitySetupRecord()
                    .addRole(
                        new RoleRecord()
                            .setRoleKey(1)
                            .setRoleId("id")
                            .setName("roleName")
                            .setDescription("description")
                            .setEntityId("entityId")
                            .setEntityType(EntityType.USER))
                    .addRoleMember(
                        new RoleRecord()
                            .setRoleId("id")
                            .setEntityType(EntityType.USER)
                            .setEntityId("username"))
                    .addUser(
                        new UserRecord()
                            .setUserKey(3L)
                            .setUsername("username")
                            .setName("name")
                            .setEmail("email")
                            .setPassword("password"))
                    .addUser(
                        new UserRecord()
                            .setUserKey(4L)
                            .setUsername("foo")
                            .setName("bar")
                            .setEmail("baz")
                            .setPassword("qux"))
                    .setDefaultTenant(
                        new TenantRecord().setTenantKey(5).setTenantId("id").setName("name"))
                    .addTenant(
                        new TenantRecord().setTenantKey(42).setTenantId("foo").setName("Foo"))
                    .addTenantMember(
                        new TenantRecord()
                            .setTenantId("id")
                            .setEntityType(EntityType.ROLE)
                            .setEntityId("id"))
                    .addMappingRule(
                        new MappingRuleRecord()
                            .setMappingRuleKey(6)
                            .setMappingRuleId("id1")
                            .setClaimName("claim1")
                            .setClaimValue("value1")
                            .setName("Claim 1"))
                    .addMappingRule(
                        new MappingRuleRecord()
                            .setMappingRuleKey(7)
                            .setMappingRuleId("id2")
                            .setClaimName("claim2")
                            .setClaimValue("value2")
                            .setName("Claim 2"))
                    .addAuthorization(
                        new AuthorizationRecord()
                            .setOwnerId("id2")
                            .setOwnerType(AuthorizationOwnerType.MAPPING_RULE)
                            .setResourceType(AuthorizationResourceType.RESOURCE)
                            .setResourceMatcher(AuthorizationResourceMatcher.ID)
                            .setResourceId("resource-id")
                            .setPermissionTypes(Set.of(PermissionType.CREATE)))
                    .addGroup(new GroupRecord().setGroupId("group1").setName("Group 1"))
                    .addGroupMember(
                        new GroupRecord()
                            .setGroupId("group1")
                            .setEntityType(EntityType.USER)
                            .setEntityId("username")),
        """
                {
                  "roles": [
                    {
                      "roleKey": 1,
                      "roleId": "id",
                      "name": "roleName",
                      "description": "description",
                      "entityId": "entityId",
                      "entityType": "USER"
                    }
                  ],
                  "roleMembers": [
                    {
                      "roleKey": -1,
                      "roleId": "id",
                      "name": "",
                      "description": "",
                      "entityId": "username",
                      "entityType": "USER"
                    }
                  ],
                  "users": [
                    {
                      "userKey": 3,
                      "username": "username",
                      "name": "name",
                      "email": "email",
                      "password": "password"
                    },
                    {
                      "userKey": 4,
                      "username": "foo",
                      "name": "bar",
                      "email": "baz",
                      "password": "qux"
                    }
                  ],
                  "defaultTenant": {
                    "tenantKey": 5,
                    "tenantId": "id",
                    "name": "name",
                    "description": "",
                    "entityId": "",
                    "entityType": "UNSPECIFIED"
                  },
                  "tenants": [
                    {
                      "tenantKey": 42,
                      "tenantId": "foo",
                      "name": "Foo",
                      "description": "",
                      "entityId": "",
                      "entityType": "UNSPECIFIED"
                    }
                  ],
                  "tenantMembers": [
                    {
                      "tenantKey": -1,
                      "tenantId": "id",
                      "name": "",
                      "description": "",
                      "entityId": "id",
                      "entityType": "ROLE"
                    }
                  ],
                  "mappingRules": [
                    {
                      "mappingRuleKey": 6,
                      "mappingRuleId": "id1",
                      "claimName": "claim1",
                      "claimValue": "value1",
                      "name": "Claim 1"
                    },
                    {
                      "mappingRuleKey": 7,
                      "mappingRuleId": "id2",
                      "claimName": "claim2",
                      "claimValue": "value2",
                      "name": "Claim 2"
                    }
                  ],
                  "authorizations": [
                    {
                      "authorizationKey": -1,
                      "ownerId": "id2",
                      "ownerType": "MAPPING_RULE",
                      "resourceMatcher": "ID",
                      "resourceId": "resource-id",
                      "resourcePropertyName": "",
                      "resourceType": "RESOURCE",
                      "permissionTypes": ["CREATE"]
                    }
                  ],
                  "groups": [
                    {
                      "groupKey": -1,
                      "groupId": "group1",
                      "name": "Group 1",
                      "description": "",
                      "entityId": "",
                      "entityType": "UNSPECIFIED"
                    }
                  ],
                  "groupMembers": [
                    {
                      "groupKey": -1,
                      "groupId": "group1",
                      "name": "",
                      "description": "",
                      "entityId": "username",
                      "entityType": "USER"
                    }
                  ]
                }
                """
      },
      /////////////////////////////////////////////////////////////////////////////////////////////
      ////////////////////////////// Empty IdentitySetupRecord
      /////////////////////////////////////////////////////////////////////////////////////////////
      // ////////////////////////////////////
      /////////////////////////////////////////////////////////////////////////////////////////////
      {
        "Empty IdentitySetupRecord",
        (Supplier<IdentitySetupRecord>)
            () ->
                new IdentitySetupRecord()
                    .setDefaultTenant(new TenantRecord().setTenantId("tenantId")),
        """
                {
                    "roles": [],
                    "users": [],
                    "defaultTenant": {
                        "tenantKey": -1,
                        "tenantId": "tenantId",
                        "name": "",
                        "description": "",
                        "entityId": "",
                        "entityType": "UNSPECIFIED"
                    },
                    "tenants": [],
                    "mappingRules": [],
                    "roleMembers": [],
                    "tenantMembers": [],
                    "authorizations": [],
                    "groups": [],
                    "groupMembers": []
                }
                """
      },
      /////////////////////////////////////////////////////////////////////////////////////////////
      ////////////////////////////// Empty BatchOperationCreationRecord
      /////////////////////////////////////////////////////////////////////////////////////////////
      // ///////////////////////////
      /////////////////////////////////////////////////////////////////////////////////////////////
      {
        "Empty BatchOperationCreationRecord",
        (Supplier<BatchOperationCreationRecord>)
            () ->
                new BatchOperationCreationRecord()
                    .setBatchOperationKey(12345L)
                    .setPartitionIds(List.of(1, 2, 3))
                    .setBatchOperationType(BatchOperationType.CANCEL_PROCESS_INSTANCE),
        """
                {
                  "batchOperationKey": 12345,
                  "batchOperationType": "CANCEL_PROCESS_INSTANCE",
                  "entityFilterBuffer": {"expandable":false},
                  "entityFilter": null,
                  "partitionIds": [1, 2, 3],
                  "migrationPlan":{"targetProcessDefinitionKey":-1,"mappingInstructions":[],"empty":false,"encodedLength":50},
                  "modificationPlan":{"moveInstructions":[],"empty":false,"encodedLength":19},
                  "authenticationBuffer": {"expandable":false},
                  "authorizationCheckBuffer": {"expandable":false},
                  "followUpCommand": {
                    "valueType": "NULL_VAL",
                    "intent": "UNKNOWN",
                    "recordValue": null
                  }
                }
                """
      },
      /////////////////////////////////////////////////////////////////////////////////////////////
      ////////////////////////////// Full BatchOperationCreationRecord
      /////////////////////////////////////////////////////////////////////////////////////////////
      // ////////////////////////////
      /////////////////////////////////////////////////////////////////////////////////////////////
      {
        "BatchOperationCreationRecord",
        (Supplier<BatchOperationCreationRecord>)
            () ->
                new BatchOperationCreationRecord()
                    .setBatchOperationKey(12345L)
                    .setPartitionIds(List.of(1, 2, 3))
                    .setBatchOperationType(BatchOperationType.MIGRATE_PROCESS_INSTANCE)
                    .setEntityFilter(
                        toMessagePack("{'processDefinitionKey': 67890, 'state': 'ACTIVE'}"))
                    .setMigrationPlan(
                        new BatchOperationProcessInstanceMigrationPlan()
                            .setTargetProcessDefinitionKey(98765L)
                            .addMappingInstruction(
                                new ProcessInstanceMigrationMappingInstruction()
                                    .setSourceElementId("sourceTask")
                                    .setTargetElementId("targetTask")))
                    .setModificationPlan(
                        new BatchOperationProcessInstanceModificationPlan()
                            .addMoveInstruction(
                                new ProcessInstanceModificationMoveInstruction()
                                    .setSourceElementId("sourceTask")
                                    .setTargetElementId("targetTask")
                                    .addVariableInstruction(
                                        new ProcessInstanceModificationVariableInstruction()
                                            .setVariables(VARIABLES_MSGPACK)
                                            .setElementId("sub-process"))
                                    .setAncestorScopeKey(55555L)
                                    .setInferAncestorScopeFromSourceHierarchy(true)))
                    .setAuthentication(
                        toMessagePack(
                            """
                            {
                              'authenticated_username': 'bud spencer',
                              'authenticated_client_id': 'client-123',
                              'authenticated_group_ids': ['groupA', 'groupB'],
                              'authenticated_role_ids': ['roleX', 'roleY'],
                              'authenticated_tenant_ids': ['tenant1', 'tenant2'],
                              'authenticated_mapping_ids': ['mapping1', 'mapping2'],
                              'claims': {
                                'email': 'budspencer@example.com',
                                'department': 'engineering'
                              }
                            }
                            """))
                    .setAuthorizationCheck(
                        toMessagePack(
                            """
                            {
                              'resource_type': 'PROCESS_DEFINITION',
                              'permission_type': 'UPDATE_PROCESS_INSTANCE',
                              'resource_ids': 'foobar_process'
                            }
                            """))
                    .setFollowUpCommand(
                        ValueType.HISTORY_DELETION,
                        HistoryDeletionIntent.DELETE,
                        new HistoryDeletionRecord()
                            .setResourceKey(1)
                            .setResourceType(HistoryDeletionType.PROCESS_DEFINITION)),
        """
                {
                   "batchOperationKey": 12345,
                   "batchOperationType": "MIGRATE_PROCESS_INSTANCE",
                   "entityFilterBuffer": {
                     "expandable": false
                   },
                  "partitionIds": [1, 2, 3],
                   "entityFilter": "{\\"processDefinitionKey\\":67890,\\"state\\":\\"ACTIVE\\"}",
                   "migrationPlan": {
                     "mappingInstructions": [
                       {
                         "targetElementId": "targetTask",
                         "sourceElementId": "sourceTask"
                       }
                     ],
                     "targetProcessDefinitionKey": 98765,
                     "empty": false,
                     "encodedLength": 109
                   },
                   "modificationPlan": {
                     "moveInstructions": [
                       {
                         "sourceElementInstanceKey": -1,
                         "targetElementId": "targetTask",
                         "sourceElementId": "sourceTask",
                         "variableInstructions": [{
                           "elementId": "sub-process",
                           "variables": {
                             "foo": "bar"
                           }
                         }],
                         "ancestorScopeKey": 55555,
                         "inferAncestorScopeFromSourceHierarchy": true,
                         "useSourceParentKeyAsAncestorScopeKey": false
                       }
                     ],
                     "empty": false,
                     "encodedLength": 265
                   },
                   "authenticationBuffer": {
                     "expandable": false
                   },
                   "authorizationCheckBuffer": {
                     "expandable": false
                   },
                    "followUpCommand": {
                      "valueType": "HISTORY_DELETION",
                      "intent": "DELETE",
                      "recordValue": {
                        "resourceKey": 1,
                        "resourceType": "PROCESS_DEFINITION",
                        "processId": "",
                        "tenantId": "<default>",
                        "decisionDefinitionId": ""
                      }
                    }
                 }
                """
      },
      /////////////////////////////////////////////////////////////////////////////////////////////
      ////////////////////////// Empty BatchOperationChunkRecord
      /////////////////////////////////////////////////////////////////////////////////////////////
      // //////////////////////////////////
      /////////////////////////////////////////////////////////////////////////////////////////////
      {
        "Empty BatchOperationChunkRecord",
        (Supplier<BatchOperationChunkRecord>)
            () -> new BatchOperationChunkRecord().setBatchOperationKey(12345L),
        """
                {
                  "batchOperationKey": 12345,
                  "items": []
                }
                """
      },
      /////////////////////////////////////////////////////////////////////////////////////////////
      ////////////////////////////// BatchOperationChunkRecord
      /////////////////////////////////////////////////////////////////////////////////////////////
      // ////////////////////////////////////
      /////////////////////////////////////////////////////////////////////////////////////////////
      {
        "BatchOperationChunkRecord",
        (Supplier<BatchOperationChunkRecord>)
            () ->
                new BatchOperationChunkRecord()
                    .setBatchOperationKey(12345L)
                    .setItems(
                        List.of(
                            new BatchOperationItem().setItemKey(1L).setProcessInstanceKey(2L),
                            new BatchOperationItem().setItemKey(2L).setProcessInstanceKey(2L))),
        """
                {
                  "items": [
                    {
                      "itemKey": 1,
                      "processInstanceKey": 2,
                      "empty": false,
                      "encodedLength": 30
                    },
                    {
                      "itemKey": 2,
                      "processInstanceKey": 2,
                      "empty": false,
                      "encodedLength": 30
                    }
                  ],
                  "batchOperationKey": 12345
                }
                """
      },
      /////////////////////////////////////////////////////////////////////////////////////////////
      ////////////////////////////// BatchOperationExecutionRecord
      /////////////////////////////////////////////////////////////////////////////////////////////
      // ////////////////////////////////
      /////////////////////////////////////////////////////////////////////////////////////////////
      {
        "BatchOperationExecutionRecord",
        (Supplier<BatchOperationExecutionRecord>)
            () ->
                new BatchOperationExecutionRecord()
                    .setBatchOperationKey(12345L)
                    .setItemKeys(Set.of(1L, 2L)),
        """
                {
                  "batchOperationKey": 12345,
                  "itemKeys": [1, 2]
                }
                """
      },
      /////////////////////////////////////////////////////////////////////////////////////////////
      //////////////////////// Empty BatchOperationExecutionRecord
      /////////////////////////////////////////////////////////////////////////////////////////////
      // ////////////////////////////////
      /////////////////////////////////////////////////////////////////////////////////////////////
      {
        "Empty BatchOperationExecutionRecord",
        (Supplier<BatchOperationExecutionRecord>)
            () -> new BatchOperationExecutionRecord().setBatchOperationKey(12345L),
        """
                {
                  "batchOperationKey": 12345,
                  "itemKeys": []
                }
                """
      },
      /////////////////////////////////////////////////////////////////////////////////////////////
      ///////////////////////// BatchOperationLifecycleManagementRecord
      /////////////////////////////////////////////////////////////////////////////////////////////
      // ///////////////////////////
      /////////////////////////////////////////////////////////////////////////////////////////////
      {
        "BatchOperationLifecycleManagementRecord",
        (Supplier<BatchOperationLifecycleManagementRecord>)
            () -> new BatchOperationLifecycleManagementRecord().setBatchOperationKey(12345L),
        """
                {
                  "batchOperationKey": 12345,
                  "errors":[]
                }
                """
      },
      /////////////////////////////////////////////////////////////////////////////////////////////
      //////////////////////////////////// UsageMetricRecord rPI
      /////////////////////////////////////////////////////////////////////////////////////////////
      // //////////////////////////////////
      /////////////////////////////////////////////////////////////////////////////////////////////
      {
        "UsageMetricRecord rPI",
        (Supplier<UsageMetricRecord>)
            () ->
                new UsageMetricRecord()
                    .setIntervalType(IntervalType.ACTIVE)
                    .setEventType(EventType.RPI)
                    .setStartTime(123L)
                    .setEndTime(124L)
                    .setCounterValues(USAGE_METRICS_MSGPACK),
        """
                {
                  "intervalType": "ACTIVE",
                  "eventType": "RPI",
                  "resetTime": -1,
                  "startTime": 123,
                  "endTime": 124,
                  "counterValues": {"tenant1":5},
                  "setValues": {}
                }
                """
      },
      /////////////////////////////////////////////////////////////////////////////////////////////
      //////////////////////////////////// UsageMetricRecord eDI
      /////////////////////////////////////////////////////////////////////////////////////////////
      // //////////////////////////////////
      /////////////////////////////////////////////////////////////////////////////////////////////
      {
        "UsageMetricRecord eDI",
        (Supplier<UsageMetricRecord>)
            () ->
                new UsageMetricRecord()
                    .setIntervalType(IntervalType.ACTIVE)
                    .setEventType(EventType.EDI)
                    .setStartTime(123L)
                    .setEndTime(124L)
                    .setCounterValues(USAGE_METRICS_MSGPACK),
        """
                {
                  "intervalType": "ACTIVE",
                  "eventType": "EDI",
                  "resetTime": -1,
                  "startTime": 123,
                  "endTime": 124,
                  "counterValues": {"tenant1":5},
                  "setValues": {}
                }
                """
      },
      /////////////////////////////////////////////////////////////////////////////////////////////
      ///////////////////////////////// Empty UsageMetricRecord
      /////////////////////////////////////////////////////////////////////////////////////////////
      // ///////////////////////////////////
      /////////////////////////////////////////////////////////////////////////////////////////////
      {
        "Empty UsageMetricRecord",
        (Supplier<UsageMetricRecord>) UsageMetricRecord::new,
        """
                {
                  "intervalType": "ACTIVE",
                  "eventType": "NONE",
                  "resetTime": -1,
                  "startTime": -1,
                  "endTime": -1,
                  "counterValues": {},
                  "setValues": {}
                }
                """
      },
      /////////////////////////////////////////////////////////////////////////////////////////////
      ///////////////////////////////////// MultiInstanceRecord
      /////////////////////////////////////////////////////////////////////////////////////////////
      // ///////////////////////////////////
      /////////////////////////////////////////////////////////////////////////////////////////////
      {
        "MultiInstanceRecord",
        (Supplier<MultiInstanceRecord>)
            () ->
                new MultiInstanceRecord()
                    .setInputCollection(
                        List.of(
                            new UnsafeBuffer(MsgPackConverter.convertToMsgPack("1")),
                            new UnsafeBuffer(MsgPackConverter.convertToMsgPack("2")),
                            new UnsafeBuffer(MsgPackConverter.convertToMsgPack("3")))),
        """
                {
                  "inputCollection": ["1", "2", "3"]
                }
                """
      },
      /////////////////////////////////////////////////////////////////////////////////////////////
      //////////////////////////////// Empty MultiInstanceRecord
      /////////////////////////////////////////////////////////////////////////////////////////////
      // //////////////////////////////////
      /////////////////////////////////////////////////////////////////////////////////////////////
      {
        "Empty MultiInstanceRecord",
        (Supplier<MultiInstanceRecord>) MultiInstanceRecord::new,
        """
                {
                  "inputCollection": []
                }
                """
      },
      ////////////////////////////////////////////////////////////////////////////////////////////
      ////////////////////////////////// RuntimeInstructionRecord ////////////////////////////////
      ////////////////////////////////////////////////////////////////////////////////////////////
      {
        "RuntimeInstructionRecord",
        (Supplier<RuntimeInstructionRecord>)
            () ->
                new RuntimeInstructionRecord()
                    .setProcessInstanceKey(12345L)
                    .setProcessDefinitionKey(6L)
                    .setTenantId("tenant_1")
                    .setElementId("element_1"),
        """
      {
        "tenantId": "tenant_1",
        "elementId": "element_1",
        "processInstanceKey": 12345,
        "processDefinitionKey": 6
      }
      """
      },
      ////////////////////////////////////////////////////////////////////////////////////////////
      ///////////////////////////////// Empty RuntimeInstructionRecord ///////////////////////////
      ////////////////////////////////////////////////////////////////////////////////////////////
      {
        "Empty RuntimeInstructionRecord",
        (Supplier<RuntimeInstructionRecord>) RuntimeInstructionRecord::new,
        """
      {
        "tenantId": "",
        "elementId": "",
        "processInstanceKey": -1,
        "processDefinitionKey": -1
      }
      """
      },
      /////////////////////////////////////////////////////////////////////////////////////////////
      //////////////////////////////////// HistoryDeletionRecord //////////////////////////////////
      /////////////////////////////////////////////////////////////////////////////////////////////
      {
        "HistoryDeletionRecord",
        (Supplier<HistoryDeletionRecord>)
            () ->
                new HistoryDeletionRecord()
                    .setResourceKey(1L)
                    .setResourceType(HistoryDeletionType.PROCESS_INSTANCE)
                    .setProcessId("processId")
                    .setTenantId("tenantId")
                    .setDecisionDefinitionId("decisionDefinitionId"),
        """
      {
        "resourceKey": 1,
        "resourceType": "PROCESS_INSTANCE",
        "processId": "processId",
        "tenantId": "tenantId",
        "decisionDefinitionId": "decisionDefinitionId"
      }
      """
      },
      /////////////////////////////////////////////////////////////////////////////////////////////
      ///////////////////////////////// Empty HistoryDeletionRecord ///////////////////////////////
      /////////////////////////////////////////////////////////////////////////////////////////////
      {
        "Empty HistoryDeletionRecord",
        (Supplier<HistoryDeletionRecord>)
            () ->
                new HistoryDeletionRecord()
                    .setResourceKey(1L)
                    .setResourceType(HistoryDeletionType.PROCESS_INSTANCE),
        """
      {
        "resourceKey": 1,
        "resourceType": "PROCESS_INSTANCE",
        "processId": "",
        "tenantId": "<default>",
        "decisionDefinitionId": ""
      }
      """
      },
      /////////////////////////////////////////////////////////////////////////////////////////////
      ////////////////////////////// ConditionalSubscriptionRecord
      /////////////////////////////////////////////////////////////////////////////////////////////
      // ///////////////////////////////
      /////////////////////////////////////////////////////////////////////////////////////////////
      {
        "ConditionalSubscriptionRecord",
        (Supplier<UnifiedRecordValue>)
            () ->
                new ConditionalSubscriptionRecord()
                    .setTenantId("tenant-1")
                    .setProcessInstanceKey(123L)
                    .setProcessDefinitionKey(456L)
                    .setBpmnProcessId(BufferUtil.wrapString("process-1"))
                    .setScopeKey(789L)
                    .setElementInstanceKey(111L)
                    .setCatchEventId(BufferUtil.wrapString("catchEvent"))
                    .setCondition(BufferUtil.wrapString("=x > 5"))
                    .setVariableNames(List.of("x", "y"))
                    .setVariableEvents(List.of("CREATED", "UPDATED"))
                    .setInterrupting(true),
        """
                {
                  "tenantId":"tenant-1",
                  "scopeKey":789,
                  "condition":"=x > 5",
                  "interrupting":true,
                  "processInstanceKey":123,
                  "elementInstanceKey":111,
                  "catchEventId":"catchEvent",
                  "variableNames":["x","y"],
                  "variableEvents":["CREATED","UPDATED"],
                  "bpmnProcessId":"process-1",
                  "processDefinitionKey":456
                }
                """
      },

      /////////////////////////////////////////////////////////////////////////////////////////////
      ////////////////////////////// Empty ConditionalSubscriptionRecord
      /////////////////////////////////////////////////////////////////////////////////////////////
      // /////////////////////////
      /////////////////////////////////////////////////////////////////////////////////////////////
      {
        "Empty ConditionalSubscriptionRecord",
        (Supplier<UnifiedRecordValue>) ConditionalSubscriptionRecord::new,
        """
                {
                  "tenantId":"<default>",
                  "scopeKey":-1,
                  "condition":"",
                  "interrupting":true,
                  "processInstanceKey":-1,
                  "elementInstanceKey":-1,
                  "catchEventId":"",
                  "variableNames":[],
                  "variableEvents":[],
                  "bpmnProcessId":"",
                  "processDefinitionKey":-1
                }
                """
      },
      /////////////////////////////////////////////////////////////////////////////////////////////
      ////////////////////////////// ConditionalEvaluationRecord
      /////////////////////////////////////////////////////////////////////////////////////////////
      // ///////////////////////////////
      /////////////////////////////////////////////////////////////////////////////////////////////
      {
        "ConditionalEvaluationRecord",
        (Supplier<UnifiedRecordValue>)
            () ->
                new ConditionalEvaluationRecord()
                    .setTenantId("tenant-1")
                    .setProcessDefinitionKey(456L)
                    .setVariables(VARIABLES_MSGPACK)
                    .addStartedProcessInstance(123L, 789L)
                    .addStartedProcessInstance(456L, 999L),
        """
                {
                  "processDefinitionKey": 456,
                  "startedProcessInstances": [
                    {
                      "processDefinitionKey": 123,
                      "processInstanceKey": 789
                    },
                    {
                      "processDefinitionKey": 456,
                      "processInstanceKey": 999
                    }
                  ],
                  "tenantId": "tenant-1",
                  "variables": {
                    "foo": "bar"
                  }
                }
                """
      },

      /////////////////////////////////////////////////////////////////////////////////////////////
      ////////////////////////////// Empty ConditionalEvaluationRecord
      /////////////////////////////////////////////////////////////////////////////////////////////
      // /////////////////////////
      /////////////////////////////////////////////////////////////////////////////////////////////
      {
        "Empty ConditionalEvaluationRecord",
        (Supplier<UnifiedRecordValue>) ConditionalEvaluationRecord::new,
        """
                {
                  "processDefinitionKey": -1,
                  "startedProcessInstances": [],
                  "tenantId": "<default>",
                  "variables": {}
                }
                """
      },
      //////////////////////////////////// JobMetricsBatchRecord /////////////////////////////
      /////////////////////////////////////////////////////////////////////////////////////////////
      {
        "JobMetricsBatchRecord",
        (Supplier<JobMetricsBatchRecord>)
            () ->
                new JobMetricsBatchRecord()
                    .setBatchStartTime(1000L)
                    .setBatchEndTime(2000L)
                    .setRecordSizeLimitExceeded(false)
                    .setEncodedStrings(List.of("jobType1", "tenant1", "worker1"))
                    .setJobMetrics(
                        List.of(
                            new JobMetrics()
                                .setJobTypeIndex(0)
                                .setTenantIdIndex(1)
                                .setWorkerNameIndex(2)
                                .setStatusMetrics(
                                    List.of(
                                        new StatusMetrics().setCount(1).setLastUpdatedAt(1000))))),
        """
      {
        "jobMetrics": [
          {
            "jobTypeIndex": 0,
            "tenantIdIndex": 1,
            "workerNameIndex": 2,
            "statusMetrics": [
              {
                "count": 1,
                "lastUpdatedAt": 1000,
                "empty": false,
                "encodedLength": 25
              }
            ],
            "empty": false,
            "encodedLength": 87
          }
        ],
        "recordSizeLimitExceeded": false,
        "batchStartTime": 1000,
        "batchEndTime": 2000,
        "encodedStrings": [
          "jobType1",
          "tenant1",
          "worker1"
        ]
      }
      """
      },
      {
        "Empty JobMetricsBatchRecord",
        (Supplier<JobMetricsBatchRecord>) JobMetricsBatchRecord::new,
        """
      {
        "batchStartTime": -1,
        "batchEndTime": -1,
        "recordSizeLimitExceeded": false,
        "encodedStrings": [],
        "jobMetrics": []
      }
      """
      },
      //////////////////////////////////// GlobalListenerBatchRecord /////////////////////////////
      /////////////////////////////////////////////////////////////////////////////////////////////
      {
        "GlobalListenerBatchRecord command",
        (Supplier<GlobalListenerBatchRecord>)
            () ->
                new GlobalListenerBatchRecord()
                    .addListener(
                        new GlobalListenerRecord()
                            .setId("listener1")
                            .setType("global1")
                            .setEventTypes(List.of("creating", "assigning"))
                            .setRetries(5))
                    .addListener(
                        new GlobalListenerRecord()
                            .setId("listener2")
                            .setType("global2")
                            .setEventTypes(List.of("all"))
                            .setRetries(3)
                            .setAfterNonGlobal(true)
                            .setPriority(10)),
        """
      {
        "globalListenerBatchKey": -1,
        "listeners": [
          {
            "globalListenerKey": -1,
            "id": "listener1",
            "type": "global1",
            "retries": 5,
            "eventTypes": ["creating", "assigning"],
            "afterNonGlobal": false,
            "priority": 50,
            "source": "CONFIGURATION",
            "listenerType": "USER_TASK",
            "configKey": -1
          },
          {
            "globalListenerKey": -1,
            "id": "listener2",
            "type": "global2",
            "retries": 3,
            "eventTypes": ["all"],
            "afterNonGlobal": true,
            "priority": 10,
            "source": "CONFIGURATION",
            "listenerType": "USER_TASK",
            "configKey": -1
          }
         ],
        "createdListenerKeys": [
        ],
        "updatedListenerKeys": [
        ],
        "deletedListenerKeys": [
        ]
      }
      """
      },
      {
        "Distributed GlobalListenerBatchRecord command",
        (Supplier<GlobalListenerBatchRecord>)
            () -> {
              final var existingListener1 =
                  new GlobalListenerRecord()
                      .setGlobalListenerKey(123L)
                      .setId("listener1")
                      .setType("global1")
                      .setEventTypes(List.of("creating", "assigning"))
                      .setRetries(5);
              final var existingListener2 =
                  new GlobalListenerRecord()
                      .setGlobalListenerKey(124L)
                      .setId("listener2")
                      .setType("global2")
                      .setEventTypes(List.of("creating", "assigning"))
                      .setRetries(5);
              final var newListener =
                  new GlobalListenerRecord()
                      .setGlobalListenerKey(125L)
                      .setId("listener3")
                      .setType("global3")
                      .setEventTypes(List.of("all"))
                      .setRetries(3)
                      .setAfterNonGlobal(true)
                      .setPriority(10);
              return new GlobalListenerBatchRecord()
                  .setGlobalListenerBatchKey(1)
                  .addListener(existingListener1)
                  .addListener(newListener)
                  .addListener(existingListener2)
                  .addCreatedListener(newListener)
                  .addUpdatedListener(existingListener1)
                  .addDeletedListener(existingListener2);
            },
        """
      {
        "globalListenerBatchKey": 1,
        "listeners": [
          {
            "globalListenerKey": 123,
            "id": "listener1",
            "type": "global1",
            "retries": 5,
            "eventTypes": ["creating", "assigning"],
            "afterNonGlobal": false,
            "priority": 50,
            "source": "CONFIGURATION",
            "listenerType": "USER_TASK",
            "configKey": -1
          },
          {
            "globalListenerKey": 125,
            "id": "listener3",
            "type": "global3",
            "retries": 3,
            "eventTypes": ["all"],
            "afterNonGlobal": true,
            "priority": 10,
            "source": "CONFIGURATION",
            "listenerType": "USER_TASK",
            "configKey": -1
          },
          {
            "globalListenerKey": 124,
            "id": "listener2",
            "type": "global2",
            "retries": 5,
            "eventTypes": ["creating", "assigning"],
            "afterNonGlobal": false,
            "priority": 50,
            "source": "CONFIGURATION",
            "listenerType": "USER_TASK",
            "configKey": -1
          }
        ],
        "createdListenerKeys": [
          125
        ],
        "updatedListenerKeys": [
          123
        ],
        "deletedListenerKeys": [
          124
        ]
      }
      """
      },
      {
        "Empty GlobalListenerBatchRecord",
        (Supplier<GlobalListenerBatchRecord>) GlobalListenerBatchRecord::new,
        """
      {
        "globalListenerBatchKey": -1,
        "listeners": [],
        "createdListenerKeys": [],
        "updatedListenerKeys": [],
        "deletedListenerKeys": []
      }
      """
      },
      //////////////////////////////////// GlobalListenerRecord ///////////////////////////////////
      /////////////////////////////////////////////////////////////////////////////////////////////
      {
        "GlobalListenerRecord",
        (Supplier<GlobalListenerRecord>)
            () ->
                new GlobalListenerRecord()
                    .setGlobalListenerKey(123L)
                    .setId("my-listener")
                    .setType("global1")
                    .setEventTypes(List.of("creating", "assigning"))
                    .setRetries(5)
                    .setAfterNonGlobal(true)
                    .setPriority(10)
                    .setSource(GlobalListenerSource.API)
                    .setConfigKey(124L),
        """
    {
      "globalListenerKey": 123,
      "id": "my-listener",
      "type": "global1",
      "retries": 5,
      "eventTypes": ["creating", "assigning"],
      "afterNonGlobal": true,
      "priority": 10,
      "source": "API",
      "listenerType": "USER_TASK",
      "configKey": 124
    }
    """
      },
      {
        "Empty GlobalListenerRecord",
        (Supplier<GlobalListenerRecord>) GlobalListenerRecord::new,
        """
    {
      "globalListenerKey": -1,
      "id": "",
      "type": "",
      "retries": 3,
      "eventTypes": [],
      "afterNonGlobal": false,
      "priority": 50,
      "source": "CONFIGURATION",
      "listenerType": "USER_TASK",
      "configKey": -1
    }
    """
      }
    };
  }

  private static String errorRecordAsJson(final long processInstanceKey) {
    final Map<String, Object> params = new HashMap<>();
    params.put("exceptionMessage", "test");
    params.put("processInstanceKey", processInstanceKey);
    params.put("errorEventPosition", 123);
    params.put("stacktrace", STACK_TRACE);

    try {
      return new ObjectMapper().writeValueAsString(params);
    } catch (final JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }

  private static DirectBuffer toMessagePack(final String json) {
    final byte[] messagePack = MsgPackConverter.convertToMsgPack(json);
    return BufferUtil.wrapArray(messagePack);
  }
}
