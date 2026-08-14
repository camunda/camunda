/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.businessvalue;

/**
 * Shared verdict function: paired JS twin ships in camunda-hub and both implementations are pinned
 * to the same JSON fixture at {@code
 * optimize/backend/src/test/resources/businessvalue/verdict-cases.json}. Any rule change touches
 * that fixture first so drift between languages fails a contract test in whichever side was
 * forgotten.
 */
public final class BusinessValueVerdict {

  private BusinessValueVerdict() {}

  public static Verdict verdict(
      final Kpi kpi, final Double value, final Double target, final Direction direction) {
    if (target == null || value == null) {
      return new Verdict(kpi, value, target, null, null, null);
    }
    final boolean met = direction == Direction.LOWER_IS_BETTER ? value <= target : value >= target;
    final double gapPct = Math.abs((value - target) / target) * 100.0;
    final String label = value.compareTo(target) == 0 ? "at" : value > target ? "over" : "under";
    return new Verdict(kpi, value, target, met, gapPct, label);
  }
}
