/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useQueries} from '@tanstack/react-query';
import {useMemo} from 'react';
import {fetchProcessDefinitionVersionStatistics} from 'modules/api/v2/processDefinitions/fetchProcessDefinitionVersionStatistics';
import type {
  ProcessDefinitionInstanceStatistics,
  GetProcessDefinitionInstanceVersionStatisticsRequestBody,
} from '@camunda/camunda-api-zod-schemas/8.8';
import {queryKeys} from '../queryKeys';
import {getClientConfig} from '../../utils/getClientConfig.ts';
import {DEFAULT_TENANT} from 'modules/constants';

const useVersionCounts = (
  processItems: ProcessDefinitionInstanceStatistics[],
): Map<string, number> => {
  const isMultiTenancyEnabled = getClientConfig()?.multiTenancyEnabled;

  const processIdsWithMultipleVersions = useMemo(
    () =>
      processItems
        .filter((item) => item.hasMultipleVersions)
        .map((item) => ({
          processDefinitionId: item.processDefinitionId,
          tenantId: item.tenantId,
        })),
    [processItems],
  );

  const versionCountQueries = useQueries({
    queries: processIdsWithMultipleVersions.map(
      ({processDefinitionId, tenantId}) => {
        const payload: GetProcessDefinitionInstanceVersionStatisticsRequestBody =
          isMultiTenancyEnabled && tenantId && tenantId !== DEFAULT_TENANT
            ? {
                page: {limit: 0},
                sort: [
                  {field: 'activeInstancesWithIncidentCount', order: 'desc'},
                  {
                    field: 'activeInstancesWithoutIncidentCount',
                    order: 'desc',
                  },
                ],
                filter: {
                  tenantId: {$eq: tenantId},
                },
              }
            : {
                page: {limit: 0},
                sort: [
                  {field: 'activeInstancesWithIncidentCount', order: 'desc'},
                  {
                    field: 'activeInstancesWithoutIncidentCount',
                    order: 'desc',
                  },
                ],
              };

        return {
          queryKey: queryKeys.processDefinitionStatistics.getByVersion(
            processDefinitionId,
            payload,
          ),
          queryFn: async () => {
            const {response, error} =
              await fetchProcessDefinitionVersionStatistics(
                processDefinitionId,
                payload,
              );

            if (response !== null) {
              return response;
            }

            throw error;
          },
          staleTime: 30000,
        };
      },
    ),
  });

  const versionCountsMap = new Map<string, number>();
  processIdsWithMultipleVersions.forEach(({processDefinitionId}, index) => {
    const query = versionCountQueries[index];
    if (query?.data?.page.totalItems !== undefined) {
      versionCountsMap.set(processDefinitionId, query.data.page.totalItems);
    }
  });

  return versionCountsMap;
};

export {useVersionCounts};
