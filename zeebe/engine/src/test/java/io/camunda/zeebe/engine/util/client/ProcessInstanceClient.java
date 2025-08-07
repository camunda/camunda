/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.util.client;

import static io.camunda.zeebe.util.buffer.BufferUtil.wrapString;

import io.camunda.zeebe.auth.Authorization;
import io.camunda.zeebe.engine.util.AuthorizationUtil;
import io.camunda.zeebe.msgpack.property.ArrayProperty;
import io.camunda.zeebe.msgpack.value.StringValue;
import io.camunda.zeebe.protocol.impl.encoding.AuthInfo;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceCreationRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceCreationRuntimeInstruction;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceCreationStartInstruction;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceMigrationMappingInstruction;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceMigrationRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceModificationActivateInstruction;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceModificationRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceModificationTerminateInstruction;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceModificationVariableInstruction;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.ErrorIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceCreationIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceMigrationIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceModificationIntent;
import io.camunda.zeebe.protocol.record.value.ErrorRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceCreationRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceMigrationRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceModificationRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import io.camunda.zeebe.protocol.record.value.RuntimeInstructionType;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.test.util.MsgPackUtil;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.Function;
import org.agrona.DirectBuffer;

public final class ProcessInstanceClient {

  private final CommandWriter writer;

  public ProcessInstanceClient(final CommandWriter writer) {
    this.writer = writer;
  }

  public ProcessInstanceCreationClient ofBpmnProcessId(final String bpmnProcessId) {
    return new ProcessInstanceCreationClient(writer, bpmnProcessId);
  }

  public ExistingInstanceClient withInstanceKey(final long processInstanceKey) {
    return new ExistingInstanceClient(writer, processInstanceKey);
  }

  public static class ProcessInstanceCreationClient {

    private static final Function<Long, Record<ProcessInstanceCreationRecordValue>>
        SUCCESS_EXPECTATION =
            (position) ->
                RecordingExporter.processInstanceCreationRecords()
                    .withIntent(ProcessInstanceCreationIntent.CREATED)
                    .withSourceRecordPosition(position)
                    .getFirst();

    private static final Function<Long, Record<ProcessInstanceCreationRecordValue>>
        REJECTION_EXPECTATION =
            (position) ->
                RecordingExporter.processInstanceCreationRecords()
                    .onlyCommandRejections()
                    .withIntent(ProcessInstanceCreationIntent.CREATE)
                    .withSourceRecordPosition(position)
                    .getFirst();
    private static final int DEFAULT_PARTITION = 1;

    private final CommandWriter writer;
    private final ProcessInstanceCreationRecord processInstanceCreationRecord;
    private int partition = DEFAULT_PARTITION;

    private Function<Long, Record<ProcessInstanceCreationRecordValue>> expectation =
        SUCCESS_EXPECTATION;

    public ProcessInstanceCreationClient(final CommandWriter writer, final String bpmnProcessId) {
      this.writer = writer;
      processInstanceCreationRecord = new ProcessInstanceCreationRecord();
      processInstanceCreationRecord.setBpmnProcessId(bpmnProcessId);
    }

    public ProcessInstanceCreationClient onPartition(final int partition) {
      this.partition = partition;
      return this;
    }

    public ProcessInstanceCreationClient withVersion(final int version) {
      processInstanceCreationRecord.setVersion(version);
      return this;
    }

    public ProcessInstanceCreationClient withTenantId(final String tenantId) {
      processInstanceCreationRecord.setTenantId(tenantId);
      return this;
    }

    public ProcessInstanceCreationClient withVariables(final Map<String, Object> variables) {
      processInstanceCreationRecord.setVariables(MsgPackUtil.asMsgPack(variables));
      return this;
    }

    public ProcessInstanceCreationClient withVariables(final String variables) {
      processInstanceCreationRecord.setVariables(MsgPackUtil.asMsgPack(variables));
      return this;
    }

    public ProcessInstanceCreationClient withVariable(final String key, final Object value) {
      processInstanceCreationRecord.setVariables(MsgPackUtil.asMsgPack(key, value));
      return this;
    }

