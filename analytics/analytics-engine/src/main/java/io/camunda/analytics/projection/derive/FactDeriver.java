/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.projection.derive;

import io.camunda.analytics.projection.SourceRecord;

/**
 * Derives facts for one {@code (ValueType, Intent)} transition as a pure projection of the
 * materialized rows — read-only (eviction is an applier's job). Each deriver is declared for one
 * transition and holds its collaborators (the read-only projection state, the fact sink) as fields,
 * so deriving is a pure function of the {@link SourceRecord}.
 */
@FunctionalInterface
public interface FactDeriver {

  void derive(SourceRecord source);
}
