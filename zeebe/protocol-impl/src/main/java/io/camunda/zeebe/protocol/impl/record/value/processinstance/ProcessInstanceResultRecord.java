/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.processinstance;

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
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.protocol.impl.record.MsgpackRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceResultRecordValue;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.agrona.DirectBuffer;
import org.jspecify.annotations.NullMarked;

@MsgpackPojo
@NullMarked
public final class ProcessInstanceResultRecord
    extends MsgpackBufferValue<ProcessInstanceResultRecord>
    implements MsgpackRecordValue, ProcessInstanceResultRecordValue {

  // Static StringValue keys for property names
  public static final StringValue BPMN_PROCESS_ID_KEY = new StringValue("bpmnProcessId");
  public static final StringValue PROCESS_DEFINITION_KEY_KEY =
      new StringValue("processDefinitionKey");
  public static final StringValue VERSION_KEY = new StringValue("version");
  public static final StringValue TENANT_ID_KEY = new StringValue("tenantId");
  public static final StringValue VARIABLES_KEY = new StringValue("variables");
  public static final StringValue PROCESS_INSTANCE_KEY_KEY = new StringValue("processInstanceKey");
  public static final StringValue TAGS_KEY = new StringValue("tags");
  public static final StringValue BUSINESS_ID_KEY = new StringValue("businessId");
  public static final StringValue STORAGE_ORDINAL_KEY = new StringValue("storageOrdinal");

  DirectBuffer bpmnProcessId = Decoding.EMPTY;
  long processDefinitionKey = -1L;
  long processInstanceKey = -1L;
  int version = -1;
  DirectBuffer tenantId = BufferUtil.wrapString(TenantOwned.DEFAULT_TENANT_IDENTIFIER);

  @MsgpackProperty(document = true)
  DirectBuffer variables = DocumentValue.EMPTY_DOCUMENT;

  final List<DirectBuffer> tags = new ArrayList<>();
  DirectBuffer businessId = Decoding.EMPTY;
  int storageOrdinal = 0;
  @MsgpackUnknownProperties final UnknownProperties unknownProperties = new UnknownProperties();

  @Override
  public String getBpmnProcessId() {
    return bufferAsString(bpmnProcessId);
  }

  public ProcessInstanceResultRecord setBpmnProcessId(final String bpmnProcessId) {
    this.bpmnProcessId = BufferUtil.wrapString(bpmnProcessId);
    return this;
  }

  public ProcessInstanceResultRecord setBpmnProcessId(final DirectBuffer bpmnProcessId) {
    this.bpmnProcessId = bpmnProcessId;
    return this;
  }

  @Override
  public int getVersion() {
    return version;
  }

  public ProcessInstanceResultRecord setVersion(final int version) {
    this.version = version;
    return this;
  }

  @Override
  public long getProcessInstanceKey() {
    return processInstanceKey;
  }

  @Override
  public long getProcessDefinitionKey() {
    return processDefinitionKey;
  }

  public ProcessInstanceResultRecord setProcessDefinitionKey(final long key) {
    processDefinitionKey = key;
    return this;
  }

  @Override
  public Set<String> getTags() {
    return tags.stream().map(BufferUtil::bufferAsString).collect(Collectors.toSet());
  }

  public ProcessInstanceResultRecord setTags(final Set<String> tags) {
    this.tags.clear();
    if (tags != null) {
      tags.forEach(tag -> this.tags.add(BufferUtil.wrapString(tag)));
    }
    return this;
  }

  @Override
  public String getBusinessId() {
    return bufferAsString(businessId);
  }

  public ProcessInstanceResultRecord setBusinessId(final String businessId) {
    this.businessId = BufferUtil.wrapString(businessId);
    return this;
  }

  public ProcessInstanceResultRecord setBusinessId(final DirectBuffer businessId) {
    this.businessId = businessId;
    return this;
  }

  public ProcessInstanceResultRecord setProcessInstanceKey(final long instanceKey) {
    processInstanceKey = instanceKey;
    return this;
  }

  @JsonIgnore
  public DirectBuffer getBusinessIdBuffer() {
    return businessId;
  }

  @JsonIgnore
  public DirectBuffer getBpmnProcessIdBuffer() {
    return bpmnProcessId;
  }

  @JsonIgnore
  public DirectBuffer getVariablesBuffer() {
    return variables;
  }

  @Override
  public Map<String, Object> getVariables() {
    return MsgPackConverter.convertToMap(variables);
  }

  public ProcessInstanceResultRecord setVariables(final DirectBuffer variables) {
    this.variables = Decoding.document(variables);
    return this;
  }

  @Override
  public String getTenantId() {
    return bufferAsString(tenantId);
  }

  public ProcessInstanceResultRecord setTenantId(final String tenantId) {
    this.tenantId = BufferUtil.wrapString(tenantId);
    return this;
  }

  @Override
  public int getStorageOrdinal() {
    return storageOrdinal;
  }

  public ProcessInstanceResultRecord setStorageOrdinal(final int storageOrdinal) {
    this.storageOrdinal = storageOrdinal;
    return this;
  }

  @Override
  protected MsgpackCodec<ProcessInstanceResultRecord> codec() {
    return ProcessInstanceResultRecordCodec.INSTANCE;
  }

  @Override
  protected MsgpackOps<ProcessInstanceResultRecord> ops() {
    return ProcessInstanceResultRecordOps.INSTANCE;
  }
}
