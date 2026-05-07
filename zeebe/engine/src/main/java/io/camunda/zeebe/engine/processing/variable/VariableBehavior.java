/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.variable;

import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnConditionalBehavior;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnConditionalBehavior.VariableEvent;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedCommandWriter;
import io.camunda.zeebe.engine.state.immutable.VariableState;
import io.camunda.zeebe.engine.state.variable.DocumentEntry;
import io.camunda.zeebe.engine.state.variable.IndexedDocument;
import io.camunda.zeebe.engine.state.variable.VariableInstance;
import io.camunda.zeebe.msgpack.spec.MsgPackReader;
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.protocol.impl.record.value.clustervariable.ClusterVariableRecord;
import io.camunda.zeebe.protocol.impl.record.value.variable.VariableRecord;
import io.camunda.zeebe.protocol.impl.record.value.variable.VariableSourceRecord;
import io.camunda.zeebe.protocol.record.intent.ClusterVariableIntent;
import io.camunda.zeebe.protocol.record.intent.VariableIntent;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A behavior which allows processors to mutate the variable state. Use this anywhere where you
 * would want to set a variable during processing.
 *
 * <p>Note that for {@link io.camunda.zeebe.engine.state.EventApplier}, you should just use the
 * mutable state directly.
 */
public final class VariableBehavior {

  /**
   * Variables in an output mapping (or any variable document merge) whose name starts with this
   * prefix are routed to the cluster variable state instead of the local process variable scope.
   * The remainder of the name (after the prefix) is used as the cluster variable name.
   *
   * <p>Note: Zeebe output mapping target paths with dots (e.g. {@code
   * target="camunda.vars.cluster.foo"}) are translated by the FEEL/output-mapping engine into a
   * nested map under a top-level entry named {@code camunda}. To detect that case we also navigate
   * such nested structures (see {@link #CLUSTER_VARIABLE_NAMESPACE_ROOT}).
   */
  public static final String CLUSTER_VARIABLE_NAME_PREFIX = "camunda.vars.cluster.";
  private static final Logger CLUSTER_VAR_LOG =
      LoggerFactory.getLogger("io.camunda.zeebe.engine.clustervariable");
  private static final String CLUSTER_VARIABLE_NAMESPACE_ROOT = "camunda";
  private static final String CLUSTER_VARIABLE_NAMESPACE_VARS = "vars";
  private static final String CLUSTER_VARIABLE_NAMESPACE_CLUSTER = "cluster";

  private final VariableState variableState;
  private final StateWriter stateWriter;
  private final TypedCommandWriter commandWriter;
  private final BpmnConditionalBehavior conditionalBehavior;
  private final KeyGenerator keyGenerator;

  private final IndexedDocument indexedDocument = new IndexedDocument();
  private final VariableRecord variableRecord = new VariableRecord();
  private final ClusterVariableRecord clusterVariableRecord = new ClusterVariableRecord();
  private final MsgPackReader clusterVariableMsgPackReader = new MsgPackReader();
  private final UnsafeBuffer clusterVariableValueView = new UnsafeBuffer();
  private final VariableSourceRecord variableSourceRecord;

  public VariableBehavior(
      final VariableState variableState,
      final StateWriter stateWriter,
      final TypedCommandWriter commandWriter,
      final BpmnConditionalBehavior conditionalBehavior,
      final KeyGenerator keyGenerator) {
    this.variableState = variableState;
    this.stateWriter = stateWriter;
    this.commandWriter = commandWriter;
    this.conditionalBehavior = conditionalBehavior;
    this.keyGenerator = keyGenerator;
    variableSourceRecord = VariableSourceRecord.none();
  }

  public VariableBehavior(
      final VariableState variableState,
      final StateWriter stateWriter,
      final TypedCommandWriter commandWriter,
      final BpmnConditionalBehavior conditionalBehavior,
      final KeyGenerator keyGenerator,
      final VariableSourceRecord variableSourceRecord) {
    this.variableState = variableState;
    this.stateWriter = stateWriter;
    this.commandWriter = commandWriter;
    this.conditionalBehavior = conditionalBehavior;
    this.keyGenerator = keyGenerator;
    this.variableSourceRecord = variableSourceRecord;
  }

