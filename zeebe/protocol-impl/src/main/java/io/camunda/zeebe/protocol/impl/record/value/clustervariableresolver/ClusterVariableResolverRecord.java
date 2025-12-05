/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.clustervariableresolver;

import static io.camunda.zeebe.util.buffer.BufferUtil.bufferAsString;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.value.ClusterVariableResolverRecordValue;
import org.agrona.DirectBuffer;

public class ClusterVariableResolverRecord extends UnifiedRecordValue
    implements ClusterVariableResolverRecordValue {

  private final StringProperty referenceProp = new StringProperty("reference", "");
  private final StringProperty resolvedValueProp = new StringProperty("resolvedValue", "");
  private final StringProperty tenantIdProp = new StringProperty("tenantId", "");

  public ClusterVariableResolverRecord() {
    super(3);
    declareProperty(referenceProp).declareProperty(resolvedValueProp).declareProperty(tenantIdProp);
  }

  @Override
  public String getReference() {
    return bufferAsString(referenceProp.getValue());
  }

  public ClusterVariableResolverRecord setReference(final String reference) {
    referenceProp.setValue(reference);
    return this;
  }

  @Override
  public String getResolvedValue() {
    final String value = bufferAsString(resolvedValueProp.getValue());
    return value.isEmpty() ? null : value;
  }

  public ClusterVariableResolverRecord setResolvedValue(final String resolvedValue) {
    resolvedValueProp.setValue(resolvedValue != null ? resolvedValue : "");
    return this;
  }

  @JsonIgnore
  public DirectBuffer getReferenceBuffer() {
    return referenceProp.getValue();
  }

  @Override
  public String getTenantId() {
    return bufferAsString(tenantIdProp.getValue());
  }

  public ClusterVariableResolverRecord setTenantId(final String tenantId) {
    tenantIdProp.setValue(tenantId);
    return this;
  }

  @JsonIgnore
  public DirectBuffer getTenantIdBuffer() {
    return tenantIdProp.getValue();
  }
}
