/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.expression;

import static io.camunda.zeebe.util.buffer.BufferUtil.bufferAsString;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.camunda.zeebe.msgpack.property.ArrayProperty;
import io.camunda.zeebe.msgpack.property.DocumentProperty;
import io.camunda.zeebe.msgpack.property.EnumProperty;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.msgpack.value.StringValue;
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.value.ExpressionRecordValue;
import io.camunda.zeebe.protocol.record.value.ExpressionScopeType;
import io.camunda.zeebe.protocol.record.value.ImmutableEvaluationWarning;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.agrona.DirectBuffer;

public final class ExpressionRecord extends UnifiedRecordValue
    implements ExpressionRecordValue {

  // Static StringValue keys for property names
  private static final StringValue EXPRESSION_KEY = new StringValue("expression");
  private static final StringValue CONTEXT_KEY = new StringValue("context");
  private static final StringValue SCOPE_TYPE_KEY = new StringValue("scopeType");
  private static final StringValue PROCESS_INSTANCE_KEY_KEY = new StringValue("processInstanceKey");
  private static final StringValue RESULT_KEY = new StringValue("result");
  private static final StringValue RESULT_TYPE_KEY = new StringValue("resultType");
  private static final StringValue WARNINGS_KEY = new StringValue("warnings");
  private static final StringValue REJECTION_REASON_KEY = new StringValue("rejectionReason");
  private static final StringValue TENANT_ID_KEY = new StringValue("tenantId");

  private final StringProperty expressionProp = new StringProperty(EXPRESSION_KEY);
  private final DocumentProperty contextProp = new DocumentProperty(CONTEXT_KEY);
  private final EnumProperty<ExpressionScopeType> scopeTypeProp =
      new EnumProperty<>(SCOPE_TYPE_KEY, ExpressionScopeType.class, ExpressionScopeType.NONE);
  private final LongProperty processInstanceKeyProp =
      new LongProperty(PROCESS_INSTANCE_KEY_KEY, -1L);
  private final DocumentProperty resultProp = new DocumentProperty(RESULT_KEY);
  private final StringProperty resultTypeProp = new StringProperty(RESULT_TYPE_KEY, "");
  private final ArrayProperty<EvaluationWarningRecord> warningsProp =
      new ArrayProperty<>(WARNINGS_KEY, EvaluationWarningRecord::new);
  private final StringProperty rejectionReasonProp = new StringProperty(REJECTION_REASON_KEY, "");
  private final StringProperty tenantIdProp =
      new StringProperty(TENANT_ID_KEY, TenantOwned.DEFAULT_TENANT_IDENTIFIER);

  public ExpressionRecord() {
    super(9);
    declareProperty(expressionProp)
        .declareProperty(contextProp)
        .declareProperty(scopeTypeProp)
        .declareProperty(processInstanceKeyProp)
        .declareProperty(resultProp)
        .declareProperty(resultTypeProp)
        .declareProperty(warningsProp)
        .declareProperty(rejectionReasonProp)
        .declareProperty(tenantIdProp);
  }

  public void wrap(final ExpressionRecord record) {
    setExpression(record.getExpressionBuffer())
        .setContext(record.getContextBuffer())
        .setScopeType(record.getScopeType())
        .setProcessInstanceKey(record.getProcessInstanceKey())
        .setResult(record.getResultBuffer())
        .setResultType(record.getResultType())
        .setRejectionReason(record.getRejectionReason())
        .setTenantId(record.getTenantId());

    // Copy warnings
    warningsProp.reset();
    for (final EvaluationWarning warning : record.getWarnings()) {
      final var warningRecord = new EvaluationWarningRecord();
      warningRecord.setType(warning.getType()).setMessage(warning.getMessage());
      warningsProp.add().wrap(warningRecord);
    }
  }

  @Override
  public String getExpression() {
    return bufferAsString(expressionProp.getValue());
  }

  public ExpressionRecord setExpression(final String expression) {
    expressionProp.setValue(expression);
    return this;
  }

  public ExpressionRecord setExpression(final DirectBuffer expression) {
    expressionProp.setValue(expression);
    return this;
  }

  @Override
  public Map<String, Object> getContext() {
    return MsgPackConverter.convertToMap(contextProp.getValue());
  }

  public ExpressionRecord setContext(final DirectBuffer context) {
    contextProp.setValue(context);
    return this;
  }

  @Override
  public ExpressionScopeType getScopeType() {
    return scopeTypeProp.getValue();
  }

  public ExpressionRecord setScopeType(final ExpressionScopeType scopeType) {
    scopeTypeProp.setValue(scopeType);
    return this;
  }

  @Override
  public long getProcessInstanceKey() {
    return processInstanceKeyProp.getValue();
  }

  public ExpressionRecord setProcessInstanceKey(final long processInstanceKey) {
    processInstanceKeyProp.setValue(processInstanceKey);
    return this;
  }

  @Override
  public Map<String, Object> getResult() {
    return MsgPackConverter.convertToMap(resultProp.getValue());
  }

  public ExpressionRecord setResult(final DirectBuffer result) {
    resultProp.setValue(result);
    return this;
  }

  @Override
  public String getResultType() {
    return bufferAsString(resultTypeProp.getValue());
  }

  public ExpressionRecord setResultType(final String resultType) {
    resultTypeProp.setValue(resultType);
    return this;
  }

  @Override
  public List<EvaluationWarning> getWarnings() {
    final List<EvaluationWarning> warnings = new ArrayList<>();
    for (final EvaluationWarningRecord warning : warningsProp) {
      warnings.add(
          ImmutableEvaluationWarning.builder()
              .withType(warning.getType())
              .withMessage(warning.getMessage())
              .build());
    }
    return warnings;
  }

  @Override
  public String getRejectionReason() {
    return bufferAsString(rejectionReasonProp.getValue());
  }

  public ExpressionRecord setRejectionReason(final String rejectionReason) {
    rejectionReasonProp.setValue(rejectionReason);
    return this;
  }

  @JsonIgnore
  public DirectBuffer getExpressionBuffer() {
    return expressionProp.getValue();
  }

  @JsonIgnore
  public DirectBuffer getContextBuffer() {
    return contextProp.getValue();
  }

  @JsonIgnore
  public DirectBuffer getResultBuffer() {
    return resultProp.getValue();
  }

  public ArrayProperty<EvaluationWarningRecord> warnings() {
    return warningsProp;
  }

  @Override
  public String getTenantId() {
    return bufferAsString(tenantIdProp.getValue());
  }

  public ExpressionRecord setTenantId(final String tenantId) {
    tenantIdProp.setValue(tenantId);
    return this;
  }
}