  public VariableBehavior withVariableSource(final VariableSourceRecord source) {
    return new VariableBehavior(
        variableState, stateWriter, commandWriter, conditionalBehavior, keyGenerator, source);
  }

  /**
   * Merges the given document directly on the given scope key.
   *
   * <p>If any variable from the document already exists on the current scope, a {@code
   * Variable.UPDATED} record is produced as a follow up event.
   *
   * <p>For all variables from the document which do not exist in the current scope, a {@code
   * Variable.CREATED} record is produced as a follow up event.
   *
   * @param scopeKey the scope key for each variable
   * @param processDefinitionKey the process key to be associated with each variable
   * @param processInstanceKey the process instance key to be associated with each variable
   * @param document the document to merge
   */
  public void mergeLocalDocument(
      final long scopeKey,
      final long processDefinitionKey,
      final long processInstanceKey,
      final long rootProcessInstanceKey,
      final DirectBuffer bpmnProcessId,
      final String tenantId,
      final DirectBuffer document) {
    indexedDocument.index(document);
    if (indexedDocument.isEmpty()) {
      return;
    }

    extractAndEmitClusterVariableUpdates(tenantId);
    if (indexedDocument.isEmpty()) {
      return;
    }

    variableRecord
        .setScopeKey(scopeKey)
        .setProcessDefinitionKey(processDefinitionKey)
        .setProcessInstanceKey(processInstanceKey)
        .setRootProcessInstanceKey(rootProcessInstanceKey)
        .setBpmnProcessId(bpmnProcessId)
        .setTenantId(tenantId)
        .setSource(variableSourceRecord);
    final List<VariableEvent> variableEvents = new ArrayList<>();
    for (final DocumentEntry entry : indexedDocument) {
      applyEntryToRecord(entry);
      final Optional<VariableEvent> variableEvent = setLocalVariable(variableRecord);
      variableEvent.ifPresent(variableEvents::add);
    }

    conditionalBehavior.evaluateConditionals(processInstanceKey, variableEvents);
  }

  /**
   * Merges the given document, propagating its changes from the bottom to the top of the scope
   * hierarchy.
   *
   * <p>Starting at the given {@code scopeKey}, it will overwrite any variables that exist in that
   * scope with the corresponding values from the given document. Variables that were not set
   * because they did not exist in the current scope are collected as a sub document, which will
   * then be merged with the parent scope, recursively, until there are no more. If we reach a scope
   * with no parent, then any remaining variables are created there.
   *
   * <p>If any variable from the document already exists on the current scope, a {@code
   * Variable.UPDATED} record is produced as a follow up event.
   *
   * <p>For all variables from the document which do not exist in the current scope, a {@code
   * Variable.CREATED} record is produced as a follow up event.
   *
   * @param scopeKey the scope key for each variable
   * @param processDefinitionKey the process key to be associated with each variable
   * @param processInstanceKey the process instance key to be associated with each variable
   * @param rootProcessInstanceKey the root process instance key to be associated with each variable
   * @param document the document to merge
   */
  public void mergeDocument(
      final long scopeKey,
      final long processDefinitionKey,
      final long processInstanceKey,
      final long rootProcessInstanceKey,
      final DirectBuffer bpmnProcessId,
      final String tenantId,
      final DirectBuffer document) {
    indexedDocument.index(document);
    if (indexedDocument.isEmpty()) {
      return;
    }

    extractAndEmitClusterVariableUpdates(tenantId);
    if (indexedDocument.isEmpty()) {
      return;
    }

    long currentScope = scopeKey;
    long parentScope;
    final List<VariableEvent> variableEvents = new ArrayList<>();

    variableRecord
        .setProcessDefinitionKey(processDefinitionKey)
        .setProcessInstanceKey(processInstanceKey)
        .setRootProcessInstanceKey(rootProcessInstanceKey)
        .setBpmnProcessId(bpmnProcessId)
        .setTenantId(tenantId)
        .setSource(variableSourceRecord);
    while ((parentScope = variableState.getParentScopeKey(currentScope)) > 0) {
      final Iterator<DocumentEntry> entryIterator = indexedDocument.iterator();

      variableRecord.setScopeKey(currentScope);
      while (entryIterator.hasNext()) {
        final DocumentEntry entry = entryIterator.next();
        final VariableInstance variableInstance =
            variableState.getVariableInstanceLocal(currentScope, entry.getName());

        if (variableInstance != null && !variableInstance.getValue().equals(entry.getValue())) {
          applyEntryToRecord(entry);
          stateWriter.appendFollowUpEvent(
              variableInstance.getKey(), VariableIntent.UPDATED, variableRecord);
          variableEvents.add(
              new VariableEvent(
                  currentScope, VariableIntent.UPDATED, getVariableRecordCopy(variableRecord)));
          entryIterator.remove();
        }
      }

      currentScope = parentScope;
    }

    variableRecord.setScopeKey(currentScope);
    for (final DocumentEntry entry : indexedDocument) {
      applyEntryToRecord(entry);
      final Optional<VariableEvent> variableEvent = setLocalVariable(variableRecord);
      variableEvent.ifPresent(variableEvents::add);
    }

    conditionalBehavior.evaluateConditionals(processInstanceKey, variableEvents);
  }

