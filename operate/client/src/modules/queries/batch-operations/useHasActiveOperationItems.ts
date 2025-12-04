/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {ProcessInstance} from '@camunda/camunda-api-zod-schemas/8.8';
import {skipToken, useQuery} from '@tanstack/react-query';
import {queryBatchOperationItems} from 'modules/api/v2/batchOperations/queryBatchOperationItems';
import {queryKeys} from 'modules/queries/queryKeys';

const useHasActiveOperationItems = ({
  processInstance,
}: {
  processInstance: ProcessInstance;
}) => {
  const {processInstanceKey} = processInstance;

  return useQuery({
    queryKey:
      queryKeys.batchOperationItems.searchByProcessInstanceKey(
        processInstanceKey,
      ),
    queryFn:
      processInstanceKey && processInstance.state === 'ACTIVE'
        ? async () => {
            const {response, error} = await queryBatchOperationItems({
              filter: {
                processInstanceKey,
                state: 'ACTIVE',
              },
              page: {
                limit: 1,
              },
            });

            if (response !== null) {
              return response;
            }

            throw error;
          }
        : skipToken,
    select: (batchOperationItems) => batchOperationItems.page.totalItems > 0,
    refetchInterval: 5000,
  });
};

export {useHasActiveOperationItems};
