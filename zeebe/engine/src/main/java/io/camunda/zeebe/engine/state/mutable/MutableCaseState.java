/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.mutable;

import io.camunda.zeebe.engine.state.immutable.CaseState;
import io.camunda.zeebe.protocol.impl.record.value.cases.CaseRecord;
import org.jspecify.annotations.NullMarked;

@NullMarked
public interface MutableCaseState extends CaseState {

  /** Stores the case and one entry per stage of the given record. */
  void create(long caseKey, CaseRecord record);

  /** Replaces the business ID of the given case, which must exist. */
  void updateBusinessId(long caseKey, String businessId);
}
