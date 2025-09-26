/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.variable;

import io.camunda.zeebe.msgpack.property.BinaryProperty;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.msgpack.value.StringValue;
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.value.ClusterVariableRecordValue;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.util.buffer.BufferUtil;

public class ClusterVariableRecord extends UnifiedRecordValue
    implements ClusterVariableRecordValue {

  private static final StringValue VARIABLE_KEY_KEY = new StringValue("variableKey");
  private static final StringValue NAME_KEY = new StringValue("name");
  private static final StringValue VALUE_KEY = new StringValue("value");
  private static final StringValue TENANT_ID_KEY = new StringValue("tenantId");

  private final LongProperty variableKeyProp = new LongProperty(VARIABLE_KEY_KEY, 0L);
  private final StringProperty nameProp = new StringProperty(NAME_KEY);
  private final BinaryProperty valueProp = new BinaryProperty(VALUE_KEY);
  private final StringProperty tenantIdProp =
      new StringProperty(TENANT_ID_KEY, TenantOwned.DEFAULT_TENANT_IDENTIFIER);

  public ClusterVariableRecord() {
    super(4);
    declareProperty(variableKeyProp)
        .declareProperty(nameProp)
        .declareProperty(valueProp)
        .declareProperty(tenantIdProp);
  }

  @Override
  public String getName() {
    return BufferUtil.bufferAsString(nameProp.getValue());
  }

  @Override
  public Object getValue() {
    return MsgPackConverter.convertToJson(valueProp.getValue());
  }

  @Override
  public long getKey() {
    return variableKeyProp.getValue();
  }

  @Override
  public String getTenantId() {
    return BufferUtil.bufferAsString(tenantIdProp.getValue());
  }
}
