/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useQuery} from '@tanstack/react-query';
import {queryBatchOperations} from 'modules/api/v2/batchOperations/queryBatchOperations';
import type {QueryBatchOperationsRequestBody} from '@camunda/camunda-api-zod-schemas/8.8';
import {queryKeys} from '../queryKeys';

const useBatchOperations = (payload: QueryBatchOperationsRequestBody) => {
  return useQuery({
    queryKey: queryKeys.batchOperations.query(payload),
    queryFn: async () => {
      const {response, error} = await queryBatchOperations(payload);

      if (response !== null) {
        return response;
      }

      throw error;
    },
  });
};

export {useBatchOperations};
