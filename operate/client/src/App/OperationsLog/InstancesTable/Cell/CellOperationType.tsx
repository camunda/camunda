/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {AuditLog} from '@camunda/camunda-api-zod-schemas/8.9/audit-log';
import {OperationLogName} from '../styled';
import {spaceAndCapitalize} from 'modules/utils/spaceAndCapitalize';

type Props = {
  item: AuditLog;
};

const CellOperationType: React.FC<Props> = ({item}) => {
  return (
    <OperationLogName>
      {spaceAndCapitalize(item.operationType)}
    </OperationLogName>
  );
};

export {CellOperationType};