    public ProcessInstanceCreationClient withStartInstruction(final String elementId) {
      final var instruction = new ProcessInstanceCreationStartInstruction().setElementId(elementId);
      processInstanceCreationRecord.addStartInstruction(instruction);
      return this;
    }

    public ProcessInstanceCreationClient withTags(final String... tags) {
      processInstanceCreationRecord.setTags(Set.of(tags));
      return this;
    }

    public ProcessInstanceCreationClient withRuntimeTerminateInstruction(
        final String afterElementId) {
      final var instruction =
          new ProcessInstanceCreationRuntimeInstruction()
              .setType(RuntimeInstructionType.TERMINATE_PROCESS_INSTANCE)
              .setAfterElementId(afterElementId);
      processInstanceCreationRecord.addRuntimeInstruction(instruction);
      return this;
    }

    public ProcessInstanceCreationWithResultClient withResult() {
      return new ProcessInstanceCreationWithResultClient(writer, processInstanceCreationRecord);
    }

    public long create() {
      return create(
          AuthorizationUtil.getAuthInfoWithClaim(Authorization.AUTHORIZED_ANONYMOUS_USER, true));
    }

    public long create(final AuthInfo authorizations) {
      final long position =
          writer.writeCommandOnPartition(
              partition,
              r ->
                  r.intent(ProcessInstanceCreationIntent.CREATE)
                      .event(processInstanceCreationRecord)
                      .authorizations(authorizations)
                      .requestId(new Random().nextLong())
                      .requestStreamId(new Random().nextInt()));

      final var resultingRecord = expectation.apply(position);
      return resultingRecord.getValue().getProcessInstanceKey();
    }

    public long create(final String username) {
      return create(AuthorizationUtil.getAuthInfo(username));
    }

    public ProcessInstanceCreationClient expectRejection() {
      expectation = REJECTION_EXPECTATION;
      return this;
    }
  }

  public static class ProcessInstanceCreationWithResultClient {
    private final CommandWriter writer;
    private final ProcessInstanceCreationRecord record;
    private long requestId = 1L;
    private int requestStreamId = 1;

    public ProcessInstanceCreationWithResultClient(
        final CommandWriter writer, final ProcessInstanceCreationRecord record) {
      this.writer = writer;
      this.record = record;
    }

    public ProcessInstanceCreationWithResultClient withFetchVariables(
        final Set<String> fetchVariables) {
      final ArrayProperty<StringValue> variablesToCollect = record.fetchVariables();
      fetchVariables.forEach(variable -> variablesToCollect.add().wrap(wrapString(variable)));
      return this;
    }

    public ProcessInstanceCreationWithResultClient withRequestId(final long requestId) {
      this.requestId = requestId;
      return this;
    }

    public ProcessInstanceCreationWithResultClient withRequestStreamId(final int requestStreamId) {
      this.requestStreamId = requestStreamId;
      return this;
    }

    public long create() {
      final long position =
          writer.writeCommand(
              requestStreamId,
              requestId,
              ProcessInstanceCreationIntent.CREATE_WITH_AWAITING_RESULT,
              record);

      return RecordingExporter.processInstanceCreationRecords()
          .withIntent(ProcessInstanceCreationIntent.CREATED)
          .withSourceRecordPosition(position)
          .getFirst()
          .getValue()
          .getProcessInstanceKey();
    }

    public long create(final String username) {
      final long position =
          writer.writeCommand(
              requestStreamId,
              requestId,
              ProcessInstanceCreationIntent.CREATE_WITH_AWAITING_RESULT,
              record,
              username);

      return RecordingExporter.processInstanceCreationRecords()
          .withIntent(ProcessInstanceCreationIntent.CREATED)
          .withSourceRecordPosition(position)
          .getFirst()
          .getValue()
          .getProcessInstanceKey();
    }

    public void asyncCreate() {
      writer.writeCommand(
          requestStreamId,
          requestId,
          ProcessInstanceCreationIntent.CREATE_WITH_AWAITING_RESULT,
          record);
    }

