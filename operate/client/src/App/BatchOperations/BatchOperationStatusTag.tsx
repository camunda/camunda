/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {
  CheckmarkFilled,
  ErrorFilled,
  InProgress,
  PauseFilled,
  WarningAltFilled,
  Pending,
  SkipForwardFilled,
  CircleDash,
  Misuse,
} from '@carbon/icons-react';
import type {
  BatchOperationState,
  BatchOperationItemState,
} from 'modules/mocks/batchOperations';

type BatchOperationStatusTagProps = {
  status: BatchOperationState | BatchOperationItemState;
  size?: 'sm' | 'md';
};

type StatusConfig = {
  Icon: typeof CheckmarkFilled;
  color: string;
  label: string;
};

const getStatusConfig = (
  status: BatchOperationState | BatchOperationItemState,
): StatusConfig => {
  switch (status) {
    case 'COMPLETED':
      return {
        Icon: CheckmarkFilled,
        color: 'var(--cds-support-success)',
        label: 'Completed',
      };
    case 'ACTIVE':
      return {
        Icon: InProgress,
        color: 'var(--cds-support-info)',
        label: 'Active',
      };
    case 'SUSPENDED':
      return {
        Icon: PauseFilled,
        color: 'var(--cds-support-warning)',
        label: 'Suspended',
      };
    case 'CANCELLED':
      return {
        Icon: Misuse,
        color: '#ff832b',
        label: 'Cancelled',
      };
    case 'FAILED':
      return {
        Icon: ErrorFilled,
        color: 'var(--cds-support-error)',
        label: 'Failed',
      };
    case 'CREATED':
      return {
        Icon: Pending,
        color: 'var(--cds-status-gray)',
        label: 'Created',
      };
    case 'PARTIALLY_COMPLETED':
      return {
        Icon: WarningAltFilled,
        color: 'var(--cds-support-warning)',
        label: 'Partially completed',
      };
    case 'SKIPPED':
      return {
        Icon: SkipForwardFilled,
        color: 'var(--cds-text-secondary)',
        label: 'Skipped',
      };
    default:
      return {
        Icon: CircleDash,
        color: 'var(--cds-text-secondary)',
        label: status,
      };
  }
};

const BatchOperationStatusTag: React.FC<BatchOperationStatusTagProps> = ({
  status,
}) => {
  const {Icon, color, label} = getStatusConfig(status);

  return (
    <div
      style={{
        display: 'flex',
        alignItems: 'center',
        gap: 'var(--cds-spacing-03)',
      }}
    >
      <Icon size={16} style={{color}} />
      <span>{label}</span>
    </div>
  );
};

export {BatchOperationStatusTag};

