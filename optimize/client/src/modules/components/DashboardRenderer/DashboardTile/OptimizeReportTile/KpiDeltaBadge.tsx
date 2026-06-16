/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Tag} from '@carbon/react';

import {
  classifyDelta,
  formatDelta,
  type DeltaDirection,
  type DeltaGoodDirection,
  type ValueUnit,
} from './comparison';

interface KpiDeltaBadgeProps {
  currentValue: number;
  priorValue: number | null | undefined;
  unit: ValueUnit;
  deltaGoodDirection: DeltaGoodDirection;
  periodLabel: string;
}

const TAG_TYPE: Record<DeltaDirection, 'green' | 'red' | 'gray'> = {
  good: 'green',
  bad: 'red',
  neutral: 'gray',
};

/**
 * Presents the period-over-period change for a KPI tile. Delta semantics and
 * formatting live in `comparison`; this component only maps them to a Tag.
 */
export default function KpiDeltaBadge({
  currentValue,
  priorValue,
  unit,
  deltaGoodDirection,
  periodLabel,
}: KpiDeltaBadgeProps): JSX.Element {
  if (priorValue == null) {
    return (
      <Tag size="sm" type="gray">
        — {periodLabel}
      </Tag>
    );
  }

  const {delta, direction} = classifyDelta(currentValue, priorValue, deltaGoodDirection);

  return (
    <Tag size="sm" type={TAG_TYPE[direction]}>
      {formatDelta(delta, unit)} {periodLabel}
    </Tag>
  );
}
