/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {requestAndParse} from 'modules/request';

const fetchBatchOperations = async (
  {
    pageSize,
    searchAfter,
  }: {
    pageSize: number;
    searchAfter?: OperationEntity['sortValues'];
  },
  options?: Parameters<typeof requestAndParse>[1],
) => {
  return requestAndParse<OperationEntity[]>(
    {
      url: '/api/batch-operations',
      method: 'POST',
      body: {
        pageSize,
        searchAfter,
      },
    },
    options,
  );
};

export {fetchBatchOperations};
