/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {BatchOperationType} from '@vzeta/camunda-api-zod-schemas/8.8';
import {CheckmarkFilled, StatusContainer, Text, WarningFilled} from './styled';
import pluralSuffix from 'modules/utils/pluralSuffix';

type Props = {
  type: BatchOperationType;
  failedCount?: number;
  completedCount?: number;
};

const getSuccessMessage = (
  type: BatchOperationType,
  completedCount: number,
): string => {
  if (type === 'RESOLVE_INCIDENT') {
    return `${completedCount} ${completedCount === 1 ? 'retry' : 'retries'} succeeded`;
  }

  return `${pluralSuffix(completedCount, 'instance')} succeeded`;
};

const getFailureMessage = (
  type: BatchOperationType,
  failedCount: number,
  completedCount: number,
): string => {
  if (type === 'RESOLVE_INCIDENT') {
    if (completedCount > 0) {
      return `${failedCount} failed`;
    }
    return `${failedCount} ${failedCount === 1 ? 'retry' : 'retries'} failed`;
  }

  if (completedCount > 0) {
    return `${failedCount} failed`;
  }

  return `${pluralSuffix(failedCount, 'instance')} failed`;
};

const OperationEntryStatus: React.FC<Props> = ({
  type,
  failedCount = 0,
  completedCount = 0,
}) => {
  return (
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
  );
};

export {OperationEntryStatus};
