/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import React from 'react';
import {Item, ItemGroup} from './styled';
import {Tooltip} from '@carbon/react';
import {
  Checkmark,
  CircleDash,
} from '@carbon/icons-react/lib/generated/bucket-3';
import {ErrorOutline} from '@carbon/icons-react/lib/generated/bucket-6';
import {Pending} from '@carbon/icons-react';

const BatchItemsCount: React.FC<{
  operationsCompletedCount: number;
  operationsFailedCount: number;
  operationsTotalCount: number;
}> = ({
  operationsCompletedCount,
  operationsFailedCount,
  operationsTotalCount,
}) => {
  const successCount = operationsCompletedCount;
  const failedCount = operationsFailedCount;
  const pendingCount = operationsTotalCount - successCount - failedCount;
  const hasAnyProgress = successCount > 0 || failedCount > 0;

  if (!hasAnyProgress && pendingCount > 0) {
    const description = 'not started';
    return (
      <Tooltip description={description} align="bottom">
        <Item color="var(--cds-status-gray)" aria-label={description}>
          <CircleDash aria-hidden="true" focusable="false" /> 0
        </Item>
      </Tooltip>
    );
  }

  const STATUS_CONFIG = [
    {
      key: 'successful',
      count: successCount,
      label: 'successful',
      Icon: Checkmark,
      color: 'var(--cds-status-green)',
    },
    {
      key: 'failed',
      count: failedCount,
      label: 'failed',
      Icon: ErrorOutline,
      color: 'var(--cds-status-red)',
    },
    {
      key: 'pending',
      count: pendingCount,
      label: 'pending',
      Icon: Pending,
      color: 'var(--cds-status-gray)',
    },
  ];

  return (
    <ItemGroup>
      {STATUS_CONFIG.filter(({count}) => count > 0).map(
        ({key, count, label, Icon, color}) => {
          const description = `${count.toLocaleString()} ${label}`;
          return (
            <Tooltip key={key} description={description} align="bottom">
              <Item color={color} role="status" aria-label={description}>
                <Icon aria-hidden="true" focusable="false" />
                {formatCount(count)}
              </Item>
            </Tooltip>
          );
        },
      )}
    </ItemGroup>
  );
};

export {BatchItemsCount};

const formatCount = (count: number): string => {
  return Intl.NumberFormat('en', {
    notation: 'compact',
    maximumFractionDigits: 1,
  }).format(count);
};
