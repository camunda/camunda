/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

// Fallbacks used if the design-system chart tokens are not resolvable at runtime. Ordered to match
// --chart-1..--chart-5.
const FALLBACK = ["#4658ff", "#ff5000", "#00a05a", "#8b5cf6", "#e11d48"];

/**
 * Resolves the design-system chart token {@code --chart-<n>} (1-based, wraps at 5). Tokens are
 * scoped to the {@code .c4-ui} element, so we read from there; falls back to a fixed palette when
 * the token is absent (e.g. before the provider has mounted).
 */
export function chartColor(index: number): string {
  const slot = (index % 5) + 1;
  if (typeof document !== "undefined") {
    const scope = document.querySelector(".c4-ui") ?? document.documentElement;
    const value = getComputedStyle(scope as Element)
      .getPropertyValue(`--chart-${slot}`)
      .trim();
    if (value) {
      return value;
    }
  }
  return FALLBACK[slot - 1];
}
