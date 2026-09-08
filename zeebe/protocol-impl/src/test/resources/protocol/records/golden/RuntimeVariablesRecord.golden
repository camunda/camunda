/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.runtimevariables;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.camunda.zeebe.msgpack.property.DocumentProperty;
import io.camunda.zeebe.msgpack.property.EnumProperty;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.value.RuntimeVariableScope;
import io.camunda.zeebe.protocol.record.value.RuntimeVariablesRecordValue;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.Map;
import org.agrona.DirectBuffer;

public final class RuntimeVariablesRecord extends UnifiedRecordValue
    implements RuntimeVariablesRecordValue {

  private final LongProperty scopeKeyProp = new LongProperty("scopeKey", -1L);
  private final EnumProperty<RuntimeVariableScope> scopeProp =
      new EnumProperty<>("scope", RuntimeVariableScope.class, RuntimeVariableScope.EFFECTIVE);
  private final StringProperty tenantIdProp = new StringProperty("tenantId", "");
  private final DocumentProperty variablesProp = new DocumentProperty("variables");

  public RuntimeVariablesRecord() {
    super(4);
    declareProperty(scopeKeyProp)
        .declareProperty(scopeProp)
        .declareProperty(tenantIdProp)
        .declareProperty(variablesProp);
  }

  @Override
  public long getScopeKey() {
    return scopeKeyProp.getValue();
  }

  public RuntimeVariablesRecord setScopeKey(final long scopeKey) {
    scopeKeyProp.setValue(scopeKey);
    return this;
  }

  @Override
  public RuntimeVariableScope getScope() {
    return scopeProp.getValue();
  }

  public RuntimeVariablesRecord setScope(final RuntimeVariableScope scope) {
    scopeProp.setValue(scope);
    return this;
  }

  @Override
  public String getTenantId() {
    return BufferUtil.bufferAsString(tenantIdProp.getValue());
  }

  public RuntimeVariablesRecord setTenantId(final String tenantId) {
    tenantIdProp.setValue(tenantId);
    return this;
  }

  @Override
  public Map<String, Object> getVariables() {
    return MsgPackConverter.convertToMap(variablesProp.getValue());
  }

  public RuntimeVariablesRecord setVariables(final DirectBuffer variables) {
    variablesProp.setValue(variables);
    return this;
  }

  @JsonIgnore
  public DirectBuffer getVariablesBuffer() {
    return variablesProp.getValue();
  }
}
