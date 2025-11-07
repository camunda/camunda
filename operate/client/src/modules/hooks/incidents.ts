/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {flowNodeSelectionStore} from 'modules/stores/flowNodeSelection';
import {getFlowNodeName} from '../utils/flowNodes';
import {useBusinessObjects} from 'modules/queries/processDefinitions/useBusinessObjects';
import {
  queryIncidentsRequestBodySchema,
  type Incident,
  type QueryIncidentsRequestBody,
} from '@camunda/camunda-api-zod-schemas/8.8';
import {useProcessInstancesSearch} from 'modules/queries/processInstance/useProcessInstancesSearch';
import {useLocation} from 'react-router-dom';
import {getSortParams} from 'modules/utils/filter';
import {useMemo} from 'react';

type EnhancedIncident = Incident & {
  processDefinitionName: string;
  elementName: string;
  isSelected: boolean;
};

const useEnhancedIncidents = (incidents: Incident[]): EnhancedIncident[] => {
  const {data: businessObjects} = useBusinessObjects();
  const instancesWithIncident = Array.from(
    new Set(incidents.map((incident) => incident.processInstanceKey)),
  );

  const {data: processDefinitionNames} = useProcessInstancesSearch(
    {
      filter: {processInstanceKey: {$in: instancesWithIncident}},
      page: {limit: instancesWithIncident.length},
    },
    {
      enabled: instancesWithIncident.length > 0,
      select: (res) =>
        res.items.reduce<Record<string, string>>((acc, item) => {
          acc[item.processInstanceKey] = item.processDefinitionName;
          return acc;
        }, {}),
    },
  );

  return incidents.map((incident) => {
    return {
      ...incident,
      elementName: getFlowNodeName({
        businessObjects,
        flowNodeId: incident.elementId,
      }),
      isSelected: flowNodeSelectionStore.isSelected({
        flowNodeId: incident.elementId,
        flowNodeInstanceId: incident.elementInstanceKey,
        isMultiInstance: false,
      }),
      processDefinitionName:
        processDefinitionNames?.[incident.processInstanceKey] ?? '',
    };
  });
};

const incidentsSortKeySchema =
  queryIncidentsRequestBodySchema.shape.sort.def.innerType.def.element.shape
    .field;

const useIncidentsSort = () => {
  const location = useLocation();
  return useMemo<QueryIncidentsRequestBody['sort']>(() => {
    const params = getSortParams(location.search);
    const field = incidentsSortKeySchema.safeParse(params?.sortBy);
    return [
      {
        field: field.success ? field.data : 'creationTime',
        order: params?.sortOrder ?? 'desc',
      },
    ];
  }, [location.search]);
};

export {useEnhancedIncidents, useIncidentsSort, type EnhancedIncident};
