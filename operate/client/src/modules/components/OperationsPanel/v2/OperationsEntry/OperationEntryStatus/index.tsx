/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {BatchOperationType} from '@vzeta/camunda-api-zod-schemas/8.8';
import {
  CheckmarkFilled,
  StatusContainer,
  Text,
  WarningFilled,
} from 'modules/components/OperationsPanel/OperationsEntry/OperationEntryStatus/styled';
import pluralSuffix from 'modules/utils/pluralSuffix';

interface Props {
  batchOperationType: BatchOperationType;
  operationsFailedCount?: number;
  operationsCompletedCount?: number;
}

const OperationEntryStatus: React.FC<Props> = ({
  batchOperationType,
  operationsFailedCount = 0,
  operationsCompletedCount = 0,
}) => {
  return (
    <StatusContainer>
      {operationsCompletedCount > 0 ? (
        <>
          <CheckmarkFilled />
          <Text>
            <>
              {batchOperationType === 'RESOLVE_INCIDENT'
                ? `${operationsCompletedCount} ${operationsCompletedCount === 1 ? 'retry' : 'retries'} succeeded`
                : `${pluralSuffix(operationsCompletedCount, 'instance')} succeeded`}
            </>
          </Text>
        </>
      ) : null}
      {operationsCompletedCount > 0 && operationsFailedCount > 0 ? ' / ' : null}
      {operationsFailedCount ? (
        <>
          <WarningFilled />
          <Text>
            <>
              {batchOperationType === 'RESOLVE_INCIDENT'
                ? operationsCompletedCount > 0
                  ? `${operationsFailedCount} failed`
                  : `${operationsFailedCount} ${operationsFailedCount === 1 ? 'retry' : 'retries'} failed`
                : operationsCompletedCount > 0
                  ? `${operationsFailedCount} failed`
                  : `${pluralSuffix(operationsFailedCount, 'instance')} failed`}
            </>
          </Text>
        </>
      ) : null}
    </StatusContainer>
  );
};

export default OperationEntryStatus;
