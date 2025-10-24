/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {
  BatchOperationState,
  BatchOperationType,
} from '@camunda/camunda-api-zod-schemas/8.8';
import {CheckmarkFilled, StatusContainer, Text, WarningFilled} from './styled';
import pluralSuffix from 'modules/utils/pluralSuffix';
import {Stack} from '@carbon/react';
import {PartiallyCompleted} from './PartiallyCompleted';
import {Failed} from './Failed';

type Props = {
  type: BatchOperationType;
  failedCount?: number;
  completedCount?: number;
  state: BatchOperationState;
};

const getSuccessMessage = (
  type: BatchOperationType,
  completedCount: number,
): string => {
  if (type === 'RESOLVE_INCIDENT') {
    return `${completedCount} ${completedCount === 1 ? 'retry' : 'retries'} succeeded`;
  }

  return `${pluralSuffix(completedCount, 'operation')} succeeded`;
};

const getFailureMessage = (
  type: BatchOperationType,
  failedCount: number,
  completedCount: number,
): string => {
  if (type === 'RESOLVE_INCIDENT') {
    if (completedCount > 0) {
      return `${failedCount} rejected`;
    }
    return `${failedCount} ${failedCount === 1 ? 'retry' : 'retries'} rejected`;
  }

  if (completedCount > 0) {
    return `${failedCount} failed`;
  }

  return `${pluralSuffix(failedCount, 'operation')} failed`;
};

const OperationEntryStatus: React.FC<Props> = ({
  type,
  failedCount = 0,
  completedCount = 0,
  state,
}) => {
  return (
    <Stack gap={3}>
      {state === 'PARTIALLY_COMPLETED' && <PartiallyCompleted />}
      {state === 'FAILED' && <Failed />}
      <StatusContainer>
        {completedCount > 0 ? (
          <>
            <CheckmarkFilled />
            <Text>{getSuccessMessage(type, completedCount)}</Text>
          </>
        ) : null}
        {completedCount > 0 && failedCount > 0 ? ' / ' : null}
        {failedCount ? (
          <>
            <WarningFilled />
            <Text>{getFailureMessage(type, failedCount, completedCount)}</Text>
          </>
        ) : null}
      </StatusContainer>
    </Stack>
  );
};

export {OperationEntryStatus};
