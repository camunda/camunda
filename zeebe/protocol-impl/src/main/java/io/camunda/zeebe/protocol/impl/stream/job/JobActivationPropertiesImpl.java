/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.stream.job;

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
import io.camunda.zeebe.protocol.impl.encoding.AuthInfo;
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.protocol.record.value.TenantFilter;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@MsgpackPojo
@NullMarked
public final class JobActivationPropertiesImpl
    extends MsgpackBufferValue<JobActivationPropertiesImpl> implements JobActivationProperties {

  DirectBuffer worker = Decoding.EMPTY;
  long timeout = -1L;
  final List<DirectBuffer> variables = new ArrayList<>();
  final List<DirectBuffer> tenantIds = new ArrayList<>();

  @MsgpackProperty(document = true)
  DirectBuffer claims = DocumentValue.EMPTY_DOCUMENT;

  TenantFilter tenantFilter = TenantFilter.PROVIDED;
  boolean withLease = false;
  @MsgpackUnknownProperties final UnknownProperties unknownProperties = new UnknownProperties();

  public JobActivationPropertiesImpl setWorker(
      final DirectBuffer worker, final int offset, final int length) {
    this.worker = new UnsafeBuffer(worker, offset, length);
    return this;
  }

  public JobActivationPropertiesImpl setTimeout(final long val) {
    timeout = val;
    return this;
  }

  public JobActivationPropertiesImpl setFetchVariables(final Set<StringValue> variables) {
    this.variables.clear();
    variables.forEach(variable -> this.variables.add(variable.getValue()));
    return this;
  }

  public JobActivationPropertiesImpl setTenantIds(final Collection<String> tenantIds) {
    this.tenantIds.clear();
    tenantIds.forEach(tenantId -> this.tenantIds.add(BufferUtil.wrapString(tenantId)));
    return this;
  }

  public JobActivationPropertiesImpl setWithLease(final boolean withLease) {
    this.withLease = withLease;
    return this;
  }

  @Override
  public DirectBuffer worker() {
    return worker;
  }

  @Override
  public Collection<DirectBuffer> fetchVariables() {
    return List.copyOf(variables);
  }

  @Override
  public long timeout() {
    return timeout;
  }

  @Override
  public Collection<String> tenantIds() {
    return tenantIds.stream().map(BufferUtil::bufferAsString).toList();
  }

  @Override
  public TenantFilter tenantFilter() {
    return tenantFilter;
  }

  public Map<String, Object> claims() {
    return MsgPackConverter.convertToMap(claims);
  }

  @Override
  public AuthInfo authInfo() {
    return AuthInfo.ofClaims(claims());
  }

  @Override
  public boolean withLease() {
    return withLease;
  }

  public JobActivationPropertiesImpl setTenantFilter(final TenantFilter tenantFilter) {
    this.tenantFilter = tenantFilter;
    return this;
  }

  public JobActivationPropertiesImpl setClaims(final @Nullable Map<String, Object> authorizations) {
    claims = Decoding.document(getDocumentOrEmpty(authorizations));
    return this;
  }

  protected DirectBuffer getDocumentOrEmpty(final @Nullable Map<String, Object> value) {
    return value == null || value.isEmpty()
        ? DocumentValue.EMPTY_DOCUMENT
        : new UnsafeBuffer(MsgPackConverter.convertToMsgPack(value));
  }

  @Override
  protected MsgpackCodec<JobActivationPropertiesImpl> codec() {
    return JobActivationPropertiesImplCodec.INSTANCE;
  }

  @Override
  protected MsgpackOps<JobActivationPropertiesImpl> ops() {
    return JobActivationPropertiesImplOps.INSTANCE;
  }
}
