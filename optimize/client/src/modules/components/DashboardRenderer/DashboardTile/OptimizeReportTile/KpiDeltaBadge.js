/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Tag} from '@carbon/react';

/**
 * Renders a coloured pill badge showing the delta between the current period
 * and the prior period for a KPI tile.
 *
 * Colour logic:
 *   deltaGoodDirection='up'   → positive delta = green, negative = red
 *   deltaGoodDirection='down' → negative delta = green, positive = red
 *
 * Renders nothing when priorValue is null/undefined (no comparison data).
 *
 * @param {object} props
 * @param {number}          props.currentValue        - Value for the current period.
 * @param {number|null}     props.priorValue          - Value for the prior period.
 * @param {'ms'|'%'|''}    props.unit                - Unit of the measure.
 * @param {'up'|'down'}     props.deltaGoodDirection  - Which direction indicates improvement.
 */
export default function KpiDeltaBadge({currentValue, priorValue, unit, deltaGoodDirection}) {
  if (priorValue === null || priorValue === undefined) {
    return null;
  }

  const delta = currentValue - priorValue;
  const isPositiveDelta = delta >= 0;
  const isGood = deltaGoodDirection === 'up' ? isPositiveDelta : !isPositiveDelta;

  return (
    <Tag className="KpiDeltaBadge" type={isGood ? 'green' : 'red'} size="sm">
      {formatDelta(delta, unit)} WoW
    </Tag>
  );
}

// ---------------------------------------------------------------------------
// Formatting
// ---------------------------------------------------------------------------

/**
 * Formats the absolute delta for display.
 *
 * - duration (ms): converts to seconds when magnitude ≥ 1000 ms.
 * - percentage:    appends a % sign.
 * - count/other:   rounds to nearest integer.
 */
function formatDelta(delta, unit) {
  const sign = delta >= 0 ? '+' : '';

  if (unit === 'ms') {
    const absMs = Math.abs(delta);
    if (absMs >= 1000) {
      return `${sign}${(delta / 1000).toFixed(1)}s`;
    }
    return `${sign}${Math.round(delta)}ms`;
  }

  if (unit === '%') {
    return `${sign}${Math.abs(delta) < 1 ? delta.toFixed(2) : delta.toFixed(1)}%`;
  }

  // frequency / count
  return `${sign}${Math.round(delta)}`;
}
