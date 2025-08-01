/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.appliers;

import io.camunda.zeebe.engine.state.TypedEventApplier;
import io.camunda.zeebe.engine.state.mutable.MutableUsageMetricState;
import io.camunda.zeebe.protocol.impl.record.value.decision.DecisionEvaluationRecord;
import io.camunda.zeebe.protocol.record.intent.DecisionEvaluationIntent;

public final class DecisionEvaluationV2Applier
    implements TypedEventApplier<DecisionEvaluationIntent, DecisionEvaluationRecord> {

  private final MutableUsageMetricState usageMetricState;

  public DecisionEvaluationV2Applier(final MutableUsageMetricState usageMetricState) {
    this.usageMetricState = usageMetricState;
  }

  @Override
  public void applyState(final long key, final DecisionEvaluationRecord value) {
    usageMetricState.recordEDIMetric(value.getTenantId());
  }
}