  /**
   * Publishes a follow up event to create or update the variable with name {@code name} on the
   * given scope with key {@code scopeKey}, with additional {@code processDefinitionKey} and {@code
   * processInstanceKey} context.
   *
   * <p>If the scope is the process instance itself, then {@code scopeKey} should be equal to {@code
   * processInstanceKey}.
   *
   * @param scopeKey the key of the scope on which to set the variable
   * @param processDefinitionKey the associated process key
   * @param processInstanceKey the associated process instance key
   * @param name a buffer containing only the name of the variable
   * @param value a buffer containing the value of the variable as MessagePack
   * @param valueOffset the offset of the value in the {@code value} buffer
   * @param valueLength the length of the value in the {@code value} buffer
   */
  public void setLocalVariable(
      final long scopeKey,
      final long processDefinitionKey,
      final long processInstanceKey,
      final long rootProcessInstanceKey,
      final DirectBuffer bpmnProcessId,
      final String tenantId,
      final DirectBuffer name,
      final DirectBuffer value,
      final int valueOffset,
      final int valueLength) {

    variableRecord
        .setScopeKey(scopeKey)
        .setProcessDefinitionKey(processDefinitionKey)
        .setProcessInstanceKey(processInstanceKey)
        .setRootProcessInstanceKey(rootProcessInstanceKey)
        .setBpmnProcessId(bpmnProcessId)
        .setTenantId(tenantId)
        .setName(name)
        .setValue(value, valueOffset, valueLength);

    final Optional<VariableEvent> variableEvent = setLocalVariable(variableRecord);
    final var variableEvents = variableEvent.map(List::of).orElseGet(List::of);

    conditionalBehavior.evaluateConditionals(processInstanceKey, variableEvents);
  }

  private Optional<VariableEvent> setLocalVariable(final VariableRecord record) {
    Optional<VariableEvent> event = Optional.empty();
    final VariableInstance variableInstance =
        variableState.getVariableInstanceLocal(record.getScopeKey(), record.getNameBuffer());
    if (variableInstance == null) {
      final long key = keyGenerator.nextKey();
      stateWriter.appendFollowUpEvent(key, VariableIntent.CREATED, record);
      event =
          Optional.of(
              new VariableEvent(
                  record.getScopeKey(), VariableIntent.CREATED, getVariableRecordCopy(record)));
    } else if (!variableInstance.getValue().equals(record.getValueBuffer())) {
      stateWriter.appendFollowUpEvent(variableInstance.getKey(), VariableIntent.UPDATED, record);
      event =
          Optional.of(
              new VariableEvent(
                  record.getScopeKey(), VariableIntent.UPDATED, getVariableRecordCopy(record)));
    }

    return event;
  }

  private void applyEntryToRecord(final DocumentEntry entry) {
    variableRecord.setName(entry.getName()).setValue(entry.getValue());
  }

