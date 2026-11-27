/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.monitoring.detector;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * A sampled detector that compares one value per child, for example per partition, against {@link
 * ConditionThresholds}. The issue message is fixed when the condition starts, so the health tree
 * does not change on every sample while the condition holds.
 */
public abstract class ThresholdDetector extends HealthDetector implements SampledDetector {

  private final ConditionThresholds thresholds;
  private final Map<String, ThresholdCondition> conditions = new HashMap<>();

  protected ThresholdDetector(final String name, final ConditionThresholds thresholds) {
    super(name);
    this.thresholds = thresholds;
  }

  /**
   * Feeds one sample of one child.
   *
   * @param child the child the value belongs to, for example a partition's component name
   * @param value the sampled value, in this detector's unit
   */
  protected void observe(final String child, final double value, final Instant now) {
    final var condition =
        conditions.computeIfAbsent(child, ignored -> new ThresholdCondition(thresholds));
    final var started = condition.update(value, now) && condition.isActive();
    if (started) {
      markDegraded(child, describe(child, condition.valueAtStart()), condition.since());
    } else if (!condition.isActive()) {
      // a no-op unless the child is new or its condition just ended
      markHealthy(child);
    }
  }

  /** Forgets the children that were not observed in this sample. */
  protected void forgetOthers(final Set<String> observed) {
    conditions.keySet().retainAll(observed);
    retainOnly(observed);
  }

  protected ConditionThresholds thresholds() {
    return thresholds;
  }

  /**
   * The issue message of a condition that just started.
   *
   * @param value the sample that started it
   */
  protected abstract String describe(String child, double value);
}
