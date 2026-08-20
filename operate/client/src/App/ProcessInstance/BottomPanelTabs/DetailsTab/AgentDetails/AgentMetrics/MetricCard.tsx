/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useId} from 'react';
import {
  MetricCardContainer,
  MetricCardTitle,
  MetricCardValue,
  MetricHelperText,
  LimitMeter,
  LimitMeterContainer,
} from './styled';

type MetricCardProps = {
  title: string;
  value: number | string;
  children?: React.ReactNode;
};

const MetricCard: React.FC<MetricCardProps> = ({title, value, children}) => {
  const id = useId();
  return (
    <MetricCardContainer aria-labelledby={id}>
      <MetricCardTitle id={id}>{title}</MetricCardTitle>
      <MetricCardValue>{value.toLocaleString()}</MetricCardValue>
      {children}
    </MetricCardContainer>
  );
};

type LimitIndicatorProps = {
  current: number;
  limit: number;
};

const LimitIndicator: React.FC<LimitIndicatorProps> = ({current, limit}) => {
  if (limit < 0) {
    return null;
  }

  const usage = limit === 0 ? 100 : (current / limit) * 100;
  const clampedUsage = Math.max(0, Math.min(usage, 100));
  return (
    <LimitMeterContainer>
      <LimitMeter
        $percent={clampedUsage}
        aria-label="Usage limit"
        role="meter"
        aria-valuemin={0}
        aria-valuemax={100}
        aria-valuenow={clampedUsage}
        aria-valuetext={`${current} of ${limit} limit (${usage.toFixed(0)}%)`}
      ></LimitMeter>
      <MetricHelperText aria-hidden="true">
        of {limit.toLocaleString()} limit
      </MetricHelperText>
    </LimitMeterContainer>
  );
};

export {MetricCard, LimitIndicator};