  /**
   * Removes any document entries that should be routed to the cluster variable state and emits a
   * {@link ClusterVariableIntent#UPDATE} command for each. Two name shapes are detected:
   *
   * <ul>
   *   <li><b>Direct prefix</b>: a single entry whose name literally starts with {@link
   *       #CLUSTER_VARIABLE_NAME_PREFIX} (e.g. a job worker that returns a variable named {@code
   *       "camunda.vars.cluster.foo"}). The remainder of the name is the cluster variable name.
   *   <li><b>Nested map</b>: an entry named {@code camunda} whose msgpack value is a map of the
   *       shape {@code {vars: {cluster: {<name>: <value>, ...}}}}. This is what Zeebe's output
   *       mapping produces when the target path uses dots, e.g. {@code
   *       target="camunda.vars.cluster.foo"}. Each key inside the {@code cluster} map is treated as
   *       a cluster variable update.
   * </ul>
   *
   * <p>Internal commands bypass authorization checks in {@link
   * io.camunda.zeebe.engine.processing.clustervariable.ClusterVariableUpdateProcessor}.
   *
   * <p>For PoC simplicity: when a tenant id is present and non-blank we emit a TENANT-scoped
   * update; otherwise we emit a GLOBAL-scoped update. The processor will reject the update if the
   * cluster variable does not exist in the chosen scope. When the nested map case is matched the
   * entire {@code camunda} entry is dropped from the local document — sibling content under {@code
   * camunda.*} that isn't a cluster variable would be lost.
   */
  private void extractAndEmitClusterVariableUpdates(final String tenantId) {
    if (commandWriter == null) {
      return;
    }
    final Iterator<DocumentEntry> iterator = indexedDocument.iterator();
    while (iterator.hasNext()) {
      final DocumentEntry entry = iterator.next();
      final String fullName = BufferUtil.bufferAsString(entry.getName());

      if (fullName.startsWith(CLUSTER_VARIABLE_NAME_PREFIX)) {
        final String clusterVariableName =
            fullName.substring(CLUSTER_VARIABLE_NAME_PREFIX.length());
        if (clusterVariableName.isEmpty()) {
          continue;
        }
        CLUSTER_VAR_LOG.info(
            "[clustervar] direct-prefix match: name='{}' tenantId='{}' valueBytes={}",
            clusterVariableName,
            tenantId,
            entry.getValue().capacity());
        emitClusterVariableUpdate(
            clusterVariableName, BufferUtil.cloneBuffer(entry.getValue()), tenantId);
        iterator.remove();
        continue;
      }

      if (CLUSTER_VARIABLE_NAMESPACE_ROOT.equals(fullName)) {
        CLUSTER_VAR_LOG.info(
            "[clustervar] nested-map candidate: top-level entry 'camunda' detected, tenantId='{}', valueBytes={}",
            tenantId,
            entry.getValue().capacity());
        if (extractAndEmitFromNestedClusterMap(entry.getValue(), tenantId)) {
          iterator.remove();
        } else {
          CLUSTER_VAR_LOG.info(
              "[clustervar] nested-map candidate did NOT match camunda.vars.cluster.* shape; "
                  + "leaving 'camunda' entry untouched");
        }
      }
    }
  }

