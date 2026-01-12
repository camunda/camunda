/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {AuditLog} from '@camunda/camunda-api-zod-schemas/8.9/audit-log';
import {Link} from '@carbon/react';
import {OperationLogName} from './styled';
import {BatchJob} from '@carbon/react/icons';
import {spaceAndCapitalize} from 'modules/utils/spaceAndCapitalize';

type Props = {
  item: AuditLog;
};

const CellOperationType: React.FC<Props> = ({item}) => {
  return (
    <>
      <OperationLogName>
        {item.entityType === 'BATCH' && item.batchOperationType ? (
          <BatchJob />
        ) : undefined}
        {spaceAndCapitalize(item.operationType)}&nbsp;
        {spaceAndCapitalize(item.entityType)}
        {item.entityType === 'BATCH' && item.batchOperationType ? (
          <div>&nbsp;({spaceAndCapitalize(item.batchOperationType)})</div>
        ) : undefined}
      </OperationLogName>
      {item.entityType !== 'BATCH' && item.batchOperationKey ? (
        <OperationLogName>
          <BatchJob />
          <Link
            href={`/batch-operations/${item.batchOperationKey}`}
            target="_self"
          >
            {item.batchOperationKey}
          </Link>
        </OperationLogName>
      ) : undefined}
    </>
  );
};

export {CellOperationType};
