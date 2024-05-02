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
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.msgpack.value.StringValue;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.Collection;
import org.agrona.DirectBuffer;

public class JobActivationPropertiesImpl extends UnpackedObject implements JobActivationProperties {
  private final StringProperty workerProp = new StringProperty("worker", "");
  private final LongProperty timeoutProp = new LongProperty("timeout", -1);
  private final ArrayProperty<StringValue> fetchVariablesProp =
      new ArrayProperty<>("variables", StringValue::new);
  private final ArrayProperty<StringValue> tenantIdsProp =
      new ArrayProperty<>(
          "tenantIds", () -> new StringValue(TenantOwned.DEFAULT_TENANT_IDENTIFIER));

  public JobActivationPropertiesImpl() {
    super(4);
    declareProperty(workerProp)
        .declareProperty(timeoutProp)
        .declareProperty(fetchVariablesProp)
        .declareProperty(tenantIdsProp);
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
}