    public void asyncCreate(final String username) {
      writer.writeCommand(
          requestStreamId,
          requestId,
          ProcessInstanceCreationIntent.CREATE_WITH_AWAITING_RESULT,
          record,
          username);
    }
  }

  public static class ExistingInstanceClient {

    public static final Function<Long, Record<ProcessInstanceRecordValue>> SUCCESS_EXPECTATION =
        (processInstanceKey) ->
            RecordingExporter.processInstanceRecords()
                .withRecordKey(processInstanceKey)
                .withIntent(ProcessInstanceIntent.ELEMENT_TERMINATED)
                .withProcessInstanceKey(processInstanceKey)
                .getFirst();

    public static final Function<Long, Record<ProcessInstanceRecordValue>> TERMINATING_EXPECTATION =
        (processInstanceKey) ->
            RecordingExporter.processInstanceRecords()
                .withRecordKey(processInstanceKey)
                .withIntent(ProcessInstanceIntent.ELEMENT_TERMINATING)
                .withProcessInstanceKey(processInstanceKey)
                .getFirst();

    public static final Function<Long, Record<ProcessInstanceRecordValue>> REJECTION_EXPECTATION =
        (processInstanceKey) ->
            RecordingExporter.processInstanceRecords()
                .onlyCommandRejections()
                .withIntent(ProcessInstanceIntent.CANCEL)
                .withRecordKey(processInstanceKey)
                .withProcessInstanceKey(processInstanceKey)
                .getFirst();

    public static final Function<Long, Record<ErrorRecordValue>> ERROR_EXPECTATION =
        (processInstanceKey) ->
            RecordingExporter.errorRecords().withIntent(ErrorIntent.CREATED).getFirst();

    private static final int DEFAULT_PARTITION = -1;
    private final CommandWriter writer;
    private final long processInstanceKey;

    private String[] authorizedTenants;
    private int partition = DEFAULT_PARTITION;
    private Function<Long, Record<ProcessInstanceRecordValue>> expectation = SUCCESS_EXPECTATION;

    public ExistingInstanceClient(final CommandWriter writer, final long processInstanceKey) {
      this.writer = writer;
      this.processInstanceKey = processInstanceKey;
      authorizedTenants = new String[] {TenantOwned.DEFAULT_TENANT_IDENTIFIER};
    }

    public ExistingInstanceClient onPartition(final int partition) {
      this.partition = partition;
      return this;
    }

    public ExistingInstanceClient expectRejection() {
      expectation = REJECTION_EXPECTATION;
      return this;
    }

    public ExistingInstanceClient expectTerminating() {
      expectation = TERMINATING_EXPECTATION;
      return this;
    }

    public Record<ProcessInstanceRecordValue> cancel() {
      writeCancelCommand();
      return expectation.apply(processInstanceKey);
    }

    public Record<ProcessInstanceRecordValue> cancel(final String username) {
      writeCancelCommandWithUserKey(username);
      return expectation.apply(processInstanceKey);
    }

    public Record<ErrorRecordValue> cancelWithError() {
      writeCancelCommand();
      return ERROR_EXPECTATION.apply(processInstanceKey);
    }

    private void writeCancelCommand() {
      if (partition == DEFAULT_PARTITION) {
        partition =
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .getFirst()
                .getPartitionId();
      }

      writer.writeCommandOnPartition(
          partition,
          processInstanceKey,
          ProcessInstanceIntent.CANCEL,
          new ProcessInstanceRecord().setProcessInstanceKey(processInstanceKey),
          authorizedTenants);
    }

    private void writeCancelCommandWithUserKey(final String username) {
      if (partition == DEFAULT_PARTITION) {
        partition =
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .getFirst()
                .getPartitionId();
      }

      writer.writeCommandOnPartition(
          partition,
          processInstanceKey,
          ProcessInstanceIntent.CANCEL,
          username,
          new ProcessInstanceRecord().setProcessInstanceKey(processInstanceKey),
          authorizedTenants);
    }

    public ExistingInstanceClient forAuthorizedTenants(final String... authorizedTenants) {
      this.authorizedTenants = authorizedTenants;
      return this;
    }

