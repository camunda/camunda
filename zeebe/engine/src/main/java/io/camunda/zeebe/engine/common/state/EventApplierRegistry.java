/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.common.state;

import io.camunda.zeebe.protocol.record.intent.Intent;

public interface EventApplierRegistry {

  <I extends Intent> EventApplierRegistry register(I intent, TypedEventApplier<I, ?> eventApplier);

  <I extends Intent> EventApplierRegistry register(
      I intent, int version, TypedEventApplier<I, ?> eventApplier);
}
