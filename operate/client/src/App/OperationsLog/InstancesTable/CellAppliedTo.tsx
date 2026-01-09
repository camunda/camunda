/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {AuditLog} from '@camunda/camunda-api-zod-schemas/8.9/audit-log';
import {formatBatchTitle} from 'modules/utils/operationsLog';
import {Link} from '@carbon/react';
import {processesStore} from 'modules/stores/processes/processes.list';

type Props = {
  item: AuditLog;
};

const CellAppliedTo: React.FC<Props> = ({item}) => {
  if (!item) {
    return null;
  }

  switch (item.entityType) {
    case 'BATCH':
      return (
        <>
          <div>Multiple {formatBatchTitle(item.batchOperationType)}</div>
          <div>
            {item.batchOperationKey ? (
              <Link
                href={`/batch-operations/${item.batchOperationKey}`}
                target="_self"
              >
                {item.batchOperationKey}
              </Link>
            ) : (
              item.batchOperationKey
            )}
          </div>
        </>
      );
    case 'RESOURCE':
      return (
        <>
          <div>Deployed resource</div>
          <div>{item.entityKey}</div>
        </>
      );
    case 'PROCESS_INSTANCE':
      return (
        <>
          <div>
            {
              processesStore.getProcess({
                bpmnProcessId: item.processDefinitionId,
              })?.name
            }
          </div>
          <div>
            <Link href={`/processes/${item.entityKey}`} target="_self">
              {item.entityKey}
            </Link>
          </div>
        </>
      );
    case 'DECISION':
      return (
        <>
          <div>
            <Link href={`/decisions/${item.entityKey}`} target="_self">
              {item.entityKey}
            </Link>
          </div>
        </>
      );
    default:
      return <>{item.entityKey}</>;
  }
};

export {CellAppliedTo};
