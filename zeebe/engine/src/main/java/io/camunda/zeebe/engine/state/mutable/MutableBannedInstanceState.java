/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.mutable;

import io.camunda.zeebe.engine.state.immutable.BannedInstanceState;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import java.util.function.Consumer;

public interface MutableBannedInstanceState extends BannedInstanceState {

  boolean tryToBanInstance(
      final TypedRecord<?> typedRecord, final Consumer<Long> onBanningInstance);

  void banProcessInstance(final long processInstanceKey);
}
