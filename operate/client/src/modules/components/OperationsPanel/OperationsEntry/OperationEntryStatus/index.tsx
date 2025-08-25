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
          <Text>
            <>
              {type === 'RESOLVE_INCIDENT'
                ? `${completedCount} ${completedCount === 1 ? 'retry' : 'retries'} succeeded`
                : `${pluralSuffix(completedCount, 'instance')} succeeded`}
            </>
          </Text>
        </>
      ) : null}
      {completedCount > 0 && failedCount > 0 ? ' / ' : null}
      {failedCount ? (
        <>
          <WarningFilled />
          <Text>
            <>
              {type === 'RESOLVE_INCIDENT'
                ? completedCount > 0
                  ? `${failedCount} failed`
                  : `${failedCount} ${failedCount === 1 ? 'retry' : 'retries'} failed`
                : completedCount > 0
                  ? `${failedCount} failed`
                  : `${pluralSuffix(failedCount, 'instance')} failed`}
            </>
          </Text>
        </>
      ) : null}
    </StatusContainer>
  );
};

export {OperationEntryStatus};
