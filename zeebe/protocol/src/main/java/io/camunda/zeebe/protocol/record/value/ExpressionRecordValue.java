/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.record.value;

import io.camunda.zeebe.protocol.record.ImmutableProtocol;
import io.camunda.zeebe.protocol.record.RecordValue;
import java.util.List;
import java.util.Map;
import org.immutables.value.Value;

@Value.Immutable
@ImmutableProtocol(builder = ImmutableExpressionRecordValue.Builder.class)
public interface ExpressionRecordValue extends RecordValue, TenantOwned {

  String getExpression();

  Map<String, Object> getContext();

  ExpressionScopeType getScopeType();

  long getProcessInstanceKey();

  Map<String, Object> getResult();

  String getResultType();

  List<EvaluationWarning> getWarnings();

  String getRejectionReason();

  /** Represents a warning that occurred during FEEL expression evaluation. */
  @Value.Immutable
  @ImmutableProtocol(builder = ImmutableEvaluationWarning.Builder.class)
  interface EvaluationWarning {
    /**
     * @return the type or category of the warning
     */
    String getType();

    /**
     * @return a description of the warning
     */
    String getMessage();
  }
}