    public ProcessInstanceModificationClient modification() {
      return new ProcessInstanceModificationClient(writer, processInstanceKey, authorizedTenants);
    }

    public ProcessInstanceMigrationClient migration() {
      return new ProcessInstanceMigrationClient(writer, processInstanceKey, authorizedTenants);
    }
  }

  public static class ProcessInstanceModificationClient {

    private static final Function<Long, Record<ProcessInstanceModificationRecordValue>>
        SUCCESS_EXPECTATION =
            (position) ->
                RecordingExporter.processInstanceModificationRecords()
                    .withIntent(ProcessInstanceModificationIntent.MODIFIED)
                    .withSourceRecordPosition(position)
                    .getFirst();

    private static final Function<Long, Record<ProcessInstanceModificationRecordValue>>
        REJECTION_EXPECTATION =
            (processInstanceKey) ->
                RecordingExporter.processInstanceModificationRecords()
                    .onlyCommandRejections()
                    .withIntent(ProcessInstanceModificationIntent.MODIFY)
                    .withRecordKey(processInstanceKey)
                    .withProcessInstanceKey(processInstanceKey)
                    .getFirst();

    private Function<Long, Record<ProcessInstanceModificationRecordValue>> expectation =
        SUCCESS_EXPECTATION;

    private final CommandWriter writer;
    private final long processInstanceKey;
    private final ProcessInstanceModificationRecord record;
    private final List<ProcessInstanceModificationActivateInstruction> activateInstructions;
    private String[] authorizedTenants;

    public ProcessInstanceModificationClient(
        final CommandWriter writer,
        final long processInstanceKey,
        final String[] authorizedTenants) {
      this.writer = writer;
      this.processInstanceKey = processInstanceKey;
      this.authorizedTenants = authorizedTenants;
      record = new ProcessInstanceModificationRecord();
      activateInstructions = new ArrayList<>();
    }

    private ProcessInstanceModificationClient(
        final CommandWriter writer,
        final long processInstanceKey,
        final ProcessInstanceModificationRecord record,
        final List<ProcessInstanceModificationActivateInstruction> activateInstructions,
        final String[] authorizedTenants) {
      this.writer = writer;
      this.processInstanceKey = processInstanceKey;
      this.record = record;
      this.activateInstructions = activateInstructions;
      this.authorizedTenants = authorizedTenants;
    }

    /**
     * Add an activate element instruction.
     *
     * @param elementId the id of the element to activate
     * @return this ActivationInstruction builder for chaining
     */
    public ActivationInstructionBuilder activateElement(final String elementId) {
      final var noAncestorScopeKey = -1;
      return activateElement(elementId, noAncestorScopeKey);
    }

    /**
     * Add an activate element instruction with ancestor selection.
     *
     * @param elementId the id of the element to activate
     * @param ancestorScopeKey the key of the ancestor scope that should be used as the (in)direct
     *     flow scope instance of the element to activate; or -1 to disable ancestor selection
     * @return this ActivationInstruction builder for chaining
     */
    public ActivationInstructionBuilder activateElement(
        final String elementId, final long ancestorScopeKey) {
      final var activateInstruction =
          new ProcessInstanceModificationActivateInstruction()
              .setElementId(elementId)
              .setAncestorScopeKey(ancestorScopeKey);
      activateInstructions.add(activateInstruction);
      return new ActivationInstructionBuilder(
          writer,
          processInstanceKey,
          record,
          activateInstructions,
          activateInstruction,
          authorizedTenants);
    }

    public ProcessInstanceModificationClient terminateElement(final long elementInstanceKey) {
      record.addTerminateInstruction(
          new ProcessInstanceModificationTerminateInstruction()
              .setElementInstanceKey(elementInstanceKey));
      return this;
    }

    public ProcessInstanceModificationClient forAuthorizedTenants(
        final String... authorizedTenants) {
      this.authorizedTenants = authorizedTenants;
      return this;
    }

    public ProcessInstanceModificationClient expectRejection() {
      expectation = REJECTION_EXPECTATION;
      return this;
    }

