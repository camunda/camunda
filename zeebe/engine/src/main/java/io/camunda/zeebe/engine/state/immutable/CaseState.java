/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.immutable;

import io.camunda.zeebe.protocol.impl.record.value.cases.CaseRecord;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public interface CaseState {

  /**
   * Returns the case with the given key.
   *
   * <p>The returned record is a fresh instance that the caller may change.
   *
   * @param caseKey the key of the case
   * @return the case, or {@code null} if there is none with this key
   */
  @Nullable CaseRecord getCase(long caseKey);

  /**
   * Returns the case of a root process instance.
   *
   * <p>The returned record is a fresh instance that the caller may change.
   *
   * @param processInstanceKey the key of the root process instance
   * @return the case, or {@code null} if the process instance has none
   */
  @Nullable CaseRecord getCaseByProcessInstanceKey(long processInstanceKey);
}
