/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import IconIndicator from '@carbon/react/lib/components/IconIndicator';
import type {IconIndicatorProps} from '@carbon/react/lib/components/IconIndicator';
import {MisuseOutline, PauseFilled, PauseOutlineFilled, SkipForwardOutlineSolid} from '@carbon/icons-react';
import type {
  BatchOperationState,
  BatchOperationItemState,
} from 'modules/mocks/batchOperations';

type BatchOperationStatusTagProps = {
  status: BatchOperationState | BatchOperationItemState;
  size?: 'sm' | 'md';
};

type StatusConfig =
  | {
      useIconIndicator: true;
      kind: IconIndicatorProps['kind'];
      label: string;
    }
  | {
      useIconIndicator: false;
      Icon: typeof PauseFilled;
      color: string;
      label: string;
    };

const getStatusConfig = (
  status: BatchOperationState | BatchOperationItemState,
): StatusConfig => {
  switch (status) {
    case 'COMPLETED':
      return {
        useIconIndicator: true,
        kind: 'succeeded',
        label: 'Completed',
      };
    case 'ACTIVE':
      return {
        useIconIndicator: true,
        kind: 'in-progress',
        label: 'Active',
      };
    case 'SUSPENDED':
      return {
        useIconIndicator: false,
        Icon: PauseOutlineFilled,
        color: 'var(--cds-status-gray)',
        label: 'Suspended',
      };
    case 'CANCELLED':
      return {
        useIconIndicator: false,
        Icon: MisuseOutline,
        color: 'var(--cds-status-red)',
        label: 'Cancelled',
      };
    case 'FAILED':
      return {
        useIconIndicator: true,
        kind: 'failed',
        label: 'Failed',
      };
    case 'CREATED':
      return {
        useIconIndicator: true,
        kind: 'not-started',
        label: 'Created',
      };
    case 'PARTIALLY_COMPLETED':
      return {
        useIconIndicator: true,
        kind: 'caution-minor',
        label: 'Partially completed',
      };
    case 'SKIPPED':
      return {
        useIconIndicator: false,
        Icon: SkipForwardOutlineSolid,
        color: 'var(--cds-status-gray)',
        label: 'Skipped',
      };
    default:
      return {
        useIconIndicator: true,
        kind: 'unknown',
        label: status,
      };
  }
};

const BatchOperationStatusTag: React.FC<BatchOperationStatusTagProps> = ({
  status,
  size = 'sm',
}) => {
  const config = getStatusConfig(status);
  const iconSize = size === 'sm' ? 16 : 20;

  if (config.useIconIndicator) {
    return (
      <IconIndicator kind={config.kind} label={config.label} size={iconSize} />
    );
  }

  // Custom icon rendering for states that don't have IconIndicator support
  const {Icon, color, label} = config;
  const classNames = `cds--icon-indicator${iconSize === 20 ? ' cds--icon-indicator--20' : ''}`;

  return (
    <div className={classNames}>
      <Icon size={iconSize} style={{color}} />
      {label}
    </div>
  );
};

export {BatchOperationStatusTag};