    public Record<ProcessInstanceModificationRecordValue> modify() {
      record.setProcessInstanceKey(processInstanceKey);
      activateInstructions.forEach(record::addActivateInstruction);

      final var position =
          writer.writeCommand(
              processInstanceKey,
              ProcessInstanceModificationIntent.MODIFY,
              record,
              authorizedTenants);

      if (expectation == REJECTION_EXPECTATION) {
        return expectation.apply(processInstanceKey);
      } else {
        return expectation.apply(position);
      }
    }

    public Record<ProcessInstanceModificationRecordValue> modify(final String username) {
      record.setProcessInstanceKey(processInstanceKey);
      activateInstructions.forEach(record::addActivateInstruction);

      final var position =
          writer.writeCommand(
              processInstanceKey,
              ProcessInstanceModificationIntent.MODIFY,
              username,
              record,
              authorizedTenants);

      if (expectation == REJECTION_EXPECTATION) {
        return expectation.apply(processInstanceKey);
      } else {
        return expectation.apply(position);
      }
    }

    public static class ActivationInstructionBuilder extends ProcessInstanceModificationClient {

      private final ProcessInstanceModificationActivateInstruction activateInstruction;

      public ActivationInstructionBuilder(
          final CommandWriter environmentRule,
          final long processInstanceKey,
          final ProcessInstanceModificationRecord record,
          final List<ProcessInstanceModificationActivateInstruction> activateInstructions,
          final ProcessInstanceModificationActivateInstruction activateInstruction,
          final String[] authorizedTenants) {
        super(environmentRule, processInstanceKey, record, activateInstructions, authorizedTenants);
        this.activateInstruction = activateInstruction;
      }

      /**
       * Add global variables to the activate instruction
       *
       * @param variables the variables to set
       * @return this client builder for chaining
       */
      public ActivationInstructionBuilder withGlobalVariables(final String variables) {
        return withGlobalVariables(MsgPackUtil.asMsgPack(variables));
      }

      /**
       * Add global variables to the activate instruction
       *
       * @param variables the variables to set
       * @return this client builder for chaining
       */
      public ActivationInstructionBuilder withGlobalVariables(final Map<String, Object> variables) {
        return withGlobalVariables(MsgPackUtil.asMsgPack(variables));
      }

      /**
       * Add global variables to the activate instruction
       *
       * @param variables the variables to set
       * @return this client builder for chaining
       */
      public ActivationInstructionBuilder withGlobalVariables(final DirectBuffer variables) {
        final var variableInstruction =
            new ProcessInstanceModificationVariableInstruction().setVariables(variables);
        activateInstruction.addVariableInstruction(variableInstruction);
        return this;
      }

      /**
       * Add variables to the activate instruction
       *
       * @param variablesScopeId the element id to use as variable scope for the provided variables
       * @param variables the variables to set locally
       * @return this client builder for chaining
       */
      public ActivationInstructionBuilder withVariables(
          final String variablesScopeId, final String variables) {
        return withVariables(variablesScopeId, MsgPackUtil.asMsgPack(variables));
      }

      /**
       * Add variables to the activate instruction
       *
       * @param variablesScopeId the element id to use as variable scope for the provided variables
       * @param variables the variables to set locally
       * @return this client builder for chaining
       */
      public ActivationInstructionBuilder withVariables(
          final String variablesScopeId, final Map<String, Object> variables) {
        return withVariables(variablesScopeId, MsgPackUtil.asMsgPack(variables));
      }

      /**
       * Add variables to the activate instruction
       *
       * @param variablesScopeId the element id to use as variable scope for the provided variables
       * @param variables the variables to set locally
       * @return this client builder for chaining
       */
      public ActivationInstructionBuilder withVariables(
          final String variablesScopeId, final DirectBuffer variables) {
        final var variableInstruction =
            new ProcessInstanceModificationVariableInstruction()
                .setElementId(variablesScopeId)
                .setVariables(variables);
        activateInstruction.addVariableInstruction(variableInstruction);
        return this;
      }
    }
  }

  public static class ProcessInstanceMigrationClient {

