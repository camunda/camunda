/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {getFlowNodeName} from '../utils/flowNodes';
import {useBusinessObjects} from 'modules/queries/processDefinitions/useBusinessObjects';
import {
  queryIncidentsRequestBodySchema,
  type Incident,
} from '@camunda/camunda-api-zod-schemas/8.8';
import {useProcessInstancesSearch} from 'modules/queries/processInstance/useProcessInstancesSearch';
import {useSearchParams} from 'react-router-dom';
import {parseSortParamsV2} from 'modules/utils/filter';
import {useMemo} from 'react';
import {useProcessInstanceElementSelection} from './useProcessInstanceElementSelection';

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
  const {isSelected} = useProcessInstanceElementSelection();

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
      isSelected: isSelected({
        elementId: incident.elementId,
        elementInstanceKey: incident.elementInstanceKey,
        isMultiInstanceBody: false,
      }),
      processDefinitionName:
        processDefinitionNames?.[incident.processInstanceKey] ?? '',
    };
  });
};

const IncidentsSortFieldSchema =
  queryIncidentsRequestBodySchema.shape.sort.def.innerType.def.element.shape
    .field;

const useIncidentsSort = () => {
  const [search] = useSearchParams();
  return useMemo(
    () =>
      parseSortParamsV2(search, IncidentsSortFieldSchema, {
        field: 'creationTime',
        order: 'desc',
      }),
    [search],
  );
};

export {useEnhancedIncidents, useIncidentsSort, type EnhancedIncident};
