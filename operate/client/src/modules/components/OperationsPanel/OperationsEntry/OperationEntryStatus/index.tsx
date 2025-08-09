/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {CheckmarkFilled, StatusContainer, Text, WarningFilled} from './styled';
import pluralSuffix from 'modules/utils/pluralSuffix';
import {type OperationLabelType} from '../';

type Props = {
  isTypeDeleteProcessOrDecision: boolean;
  label: OperationLabelType;
  failedOperationsCount?: number;
  completedOperationsCount?: number;
};

const OperationEntryStatus: React.FC<Props> = ({
  label,
  isTypeDeleteProcessOrDecision,
  failedOperationsCount = 0,
  completedOperationsCount = 0,
}) => {
  const instanceDeletedText = `${pluralSuffix(
    completedOperationsCount,
    'instance',
  )} deleted`;

  const successText = `${completedOperationsCount} success`;

  if (label === 'Delete' && !isTypeDeleteProcessOrDecision) {
    if (failedOperationsCount) {
      return (
        <StatusContainer>
          {failedOperationsCount ? (
            <>
              <WarningFilled />
              <Text>{`${failedOperationsCount} fail`}</Text>
            </>
          ) : null}
        </StatusContainer>
      );
    }

    return null;
  }

  return (
    <StatusContainer>
      {completedOperationsCount ? (
        <>
          <CheckmarkFilled />
          <Text>
            {isTypeDeleteProcessOrDecision ? instanceDeletedText : successText}
          </Text>
        </>
      ) : null}
      {completedOperationsCount && failedOperationsCount ? ' / ' : null}
      {failedOperationsCount ? (
        <>
          <WarningFilled />
          <Text>{`${failedOperationsCount} fail`}</Text>
        </>
      ) : null}
    </StatusContainer>
  );
};

export {OperationEntryStatus};
