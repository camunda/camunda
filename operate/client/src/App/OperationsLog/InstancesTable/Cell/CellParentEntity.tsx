/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {AuditLog} from '@camunda/camunda-api-zod-schemas/8.10/audit-log';
import {Link} from 'modules/components/Link';
import {Paths} from 'modules/Routes';
import {isValidProcessInstanceKey} from 'modules/utils/operationsLog';

type Props = {
  item: AuditLog;
  processDefinitionName?: string;
};

const CellParentEntity: React.FC<Props> = ({item, processDefinitionName}) => {
  if (!item) {
    return null;
  }

  switch (item.entityType) {
    case 'USER_TASK':
    case 'INCIDENT':
    case 'VARIABLE':
      return isValidProcessInstanceKey(item.processInstanceKey) ? (
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
      ) : (
        '-'
      );
    default:
      return '-';
  }
};

export {CellParentEntity};
