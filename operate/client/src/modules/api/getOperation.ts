/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {requestAndParse} from 'modules/request';
import type {InstanceOperationEntity} from 'modules/types/operate';

const getOperation = async (
  batchOperationId: string,
  options?: Parameters<typeof requestAndParse>[1],
) => {
  return requestAndParse<InstanceOperationEntity[]>(
    {
      url: `/api/operations?batchOperationId=${batchOperationId}`,
    },
    options,
  );
};

export {getOperation};
