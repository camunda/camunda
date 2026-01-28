/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.expression;

import io.camunda.zeebe.msgpack.property.ArrayProperty;
import io.camunda.zeebe.msgpack.property.BinaryProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.msgpack.value.StringValue;
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.value.ExpressionRecordValue;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public class ExpressionRecord extends UnifiedRecordValue implements ExpressionRecordValue {

  private static final StringValue EXPRESSION_KEY = new StringValue("expression");
  private static final StringValue RESULT_VALUE_KEY = new StringValue("resultValue");
  private static final StringValue WARNINGS_KEY = new StringValue("warnings");
  private static final StringValue TENANT_ID_KEY = new StringValue("tenantId");

  private final StringProperty expressionProp = new StringProperty(EXPRESSION_KEY);

  private final BinaryProperty resultValueProp =
      new BinaryProperty(RESULT_VALUE_KEY, new UnsafeBuffer(new byte[] {0}));

  private final ArrayProperty<StringValue> warningsProp =
      new ArrayProperty<>(WARNINGS_KEY, StringValue::new);
  private final StringProperty tenantIdProp = new StringProperty(TENANT_ID_KEY, "");

  public ExpressionRecord() {
    super(4);
    declareProperty(expressionProp)
        .declareProperty(resultValueProp)
        .declareProperty(warningsProp)
        .declareProperty(tenantIdProp);
  }

  @Override
  public String getExpression() {
    return BufferUtil.bufferAsString(expressionProp.getValue());
  }

  public ExpressionRecord setExpression(final String expression) {
    expressionProp.setValue(expression);
    return this;
  }

  @Override
  public Object getResultValue() {
    return MsgPackConverter.convertToObject(resultValueProp.getValue(), Object.class);
  }

  @Override
  public List<String> getWarnings() {
    return StreamSupport.stream(warningsProp.spliterator(), false)
        .map(StringValue::getValue)
        .map(BufferUtil::bufferAsString)
        .collect(Collectors.toList());
  }

  public ExpressionRecord setWarnings(final List<String> warnings) {
    warningsProp.reset();
    if (warnings != null) {
      warnings.forEach(warning -> warningsProp.add().wrap(BufferUtil.wrapString(warning)));
    }
    return this;
  }

  public ExpressionRecord setResultValue(final DirectBuffer value) {
    resultValueProp.setValue(value);
    return this;
  }

  @Override
  public String getTenantId() {
    return BufferUtil.bufferAsString(tenantIdProp.getValue());
  }

  public ExpressionRecord setTenantId(final String tenantId) {
    tenantIdProp.setValue(tenantId);
    return this;
  }
}
