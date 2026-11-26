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

  /** Stores a new case and makes it findable by its root process instance. */
  void createCase(CaseRecord caseRecord);

  /** Replaces the stored case with the same case key. */
  void updateCase(CaseRecord caseRecord);
}
