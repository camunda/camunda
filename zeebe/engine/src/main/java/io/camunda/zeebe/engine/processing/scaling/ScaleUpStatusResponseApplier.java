/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.scaling;

import io.camunda.zeebe.engine.state.TypedEventApplier;
import io.camunda.zeebe.protocol.impl.record.value.scaling.ScaleRecord;
import io.camunda.zeebe.protocol.record.intent.HandlesIntent;
import io.camunda.zeebe.protocol.record.intent.scaling.ScaleIntent;

@HandlesIntent(intent = ScaleIntent.class, type = "STATUS_RESPONSE")
public class ScaleUpStatusResponseApplier implements TypedEventApplier<ScaleIntent, ScaleRecord> {

  @Override
  public void applyState(final long key, final ScaleRecord value) {}
}
