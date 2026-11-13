/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.immutable;

import io.camunda.zeebe.engine.state.cases.PersistedCase;
import io.camunda.zeebe.engine.state.cases.PersistedCaseStage;
import java.util.Optional;
import java.util.function.BiConsumer;
import org.jspecify.annotations.NullMarked;

/**
 * The cases of the partition. A case is keyed by the key of its root process instance, as a case
 * has exactly one root process instance.
 */
@NullMarked
public interface CaseState {

  /** Whether a case exists for the given process instance key. */
  boolean exists(long caseKey);

  /** The case with the given key, as a copy that stays valid after the next state access. */
  Optional<PersistedCase> getCase(long caseKey);

  /**
   * Visits the stages of the given case with their element ID. The visited stage is reused between
   * calls: read what is needed from it inside the consumer, do not retain it.
   */
  void forEachStage(long caseKey, BiConsumer<String, PersistedCaseStage> consumer);
}
