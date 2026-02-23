/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.stream.job;

import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.msgpack.property.ArrayProperty;
import io.camunda.zeebe.msgpack.property.DocumentProperty;
import io.camunda.zeebe.msgpack.property.EnumProperty;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.msgpack.value.DocumentValue;
import io.camunda.zeebe.msgpack.value.StringValue;
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.protocol.record.value.TenantFilter;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.Collection;
import java.util.Map;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public class JobActivationPropertiesImpl extends UnpackedObject implements JobActivationProperties {
  private final StringProperty workerProp = new StringProperty("worker", "");
  private final LongProperty timeoutProp = new LongProperty("timeout", -1);
  private final ArrayProperty<StringValue> fetchVariablesProp =
      new ArrayProperty<>("variables", StringValue::new);
  private final ArrayProperty<StringValue> tenantIdsProp =
      new ArrayProperty<>(
          "tenantIds", () -> new StringValue(TenantOwned.DEFAULT_TENANT_IDENTIFIER));
  private final DocumentProperty claimsProp = new DocumentProperty("claims");
  private final EnumProperty<TenantFilter> tenantFilterProp =
      new EnumProperty<>("tenantFilter", TenantFilter.class, TenantFilter.PROVIDED);

  public JobActivationPropertiesImpl() {
    super(6);
    declareProperty(workerProp)
        .declareProperty(timeoutProp)
        .declareProperty(fetchVariablesProp)
        .declareProperty(tenantIdsProp)
        .declareProperty(claimsProp)
        .declareProperty(tenantFilterProp);
  }

  public JobActivationPropertiesImpl setWorker(
      final DirectBuffer worker, final int offset, final int length) {
    workerProp.setValue(worker, offset, length);
    return this;
  }

  public JobActivationPropertiesImpl setTimeout(final long val) {
    timeoutProp.setValue(val);
    return this;
  }

  public JobActivationPropertiesImpl setFetchVariables(final Collection<StringValue> variables) {
    fetchVariablesProp.reset();
    variables.forEach(variable -> fetchVariablesProp.add().wrap(variable));
    return this;
  }

  public JobActivationPropertiesImpl setTenantIds(final Collection<String> tenantIds) {
    tenantIdsProp.reset();
    tenantIds.forEach(tenantId -> tenantIdsProp.add().wrap(BufferUtil.wrapString(tenantId)));
    return this;
  }

  @Override
  public DirectBuffer worker() {
    return workerProp.getValue();
  }

  @Override
  public Collection<DirectBuffer> fetchVariables() {
    return fetchVariablesProp.stream().map(StringValue::getValue).toList();
  }

  @Override
  public long timeout() {
    return timeoutProp.getValue();
  }

  @Override
  public Collection<String> tenantIds() {
    return tenantIdsProp.stream().map(StringValue::toString).toList();
  }

  @Override
  public TenantFilter tenantFilter() {
    return tenantFilterProp.getValue();
  }

  @Override
  public Map<String, Object> claims() {
    return MsgPackConverter.convertToMap(claimsProp.getValue());
  }

  public JobActivationPropertiesImpl setTenantFilter(final TenantFilter tenantFilter) {
    tenantFilterProp.setValue(tenantFilter);
    return this;
  }

  public JobActivationPropertiesImpl setClaims(final Map<String, Object> authorizations) {
    claimsProp.setValue(getDocumentOrEmpty(authorizations));
    return this;
  }

  protected DirectBuffer getDocumentOrEmpty(final Map<String, Object> value) {
    return value == null || value.isEmpty()
        ? DocumentValue.EMPTY_DOCUMENT
        : new UnsafeBuffer(MsgPackConverter.convertToMsgPack(value));
  }
}
