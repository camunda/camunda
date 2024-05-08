/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {ACTIVE_OPERATION_STATES} from 'modules/constants';

const hasActiveOperations = (operations: InstanceOperationEntity[]) => {
  return operations.some((operation) =>
    ACTIVE_OPERATION_STATES.includes(operation.state),
  );
};

export {hasActiveOperations};