    private static final Function<Long, Record<ProcessInstanceMigrationRecordValue>>
        SUCCESS_EXPECTATION =
            (position) ->
                RecordingExporter.processInstanceMigrationRecords()
                    .withIntent(ProcessInstanceMigrationIntent.MIGRATED)
                    .withSourceRecordPosition(position)
                    .getFirst();

    private static final Function<Long, Record<ProcessInstanceMigrationRecordValue>>
        REJECTION_EXPECTATION =
            (processInstanceKey) ->
                RecordingExporter.processInstanceMigrationRecords()
                    .onlyCommandRejections()
                    .withIntent(ProcessInstanceMigrationIntent.MIGRATE)
                    .withRecordKey(processInstanceKey)
                    .withProcessInstanceKey(processInstanceKey)
                    .getFirst();

    private Function<Long, Record<ProcessInstanceMigrationRecordValue>> expectation =
        SUCCESS_EXPECTATION;

    private final CommandWriter writer;
    private final long processInstanceKey;
    private final ProcessInstanceMigrationRecord record;
    private final List<ProcessInstanceMigrationMappingInstruction> mappingInstructions;
    private String[] authorizedTenants;

    public ProcessInstanceMigrationClient(
        final CommandWriter writer,
        final long processInstanceKey,
        final String[] authorizedTenants) {
      this.writer = writer;
      this.processInstanceKey = processInstanceKey;
      this.authorizedTenants = authorizedTenants;
      record = new ProcessInstanceMigrationRecord();
      mappingInstructions = new ArrayList<>();
    }

    /**
     * Set the target process definition key.
     *
     * @param processDefinitionKey The process definition key of the target process
     * @return this client builder for chaining
     */
    public ProcessInstanceMigrationClient withTargetProcessDefinitionKey(
        final long processDefinitionKey) {
      record.setTargetProcessDefinitionKey(processDefinitionKey);
      return this;
    }

    /**
     * Add a mapping instruction. Can be chained to add multiple instructions.
     *
     * @param sourceElementId The element id of the source element
     * @param targetElementId The element id of the target element
     * @return this client builder for chaining
     */
    public ProcessInstanceMigrationClient addMappingInstruction(
        final String sourceElementId, final String targetElementId) {
      final var mappingInstruction =
          new ProcessInstanceMigrationMappingInstruction()
              .setSourceElementId(sourceElementId)
              .setTargetElementId(targetElementId);
      mappingInstructions.add(mappingInstruction);
      return this;
    }

    public ProcessInstanceMigrationClient forAuthorizedTenants(final String... authorizedTenants) {
      this.authorizedTenants = authorizedTenants;
      return this;
    }

    /**
     * Expect the migration to be rejected. Fails the test if the migration is not rejected.
     *
     * @return this client builder for chaining
     */
    public ProcessInstanceMigrationClient expectRejection() {
      expectation = REJECTION_EXPECTATION;
      return this;
    }

    /**
     * Migrate the process instance. Awaits the defined expectation. Fails the test if the
     * expectation is not met.
     *
     * @return the resulting record of the migration
     */
    public Record<ProcessInstanceMigrationRecordValue> migrate() {
      record.setProcessInstanceKey(processInstanceKey);
      mappingInstructions.forEach(record::addMappingInstruction);

      final var position =
          writer.writeCommand(
              processInstanceKey,
              ProcessInstanceMigrationIntent.MIGRATE,
              record,
              authorizedTenants);

      if (expectation == REJECTION_EXPECTATION) {
        return expectation.apply(processInstanceKey);
      } else {
        return expectation.apply(position);
      }
    }

    public Record<ProcessInstanceMigrationRecordValue> migrate(final String username) {
      record.setProcessInstanceKey(processInstanceKey);
      mappingInstructions.forEach(record::addMappingInstruction);

      final var position =
          writer.writeCommand(
              processInstanceKey,
              ProcessInstanceMigrationIntent.MIGRATE,
              username,
              record,
              authorizedTenants);

      if (expectation == REJECTION_EXPECTATION) {
        return expectation.apply(processInstanceKey);
      } else {
        return expectation.apply(position);
      }
    }
  }
}
