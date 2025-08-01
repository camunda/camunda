/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.variable;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.camunda.zeebe.msgpack.property.DocumentProperty;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.value.StringValue;
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.value.GlobalVariableRecordValue;
import java.util.Map;
import org.agrona.DirectBuffer;

public class GlobalVariableRecord extends UnifiedRecordValue implements GlobalVariableRecordValue {
  private static final StringValue SCOPE_KEY_KEY = new StringValue("scopeKey");
  private static final StringValue VARIABLES_KEY = new StringValue("variables");

  private final LongProperty scopeKeyProperty = new LongProperty(SCOPE_KEY_KEY);

  private final DocumentProperty variablesProperty = new DocumentProperty(VARIABLES_KEY);

  public GlobalVariableRecord() {
    super(2);
    declareProperty(scopeKeyProperty).declareProperty(variablesProperty);
    scopeKeyProperty.setValue(-1);
  }

  @Override
  public long getScopeKey() {
    return scopeKeyProperty.getValue();
  }

  @Override
  public Map<String, Object> getVariables() {
    return MsgPackConverter.convertToMap(variablesProperty.getValue());
  }

  public GlobalVariableRecord setVariables(final DirectBuffer variables) {
    variablesProperty.setValue(variables);
    return this;
  }

  @JsonIgnore
  public DirectBuffer getVariablesBuffer() {
    return variablesProperty.getValue();
  }

  @Override
  public String getTenantId() {
    return "cluster";
  }
}
