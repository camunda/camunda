/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.conditional;

import static io.camunda.zeebe.util.buffer.BufferUtil.bufferAsString;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.camunda.zeebe.msgpack.pojo.Decoding;
import io.camunda.zeebe.msgpack.pojo.MsgpackBufferValue;
import io.camunda.zeebe.msgpack.pojo.MsgpackCodec;
import io.camunda.zeebe.msgpack.pojo.MsgpackOps;
import io.camunda.zeebe.msgpack.pojo.MsgpackPojo;
import io.camunda.zeebe.msgpack.pojo.MsgpackProperty;
import io.camunda.zeebe.msgpack.pojo.MsgpackUnknownProperties;
import io.camunda.zeebe.msgpack.pojo.UnknownProperties;
import io.camunda.zeebe.msgpack.value.DocumentValue;
import io.camunda.zeebe.msgpack.value.StringValue;
import io.camunda.zeebe.msgpack.value.ValueArray;
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.protocol.impl.record.MsgpackRecordValue;
import io.camunda.zeebe.protocol.record.value.ConditionalEvaluationRecordValue;
import io.camunda.zeebe.protocol.record.value.ConditionalStartedProcessInstanceValue;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.agrona.DirectBuffer;
import org.jspecify.annotations.NullMarked;

@MsgpackPojo
@NullMarked
public final class ConditionalEvaluationRecord
    extends MsgpackBufferValue<ConditionalEvaluationRecord>
    implements MsgpackRecordValue, ConditionalEvaluationRecordValue {

  // Static StringValue keys for property names
  public static final StringValue PROCESS_DEFINITION_KEY_KEY =
      new StringValue("processDefinitionKey");
  public static final StringValue VARIABLES_KEY = new StringValue("variables");
  public static final StringValue TENANT_ID_KEY = new StringValue("tenantId");
  public static final StringValue STARTED_PROCESS_INSTANCES_KEY =
      new StringValue("startedProcessInstances");

  long processDefinitionKey = -1L;

  @MsgpackProperty(document = true)
  DirectBuffer variables = DocumentValue.EMPTY_DOCUMENT;

  DirectBuffer tenantId = BufferUtil.wrapString(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
  final List<ConditionalStartedProcessInstance> startedProcessInstances = new ArrayList<>();
  @MsgpackUnknownProperties final UnknownProperties unknownProperties = new UnknownProperties();

  public void wrap(final ConditionalEvaluationRecord other) {
    processDefinitionKey = other.getProcessDefinitionKey();
    variables = Decoding.document(other.getVariablesBuffer());
    tenantId = BufferUtil.wrapString(other.getTenantId());
    startedProcessInstances.clear();
    for (final ConditionalStartedProcessInstance instance : other.startedProcessInstances) {
      final var copy = new ConditionalStartedProcessInstance();
      copy.copy(instance);
      startedProcessInstances.add(copy);
    }
  }

  @Override
  public long getProcessDefinitionKey() {
    return processDefinitionKey;
  }

  public ConditionalEvaluationRecord setProcessDefinitionKey(final long processDefinitionKey) {
    this.processDefinitionKey = processDefinitionKey;
    return this;
  }

  @Override
  public List<ConditionalStartedProcessInstanceValue> getStartedProcessInstances() {
    return startedProcessInstances.stream()
        .map(ConditionalStartedProcessInstanceValue.class::cast)
        .toList();
  }

  @JsonIgnore
  public DirectBuffer getVariablesBuffer() {
    return variables;
  }

  @Override
  public Map<String, Object> getVariables() {
    return MsgPackConverter.convertToMap(variables);
  }

  public ConditionalEvaluationRecord setVariables(final DirectBuffer variables) {
    this.variables = Decoding.document(variables);
    return this;
  }

  @Override
  public String getTenantId() {
    return bufferAsString(tenantId);
  }

  public ConditionalEvaluationRecord setTenantId(final String tenantId) {
    this.tenantId = BufferUtil.wrapString(tenantId);
    return this;
  }

  /**
   * Returns the started process instances for direct manipulation by the processor. This is used to
   * add started instances during processing.
   *
   * @return the started process instances, which can be added to and removed from
   */
  @JsonIgnore
  public ValueArray<ConditionalStartedProcessInstance> startedProcessInstances() {
    return new StartedProcessInstances(startedProcessInstances);
  }

  /**
   * Adds a started process instance to the record.
   *
   * @param processDefinitionKey the process definition key
   * @param processInstanceKey the process instance key
   * @param storageOrdinal the storage ordinal assigned to the started process instance
   * @return this record for fluent chaining
   */
  public ConditionalEvaluationRecord addStartedProcessInstance(
      final long processDefinitionKey, final long processInstanceKey, final int storageOrdinal) {
    startedProcessInstances.add(
        new ConditionalStartedProcessInstance()
            .setProcessDefinitionKey(processDefinitionKey)
            .setProcessInstanceKey(processInstanceKey)
            .setStorageOrdinal(storageOrdinal));
    return this;
  }

  @Override
  protected MsgpackCodec<ConditionalEvaluationRecord> codec() {
    return ConditionalEvaluationRecordCodec.INSTANCE;
  }

  @Override
  protected MsgpackOps<ConditionalEvaluationRecord> ops() {
    return ConditionalEvaluationRecordOps.INSTANCE;
  }

  /**
   * The mutable accessor of {@code startedProcessInstances}: it adds and removes the elements of
   * the list, so that code written against the former {@code ArrayProperty} keeps working.
   */
  private static final class StartedProcessInstances
      implements ValueArray<ConditionalStartedProcessInstance> {

    final List<ConditionalStartedProcessInstance> instances;

    private StartedProcessInstances(final List<ConditionalStartedProcessInstance> instances) {
      this.instances = instances;
    }

    @Override
    public ConditionalStartedProcessInstance add() {
      final var instance = new ConditionalStartedProcessInstance();
      instances.add(instance);
      return instance;
    }

    @Override
    public ConditionalStartedProcessInstance add(final int index) {
      final var instance = new ConditionalStartedProcessInstance();
      instances.add(index, instance);
      return instance;
    }

    @Override
    public ConditionalStartedProcessInstance get(final int index) {
      return instances.get(index);
    }

    @Override
    public ConditionalStartedProcessInstance remove(final int index) {
      return instances.remove(index);
    }

    @Override
    public int size() {
      return instances.size();
    }

    @Override
    public Stream<ConditionalStartedProcessInstance> stream() {
      return instances.stream();
    }

    @Override
    public Iterator<ConditionalStartedProcessInstance> iterator() {
      return instances.iterator();
    }
  }
}
