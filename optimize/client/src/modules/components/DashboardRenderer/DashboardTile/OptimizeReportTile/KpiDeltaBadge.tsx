/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Tag} from '@carbon/react';

interface KpiDeltaBadgeProps {
  currentValue: number;
  priorValue: number | null | undefined;
  unit: 'ms' | '%' | '';
  deltaGoodDirection: 'up' | 'down';
}

export default function KpiDeltaBadge({
  currentValue,
  priorValue,
  unit,
  deltaGoodDirection,
}: KpiDeltaBadgeProps): JSX.Element | null {
  if (priorValue == null) {
    return null;
  }

  const delta = currentValue - priorValue;
  const isPositive = delta >= 0;
  const isGood =
    (deltaGoodDirection === 'up' && isPositive) ||
    (deltaGoodDirection === 'down' && !isPositive);

  const sign = isPositive ? '+' : '-';
  const absDelta = Math.abs(delta);

  let formatted: string;
  if (unit === 'ms') {
    if (absDelta >= 1000) {
      formatted = `${sign}${(absDelta / 1000).toFixed(1)}s`;
    } else {
      formatted = `${sign}${Math.round(absDelta)}ms`;
    }
  } else if (unit === '%') {
    const decimals = absDelta < 1 ? 1 : 0;
    formatted = `${sign}${absDelta.toFixed(decimals)}%`;
  } else {
    formatted = `${sign}${Math.round(absDelta)}`;
  }

  return (
    <Tag size="sm" type={isGood ? 'green' : 'red'}>
      {formatted} WoW
    </Tag>
  );
}
