/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.state;

import java.time.Instant;
import org.jspecify.annotations.NullMarked;

/**
 * Minimal record of a completed {@link PhasedChangePlan}, retained so that:
 *
 * <ul>
 *   <li>The next plan's ID can be derived monotonically ({@code lastChange.id() + 1}).
 *   <li>Operators can observe when the last cluster-spanning change finished and whether it
 *       succeeded.
 * </ul>
 */
@NullMarked
public record CompletedPhasedChange(
    long id, PhasedChangePlanStatus status, Instant startedAt, Instant completedAt) {}
