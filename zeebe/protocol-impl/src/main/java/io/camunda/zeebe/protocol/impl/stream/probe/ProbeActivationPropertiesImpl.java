/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.stream.probe;

import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.msgpack.property.ArrayProperty;
import io.camunda.zeebe.msgpack.property.DocumentProperty;
import io.camunda.zeebe.msgpack.property.EnumProperty;
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

public final class ProbeActivationPropertiesImpl extends UnpackedObject
    implements ProbeActivationProperties {
  private final StringProperty workerProp = new StringProperty("worker", "");
  private final ArrayProperty<StringValue> capabilitiesProp =
      new ArrayProperty<>("capabilities", StringValue::new);
  private final ArrayProperty<StringValue> tenantIdsProp =
      new ArrayProperty<>(
          "tenantIds", () -> new StringValue(TenantOwned.DEFAULT_TENANT_IDENTIFIER));
  private final DocumentProperty claimsProp = new DocumentProperty("claims");
  private final EnumProperty<TenantFilter> tenantFilterProp =
      new EnumProperty<>("tenantFilter", TenantFilter.class, TenantFilter.PROVIDED);

  public ProbeActivationPropertiesImpl() {
    super(5);
    declareProperty(workerProp)
        .declareProperty(capabilitiesProp)
        .declareProperty(tenantIdsProp)
        .declareProperty(claimsProp)
        .declareProperty(tenantFilterProp);
  }

  public ProbeActivationPropertiesImpl setWorker(final String worker) {
    workerProp.setValue(worker);
    return this;
  }

  public ProbeActivationPropertiesImpl setCapabilities(final Collection<String> capabilities) {
    capabilitiesProp.reset();
    capabilities.forEach(
        capability -> capabilitiesProp.add().wrap(BufferUtil.wrapString(capability)));
    return this;
  }

  public ProbeActivationPropertiesImpl setTenantIds(final Collection<String> tenantIds) {
    tenantIdsProp.reset();
    tenantIds.forEach(tenantId -> tenantIdsProp.add().wrap(BufferUtil.wrapString(tenantId)));
    return this;
  }

  public ProbeActivationPropertiesImpl setTenantFilter(final TenantFilter tenantFilter) {
    tenantFilterProp.setValue(tenantFilter);
    return this;
  }

  public ProbeActivationPropertiesImpl setClaims(final Map<String, Object> claims) {
    claimsProp.setValue(
        claims == null || claims.isEmpty()
            ? DocumentValue.EMPTY_DOCUMENT
            : new UnsafeBuffer(MsgPackConverter.convertToMsgPack(claims)));
    return this;
  }

  @Override
  public DirectBuffer worker() {
    return workerProp.getValue();
  }

  @Override
  public Collection<String> capabilities() {
    return capabilitiesProp.stream().map(StringValue::toString).toList();
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
}
