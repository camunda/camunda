/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {ACTIVE_OPERATION_STATES} from 'modules/constants';

const hasActiveOperations = (operations: InstanceOperationEntity[]) => {
  return operations.some((operation) =>
    ACTIVE_OPERATION_STATES.includes(operation.state)
  );
};

export {hasActiveOperations};
