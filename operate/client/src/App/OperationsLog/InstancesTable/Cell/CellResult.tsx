/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {AuditLog} from '@camunda/camunda-api-zod-schemas/8.9/audit-log';
import {OperationsLogStateIcon} from 'modules/components/OperationsLogStateIcon';
import {OperationLogName} from '../styled';

type Props = {
  item: AuditLog;
};

const CellResult: React.FC<Props> = ({item}) => {
  return (
    <OperationLogName>
      <OperationsLogStateIcon
        state={item.result}
        data-testid={`${item.auditLogKey}-icon`}
      />
    </OperationLogName>
  );
};

export {CellResult};
