/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

/**
 * Reads `--c3-sidebar-width` (set by C3NavigationV2 on :root) and returns its
 * pixel value. Supports `rem` and `px` units. Returns 0 when the variable is
 * unset (V1 surface), so callers can use the result as a left-edge offset
 * unconditionally.
 */
export default function getC3SidebarWidth(): number {
  if (typeof document === 'undefined') return 0;
  const rootStyle = getComputedStyle(document.documentElement);
  const raw = rootStyle.getPropertyValue('--c3-sidebar-width').trim();
  if (!raw) return 0;
  const value = parseFloat(raw);
  if (Number.isNaN(value)) return 0;
  if (raw.endsWith('rem')) {
    const rootFontSize = parseFloat(rootStyle.fontSize) || 16;
    return value * rootFontSize;
  }
  return value;
}
