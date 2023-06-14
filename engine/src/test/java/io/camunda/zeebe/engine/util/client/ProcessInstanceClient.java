/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.util.client;

import static io.camunda.zeebe.util.buffer.BufferUtil.wrapString;

import io.camunda.zeebe.msgpack.property.ArrayProperty;
import io.camunda.zeebe.msgpack.value.StringValue;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceCreationRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceCreationStartInstruction;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceModificationActivateInstruction;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceModificationRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceModificationTerminateInstruction;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceModificationVariableInstruction;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceCreationIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceModificationIntent;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceCreationRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceModificationRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import io.camunda.zeebe.test.util.MsgPackUtil;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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

    private final CommandWriter writer;
    private final ProcessInstanceCreationRecord processInstanceCreationRecord;

    private Function<Long, Record<ProcessInstanceCreationRecordValue>> expectation =
        SUCCESS_EXPECTATION;

    public ProcessInstanceCreationClient(final CommandWriter writer, final String bpmnProcessId) {
      this.writer = writer;
      processInstanceCreationRecord = new ProcessInstanceCreationRecord();
      processInstanceCreationRecord.setBpmnProcessId(bpmnProcessId);
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

    public ProcessInstanceCreationWithResultClient withResult() {
      return new ProcessInstanceCreationWithResultClient(writer, processInstanceCreationRecord);
    }

    public long create() {
      final long position =
          writer.writeCommand(ProcessInstanceCreationIntent.CREATE, processInstanceCreationRecord);

      final var resultingRecord = expectation.apply(position);
      return resultingRecord.getValue().getProcessInstanceKey();
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

    public void asyncCreate() {
      writer.writeCommand(
          requestStreamId,
          requestId,
          ProcessInstanceCreationIntent.CREATE_WITH_AWAITING_RESULT,
          record);
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

    public static final Function<Long, Record<ProcessInstanceRecordValue>> REJECTION_EXPECTATION =
        (processInstanceKey) ->
            RecordingExporter.processInstanceRecords()
                .onlyCommandRejections()
                .withIntent(ProcessInstanceIntent.CANCEL)
                .withRecordKey(processInstanceKey)
                .withProcessInstanceKey(processInstanceKey)
                .getFirst();

    private static final int DEFAULT_PARTITION = -1;
    private final CommandWriter writer;
    private final long processInstanceKey;

    private int partition = DEFAULT_PARTITION;
    private Function<Long, Record<ProcessInstanceRecordValue>> expectation = SUCCESS_EXPECTATION;

    public ExistingInstanceClient(final CommandWriter writer, final long processInstanceKey) {
      this.writer = writer;
      this.processInstanceKey = processInstanceKey;
    }

    public ExistingInstanceClient onPartition(final int partition) {
      this.partition = partition;
      return this;
    }

    public ExistingInstanceClient expectRejection() {
      expectation = REJECTION_EXPECTATION;
      return this;
    }

    public Record<ProcessInstanceRecordValue> cancel() {
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
          new ProcessInstanceRecord().setProcessInstanceKey(processInstanceKey));

      return expectation.apply(processInstanceKey);
    }

    public ProcessInstanceModificationClient modification() {
      return new ProcessInstanceModificationClient(writer, processInstanceKey);
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

    public ProcessInstanceModificationClient(
        final CommandWriter writer, final long processInstanceKey) {
      this.writer = writer;
      this.processInstanceKey = processInstanceKey;
      record = new ProcessInstanceModificationRecord();
      activateInstructions = new ArrayList<>();
    }

    private ProcessInstanceModificationClient(
        final CommandWriter writer,
        final long processInstanceKey,
        final ProcessInstanceModificationRecord record,
        final List<ProcessInstanceModificationActivateInstruction> activateInstructions) {
      this.writer = writer;
      this.processInstanceKey = processInstanceKey;
      this.record = record;
      this.activateInstructions = activateInstructions;
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
          writer, processInstanceKey, record, activateInstructions, activateInstruction);
    }

    public ProcessInstanceModificationClient terminateElement(final long elementInstanceKey) {
      record.addTerminateInstruction(
          new ProcessInstanceModificationTerminateInstruction()
              .setElementInstanceKey(elementInstanceKey));
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
          writer.writeCommand(processInstanceKey, ProcessInstanceModificationIntent.MODIFY, record);

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
          final ProcessInstanceModificationActivateInstruction activateInstruction) {
        super(environmentRule, processInstanceKey, record, activateInstructions);
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
}
