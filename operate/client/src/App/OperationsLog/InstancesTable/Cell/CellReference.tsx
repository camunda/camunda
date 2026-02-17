/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {AuditLog} from '@camunda/camunda-api-zod-schemas/8.9/audit-log';
import {formatBatchTitle} from 'modules/utils/operationsLog';
import {Link} from 'modules/components/Link';
import {Paths} from 'modules/Routes';

type Props = {
  item: AuditLog;
  processDefinitionName?: string;
};

const CellReference: React.FC<Props> = ({item, processDefinitionName}) => {
  if (!item) {
    return null;
  }

  switch (item.entityType) {
    case 'BATCH':
      return (
        <>
          <div>
            {item.batchOperationKey ? (
              <Link
                to={Paths.batchOperation(item.batchOperationKey)}
                aria-label={`View batch operation ${item.batchOperationKey}`}
              >
                {item.batchOperationKey}
              </Link>
            ) : (
              item.batchOperationKey
            )}
          </div>
          <em>Multiple {formatBatchTitle(item.batchOperationType)}</em>
        </>
      );
    case 'RESOURCE':
      return (
        <>
          <div>{item.entityKey}</div>
          <em>Deployed resource</em>
        </>
      );
    case 'INCIDENT':
    case 'VARIABLE':
    case 'PROCESS_INSTANCE':
      return (
        <div>
          <div>
            <Link
              to={Paths.processInstance(item.processInstanceKey)}
              aria-label={`View process instance ${item.processInstanceKey}`}
            >
              {item.processInstanceKey}
            </Link>
          </div>
          <em>{processDefinitionName}</em>
        </div>
      );
    case 'DECISION':
      return (
        <>
          <div>
            <Link
              to={Paths.decisionInstance(item.entityKey)}
              aria-label={`View decision ${item.entityKey}`}
            >
              {item.entityKey}
            </Link>
          </div>
        </>
      );
    default:
      return <>{item.entityKey}</>;
  }
};

export {CellReference};
