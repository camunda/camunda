/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.transport.commandapi;

import static io.camunda.zeebe.protocol.record.ExecuteCommandRequestDecoder.TEMPLATE_ID;

import io.camunda.zeebe.broker.transport.AsyncApiRequestHandler.RequestReader;
import io.camunda.zeebe.broker.transport.RequestReaderException;
import io.camunda.zeebe.protocol.impl.encoding.AuthInfo;
import io.camunda.zeebe.protocol.impl.record.RecordMetadata;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.impl.record.value.authorization.AuthorizationRecord;
import io.camunda.zeebe.protocol.impl.record.value.authorization.IdentitySetupRecord;
import io.camunda.zeebe.protocol.impl.record.value.authorization.MappingRecord;
import io.camunda.zeebe.protocol.impl.record.value.authorization.RoleRecord;
import io.camunda.zeebe.protocol.impl.record.value.batchoperation.BatchOperationCreationRecord;
import io.camunda.zeebe.protocol.impl.record.value.batchoperation.BatchOperationExecutionRecord;
import io.camunda.zeebe.protocol.impl.record.value.clock.ClockRecord;
import io.camunda.zeebe.protocol.impl.record.value.compensation.CompensationSubscriptionRecord;
import io.camunda.zeebe.protocol.impl.record.value.decision.DecisionEvaluationRecord;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DeploymentRecord;
import io.camunda.zeebe.protocol.impl.record.value.distribution.CommandDistributionRecord;
import io.camunda.zeebe.protocol.impl.record.value.group.GroupRecord;
import io.camunda.zeebe.protocol.impl.record.value.incident.IncidentRecord;
import io.camunda.zeebe.protocol.impl.record.value.job.JobBatchRecord;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageBatchRecord;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageCorrelationRecord;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceBatchRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceCreationRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceMigrationRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceModificationRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.impl.record.value.resource.ResourceDeletionRecord;
import io.camunda.zeebe.protocol.impl.record.value.scaling.ScaleRecord;
import io.camunda.zeebe.protocol.impl.record.value.signal.SignalRecord;
import io.camunda.zeebe.protocol.impl.record.value.tenant.TenantRecord;
import io.camunda.zeebe.protocol.impl.record.value.user.UserRecord;
import io.camunda.zeebe.protocol.impl.record.value.usertask.UserTaskRecord;
import io.camunda.zeebe.protocol.impl.record.value.variable.VariableDocumentRecord;
import io.camunda.zeebe.protocol.record.ExecuteCommandRequestDecoder;
import io.camunda.zeebe.protocol.record.MessageHeaderDecoder;
import io.camunda.zeebe.protocol.record.ValueType;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Supplier;
import org.agrona.DirectBuffer;

public class CommandApiRequestReader implements RequestReader<ExecuteCommandRequestDecoder> {

  static final Map<ValueType, Supplier<UnifiedRecordValue>> RECORDS_BY_TYPE =
      new EnumMap<>(ValueType.class);

