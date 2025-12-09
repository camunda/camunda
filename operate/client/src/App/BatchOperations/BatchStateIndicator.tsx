/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {BatchOperationState} from '@camunda/camunda-api-zod-schemas/8.8';
import {
  CheckmarkFilled,
  CircleDash,
  ErrorFilled,
  Incomplete,
  InProgress,
  MisuseOutline,
  PauseOutlineFilled,
  UnknownFilled,
  type CarbonIconType,
} from '@carbon/icons-react';
import {BatchStateIndicatorContainer} from './styled';

type BatchStateIndicatorProps = {
  status: BatchOperationState;
};

const BatchStateIndicator: React.FC<BatchStateIndicatorProps> = ({status}) => {
  const config = getStatusConfig(status);

  const {Icon, color, label} = config;
  return (
    <BatchStateIndicatorContainer
      role="status"
      aria-label={`Batch operation status: ${label}`}
    >
      <Icon style={{color}} aria-hidden="true" focusable="false" />
      {label}
    </BatchStateIndicatorContainer>
  );
};

export {BatchStateIndicator};

type StatusConfig = {
  Icon: CarbonIconType;
  color: string;
  label: string;
};

const getStatusConfig = (status: BatchOperationState): StatusConfig => {
  switch (status) {
    case 'COMPLETED':
      return {
        Icon: CheckmarkFilled,
        color: 'var(--cds-status-green)',
        label: 'Completed',
      };
    case 'ACTIVE':
      return {
        Icon: InProgress,
        color: 'var(--cds-status-blue)',
        label: 'Active',
      };
    case 'SUSPENDED':
      return {
        Icon: PauseOutlineFilled,
        color: 'var(--cds-status-gray)',
        label: 'Suspended',
      };
    case 'CANCELED':
      return {
        Icon: MisuseOutline,
        color: 'var(--cds-status-red)',
        label: 'Canceled',
      };
    case 'FAILED':
      return {
        Icon: ErrorFilled,
        color: 'var(--cds-status-red)',
        label: 'Failed',
      };
    case 'CREATED':
      return {
        Icon: CircleDash,
        color: 'var(--cds-status-gray)',
        label: 'Created',
      };
    case 'PARTIALLY_COMPLETED':
      return {
        Icon: Incomplete,
        color: 'var(--cds-status-blue)',
        label: 'Partially completed',
      };
    default:
      return {
        Icon: UnknownFilled,
        color: 'var(--cds-status-gray)',
        label: status,
      };
  }
};
