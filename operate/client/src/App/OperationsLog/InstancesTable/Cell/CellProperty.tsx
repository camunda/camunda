/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {
  AuditLog,
  AuditLogOperationType,
} from '@camunda/camunda-api-zod-schemas/8.9/audit-log';
import {PropertyText} from '../styled';
import {spaceAndCapitalize} from 'modules/utils/spaceAndCapitalize';

type Props = {
  item: AuditLog;
};

const userTaskOperations: AuditLogOperationType[] = ['ASSIGN', 'UNASSIGN'];

const CellProperty: React.FC<Props> = ({item}) => {
  if (item.result === 'FAIL') {
    return (
      <div>
        <PropertyText>Error code</PropertyText>
        {item.entityDescription}
      </div>
    );
  }

  switch (item.entityType) {
    case 'INCIDENT':
      return (
        <>
          <PropertyText>Incident key</PropertyText>
          {item.entityKey}
        </>
      );
    case 'VARIABLE':
      return (
        <>
          <PropertyText>Variable name</PropertyText>
          {item.entityDescription}
        </>
      );
    case 'RESOURCE':
      return (
        <>
          <PropertyText>Resource name</PropertyText>
          {item.entityDescription}
        </>
      );
    case 'BATCH':
      return item.batchOperationType ? (
        <>
          <PropertyText>Batch operation type</PropertyText>
          {spaceAndCapitalize(item.batchOperationType)}
        </>
      ) : (
        '-'
      );
    case 'USER_TASK':
      return userTaskOperations.includes(item.operationType) ? (
        <>
          <PropertyText>Assignee</PropertyText>
          {item.relatedEntityKey}
        </>
      ) : (
        '-'
      );
    default:
      return item.entityDescription ?? '-';
  }
};

export {CellProperty};
