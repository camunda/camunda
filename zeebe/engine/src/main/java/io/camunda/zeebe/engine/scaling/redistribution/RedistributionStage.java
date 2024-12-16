/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.scaling.redistribution;

import java.util.Objects;

/**
 * Redistribution is organized in stages. Each stage needs to be defined here and mapped to an index
 * that determines the order of stages. Stages with a lower index are completed before stages with
 * higher indexes.
 */
public sealed interface RedistributionStage {

  /** All stages need to be mapped to a unique index here. Use */
  static int stageToIndex(final RedistributionStage stage) {
    return switch (stage) {
      case final Done ignored -> 0;
      case final Deployments ignored -> 1;
    };
  }

  static RedistributionStage indexToStage(final int index) {
    return switch (index) {
      case 0 -> new Done();
      case 1 -> new Deployments();
      default -> null;
    };
  }

  static RedistributionStage nextStage(final RedistributionStage currentStage) {
    final var nextIndex = stageToIndex(currentStage) + 1;
    final var nextStage = indexToStage(nextIndex);
    return Objects.requireNonNullElseGet(nextStage, Done::new);
  }

  record Done() implements RedistributionStage {}

  record Deployments() implements RedistributionStage {}
}