  static {
    RECORDS_BY_TYPE.put(ValueType.DEPLOYMENT, DeploymentRecord::new);
    RECORDS_BY_TYPE.put(ValueType.JOB, JobRecord::new);
    RECORDS_BY_TYPE.put(ValueType.PROCESS_INSTANCE, ProcessInstanceRecord::new);
    RECORDS_BY_TYPE.put(ValueType.MESSAGE, MessageRecord::new);
    RECORDS_BY_TYPE.put(ValueType.MESSAGE_BATCH, MessageBatchRecord::new);
    RECORDS_BY_TYPE.put(ValueType.JOB_BATCH, JobBatchRecord::new);
    RECORDS_BY_TYPE.put(ValueType.INCIDENT, IncidentRecord::new);
    RECORDS_BY_TYPE.put(ValueType.VARIABLE_DOCUMENT, VariableDocumentRecord::new);
    RECORDS_BY_TYPE.put(ValueType.PROCESS_INSTANCE_CREATION, ProcessInstanceCreationRecord::new);
    RECORDS_BY_TYPE.put(ValueType.DECISION_EVALUATION, DecisionEvaluationRecord::new);
    RECORDS_BY_TYPE.put(
        ValueType.PROCESS_INSTANCE_MODIFICATION, ProcessInstanceModificationRecord::new);
    RECORDS_BY_TYPE.put(ValueType.SIGNAL, SignalRecord::new);
    RECORDS_BY_TYPE.put(ValueType.COMMAND_DISTRIBUTION, CommandDistributionRecord::new);
    RECORDS_BY_TYPE.put(ValueType.PROCESS_INSTANCE_BATCH, ProcessInstanceBatchRecord::new);
    RECORDS_BY_TYPE.put(ValueType.RESOURCE_DELETION, ResourceDeletionRecord::new);
    RECORDS_BY_TYPE.put(ValueType.USER_TASK, UserTaskRecord::new);
    RECORDS_BY_TYPE.put(ValueType.PROCESS_INSTANCE_MIGRATION, ProcessInstanceMigrationRecord::new);
    RECORDS_BY_TYPE.put(ValueType.COMPENSATION_SUBSCRIPTION, CompensationSubscriptionRecord::new);
    RECORDS_BY_TYPE.put(ValueType.MESSAGE_CORRELATION, MessageCorrelationRecord::new);
    RECORDS_BY_TYPE.put(ValueType.USER, UserRecord::new);
    RECORDS_BY_TYPE.put(ValueType.CLOCK, ClockRecord::new);
    RECORDS_BY_TYPE.put(ValueType.AUTHORIZATION, AuthorizationRecord::new);
    RECORDS_BY_TYPE.put(ValueType.ROLE, RoleRecord::new);
    RECORDS_BY_TYPE.put(ValueType.TENANT, TenantRecord::new);
    RECORDS_BY_TYPE.put(ValueType.SCALE, ScaleRecord::new);
    RECORDS_BY_TYPE.put(ValueType.GROUP, GroupRecord::new);
    RECORDS_BY_TYPE.put(ValueType.MAPPING, MappingRecord::new);
    RECORDS_BY_TYPE.put(ValueType.IDENTITY_SETUP, IdentitySetupRecord::new);
    RECORDS_BY_TYPE.put(ValueType.BATCH_OPERATION, BatchOperationCreationRecord::new);
    RECORDS_BY_TYPE.put(ValueType.BATCH_OPERATION_EXECUTION, BatchOperationExecutionRecord::new);
  }

  private UnifiedRecordValue value;
  private final RecordMetadata metadata = new RecordMetadata();
  private final AuthInfo authInfo = new AuthInfo();
  private final MessageHeaderDecoder messageHeaderDecoder = new MessageHeaderDecoder();
  private final ExecuteCommandRequestDecoder commandRequestDecoder =
      new ExecuteCommandRequestDecoder();

  @Override
  public void reset() {
    if (value != null) {
      value.reset();
    }
    metadata.reset();
  }

  @Override
  public ExecuteCommandRequestDecoder getMessageDecoder() {
    return commandRequestDecoder;
  }

  @Override
  public void wrap(final DirectBuffer buffer, final int offset, final int length) {
    messageHeaderDecoder.wrap(buffer, offset);

    final int templateId = messageHeaderDecoder.templateId();
    if (TEMPLATE_ID != templateId) {
      throw new RequestReaderException.InvalidTemplateException(
          messageHeaderDecoder.templateId(), templateId);
    }

    commandRequestDecoder.wrap(
        buffer,
        offset + MessageHeaderDecoder.ENCODED_LENGTH,
        messageHeaderDecoder.blockLength(),
        messageHeaderDecoder.version());

    metadata.protocolVersion(messageHeaderDecoder.version());
    final var recordSupplier = RECORDS_BY_TYPE.get(commandRequestDecoder.valueType());
    if (recordSupplier != null) {
      final int valueOffset =
          commandRequestDecoder.limit() + ExecuteCommandRequestDecoder.valueHeaderLength();
      final int valueLength = commandRequestDecoder.valueLength();
      value = recordSupplier.get();
      value.wrap(buffer, valueOffset, valueLength);
    }

    commandRequestDecoder.skipValue();
    if (commandRequestDecoder.limit() < buffer.capacity()) {
      final int authOffset =
          commandRequestDecoder.limit() + ExecuteCommandRequestDecoder.authorizationHeaderLength();
      authInfo.wrap(buffer, authOffset, commandRequestDecoder.authorizationLength());
      metadata.authorization(authInfo);
    }
  }

  public UnifiedRecordValue value() {
    return value;
  }

  public RecordMetadata metadata() {
    return metadata;
  }
}