  /**
   * Treats {@code value} as a msgpack map of shape {@code {vars: {cluster: {<name>: <v>, ...}}}}
   * and emits a cluster variable UPDATE command for each entry inside the inner {@code cluster}
   * map. Returns {@code true} iff at least one update was emitted.
   */
  private boolean extractAndEmitFromNestedClusterMap(
      final DirectBuffer value, final String tenantId) {
    try {
      clusterVariableMsgPackReader.wrap(value, 0, value.capacity());
      final int topLevelMapSize = clusterVariableMsgPackReader.readMapHeader();
      if (!seekToKeyInMap(topLevelMapSize, CLUSTER_VARIABLE_NAMESPACE_VARS)) {
        CLUSTER_VAR_LOG.info("[clustervar] no 'vars' key under 'camunda' — skipping");
        return false;
      }
      final int varsMapSize = clusterVariableMsgPackReader.readMapHeader();
      if (!seekToKeyInMap(varsMapSize, CLUSTER_VARIABLE_NAMESPACE_CLUSTER)) {
        CLUSTER_VAR_LOG.info("[clustervar] no 'cluster' key under 'camunda.vars' — skipping");
        return false;
      }
      final int clusterMapSize = clusterVariableMsgPackReader.readMapHeader();
      CLUSTER_VAR_LOG.info(
          "[clustervar] navigated to camunda.vars.cluster, found {} entries", clusterMapSize);
      boolean emittedAny = false;
      for (int i = 0; i < clusterMapSize; i++) {
        final int nameLength = clusterVariableMsgPackReader.readStringLength();
        final String varName =
            BufferUtil.bufferAsString(
                clusterVariableMsgPackReader.getBuffer(),
                clusterVariableMsgPackReader.getOffset(),
                nameLength);
        clusterVariableMsgPackReader.skipBytes(nameLength);

        final int valueOffset = clusterVariableMsgPackReader.getOffset();
        clusterVariableMsgPackReader.skipValue();
        final int valueLength = clusterVariableMsgPackReader.getOffset() - valueOffset;

        clusterVariableValueView.wrap(
            clusterVariableMsgPackReader.getBuffer(), valueOffset, valueLength);
        CLUSTER_VAR_LOG.info(
            "[clustervar] nested-map extracted: name='{}' valueBytes={} "
                + "(reader pos before={} after={})",
            varName,
            valueLength,
            valueOffset,
            valueOffset + valueLength);
        emitClusterVariableUpdate(
            varName, BufferUtil.cloneBuffer(clusterVariableValueView), tenantId);
        emittedAny = true;
      }
      return emittedAny;
    } catch (final RuntimeException e) {
      CLUSTER_VAR_LOG.warn(
          "[clustervar] failed to navigate nested map under 'camunda' — leaving entry as-is", e);
      return false;
    }
  }

  /**
   * Walks {@code mapSize} key/value pairs from the current reader position looking for {@code
   * targetKey}. Returns true with the reader positioned at the matched value, or false (with the
   * reader fully consumed past the map) if no match is found.
   */
  private boolean seekToKeyInMap(final int mapSize, final String targetKey) {
    for (int i = 0; i < mapSize; i++) {
      final int keyLength = clusterVariableMsgPackReader.readStringLength();
      final String key =
          BufferUtil.bufferAsString(
              clusterVariableMsgPackReader.getBuffer(),
              clusterVariableMsgPackReader.getOffset(),
              keyLength);
      clusterVariableMsgPackReader.skipBytes(keyLength);
      if (targetKey.equals(key)) {
        return true;
      }
      clusterVariableMsgPackReader.skipValue();
    }
    return false;
  }

  private void emitClusterVariableUpdate(
      final String name, final DirectBuffer value, final String tenantId) {
    clusterVariableRecord.reset();
    clusterVariableRecord.setName(name).setValue(value);
    if (tenantId != null
        && !tenantId.isBlank()
        && !TenantOwned.DEFAULT_TENANT_IDENTIFIER.equals(tenantId)) {
      clusterVariableRecord.setTenantId(tenantId).setTenantScope();
    } else {
      clusterVariableRecord.setGlobalScope();
    }
    if (CLUSTER_VAR_LOG.isInfoEnabled()) {
      String valueAsJson;
      try {
        valueAsJson = MsgPackConverter.convertToJson(value);
      } catch (final Exception e) {
        valueAsJson = "<unable to convert value to JSON: " + e.getMessage() + ">";
      }
      // Truncate the JSON for log readability — full bytes are still in the command
      final String preview =
          valueAsJson.length() > 1024
              ? valueAsJson.substring(0, 1024) + "...(truncated)"
              : valueAsJson;
      CLUSTER_VAR_LOG.info(
          "[clustervar] EMIT ClusterVariableIntent.UPDATE name='{}' scope={} tenantId='{}' "
              + "valueBytes={} valueJson={}",
          name,
          clusterVariableRecord.getScope(),
          clusterVariableRecord.getTenantId(),
          value.capacity(),
          valueAsJson);
    }
    commandWriter.appendNewCommand(ClusterVariableIntent.UPDATE, clusterVariableRecord);
  }

  private VariableRecord getVariableRecordCopy(final VariableRecord variableRecord) {
    final VariableRecord variableRecordCopy = new VariableRecord();
    variableRecordCopy.copyFrom(variableRecord);

    return variableRecordCopy;
  }
}
