/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.appliers;

import io.camunda.zeebe.engine.state.TypedEventApplier;
import io.camunda.zeebe.engine.state.mutable.MutableMultiInstanceState;
import io.camunda.zeebe.protocol.impl.record.value.multiinstance.MultiInstanceRecord;
import io.camunda.zeebe.protocol.record.intent.MultiInstanceIntent;

public class MultiInstanceInputCollectionEvaluatedApplier
    implements TypedEventApplier<MultiInstanceIntent, MultiInstanceRecord> {

  final MutableMultiInstanceState multiInstanceState;

  public MultiInstanceInputCollectionEvaluatedApplier(
      final MutableMultiInstanceState multiInstanceState) {
    this.multiInstanceState = multiInstanceState;
  }

  @Override
  public void applyState(final long multiInstanceBodyKey, final MultiInstanceRecord value) {
    multiInstanceState.insertInputCollection(
        multiInstanceBodyKey, value.getInputCollectionBuffers());
  }
}
