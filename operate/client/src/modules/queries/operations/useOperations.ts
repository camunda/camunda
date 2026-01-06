/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useProcessInstanceDeprecated} from '../processInstance/deprecated/useProcessInstanceDeprecated';
import type {
  ProcessInstanceEntity,
  InstanceOperationEntity,
} from 'modules/types/operate';

const operationsParser = (data: ProcessInstanceEntity) => {
  return data.operations;
};

type Options = {
  enabled?: boolean;
};

const useOperations = (options?: Options) => {
  return useProcessInstanceDeprecated<InstanceOperationEntity[]>(
    operationsParser,
    options?.enabled,
  );
};

export {useOperations};
