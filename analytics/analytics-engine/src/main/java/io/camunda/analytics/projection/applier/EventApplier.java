/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.projection.applier;

import io.camunda.analytics.projection.SourceRecord;

/**
 * Folds one {@code (ValueType, Intent)} transition into the materialized projection — the sole
 * mutator of the read-model, mirroring the workflow engine's {@code EventApplier}. Each applier is
 * declared for one transition and holds its collaborators (the projection state, timing) as fields,
 * so applying is a pure function of the {@link SourceRecord}. The entity lifecycle is reflected by
 * the <em>set</em> of registered appliers (activate / complete / …), not a fixed interface.
 */
@FunctionalInterface
public interface EventApplier {

  void apply(SourceRecord source);
}
