/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.variable;

import static io.camunda.zeebe.util.buffer.BufferUtil.bufferAsString;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.camunda.zeebe.msgpack.pojo.Decoding;
import io.camunda.zeebe.msgpack.pojo.MsgpackBufferValue;
import io.camunda.zeebe.msgpack.pojo.MsgpackCodec;
import io.camunda.zeebe.msgpack.pojo.MsgpackIgnore;
import io.camunda.zeebe.msgpack.pojo.MsgpackOps;
import io.camunda.zeebe.msgpack.pojo.MsgpackPojo;
import io.camunda.zeebe.msgpack.pojo.MsgpackProperty;
import io.camunda.zeebe.msgpack.pojo.MsgpackUnknownProperties;
import io.camunda.zeebe.msgpack.pojo.RequiredLong;
import io.camunda.zeebe.msgpack.pojo.UnknownProperties;
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.protocol.impl.record.MsgpackRecordValue;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.protocol.record.value.VariableRecordValue;
import io.camunda.zeebe.util.buffer.BufferUtil;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@MsgpackPojo
@NullMarked
public final class VariableRecord extends MsgpackBufferValue<VariableRecord>
    implements MsgpackRecordValue, VariableRecordValue {

  @Nullable DirectBuffer name;

  @MsgpackProperty(binary = true)
  @Nullable DirectBuffer value;

  final RequiredLong scopeKey = new RequiredLong();
  final RequiredLong processInstanceKey = new RequiredLong();
  final RequiredLong processDefinitionKey = new RequiredLong();
  DirectBuffer bpmnProcessId = Decoding.EMPTY;
  DirectBuffer tenantId = BufferUtil.wrapString(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
  long rootProcessInstanceKey = -1L;
  int storageOrdinal = 0;
  final VariableSourceRecord source = new VariableSourceRecord();
  @MsgpackUnknownProperties final UnknownProperties unknownProperties = new UnknownProperties();

  /**
   * Cached JSON representation of the value. Lazily computed on first call to {@link #getValue()}
   * and invalidated when the underlying value changes via {@link #setValue}, {@link #wrap} or
   * {@link #copyFrom}.
   */
  @MsgpackIgnore @Nullable String cachedJsonValue;

  @Override
  public void reset() {
    super.reset();
    cachedJsonValue = null;
  }

  @Override
  public void copyFrom(final VariableRecord other) {
    super.copyFrom(other);
    cachedJsonValue = null;
  }

  @Override
  public String getName() {
    return BufferUtil.bufferAsString(Decoding.required(name, "name"));
  }

  @Override
  public String getValue() {
    String json = cachedJsonValue;
    if (json == null) {
      json = MsgPackConverter.convertToJson(Decoding.required(value, "value"));
      cachedJsonValue = json;
    }
    return json;
  }

  // Internal accessor used by MetricsExporter for size-based metrics; not part of
  // VariableRecordValue and excluded from JSON serialization.
  @JsonIgnore
  public int getValueLength() {
    return Decoding.required(value, "value").capacity();
  }

  @Override
  public long getScopeKey() {
    return scopeKey.get();
  }

  public VariableRecord setScopeKey(final long scopeKey) {
    this.scopeKey.set(scopeKey);
    return this;
  }

  @Override
  public long getProcessInstanceKey() {
    return processInstanceKey.get();
  }

  @Override
  public long getProcessDefinitionKey() {
    return processDefinitionKey.get();
  }

  public VariableRecord setProcessDefinitionKey(final long processDefinitionKey) {
    this.processDefinitionKey.set(processDefinitionKey);
    return this;
  }

  @Override
  public long getRootProcessInstanceKey() {
    return rootProcessInstanceKey;
  }

  @Override
  public String getBpmnProcessId() {
    return BufferUtil.bufferAsString(bpmnProcessId);
  }

  public VariableRecord setBpmnProcessId(final DirectBuffer bpmnProcessId) {
    this.bpmnProcessId = bpmnProcessId;
    return this;
  }

  @Override
  public VariableSourceRecord getSource() {
    return source;
  }

  public VariableRecord setSource(final VariableSourceRecord source) {
    this.source.wrap(source);
    return this;
  }

  public VariableRecord setRootProcessInstanceKey(final long rootProcessInstanceKey) {
    this.rootProcessInstanceKey = rootProcessInstanceKey;
    return this;
  }

  public VariableRecord setProcessInstanceKey(final long processInstanceKey) {
    this.processInstanceKey.set(processInstanceKey);
    return this;
  }

  public VariableRecord setValue(final DirectBuffer value) {
    this.value = value;
    cachedJsonValue = null;
    return this;
  }

  public VariableRecord setName(final DirectBuffer name) {
    this.name = name;
    return this;
  }

  public VariableRecord setValue(final DirectBuffer value, final int offset, final int length) {
    this.value = new UnsafeBuffer(value, offset, length);
    cachedJsonValue = null;
    return this;
  }

  @JsonIgnore
  public DirectBuffer getNameBuffer() {
    return Decoding.required(name, "name");
  }

  @JsonIgnore
  public DirectBuffer getValueBuffer() {
    return Decoding.required(value, "value");
  }

  @JsonIgnore
  public DirectBuffer getBpmnProcessIdBuffer() {
    return bpmnProcessId;
  }

  @Override
  public String getTenantId() {
    return bufferAsString(tenantId);
  }

  public VariableRecord setTenantId(final String tenantId) {
    this.tenantId = BufferUtil.wrapString(tenantId);
    return this;
  }

  @Override
  public int getStorageOrdinal() {
    return storageOrdinal;
  }

  public VariableRecord setStorageOrdinal(final int storageOrdinal) {
    this.storageOrdinal = storageOrdinal;
    return this;
  }

  @Override
  protected MsgpackCodec<VariableRecord> codec() {
    return VariableRecordCodec.INSTANCE;
  }

  @Override
  protected MsgpackOps<VariableRecord> ops() {
    return VariableRecordOps.INSTANCE;
  }
}
