/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {z} from 'zod';
import {
  queryProcessInstancesRequestBodySchema,
  type ProcessInstanceState,
  type QueryProcessInstancesRequestBody,
} from '@camunda/camunda-api-zod-schemas/8.10';
import {formatToISO} from 'modules/utils/date/formatDate';
import {parseIds, parseSortParamsV2, updateFiltersSearchString} from './index';
import {advancedStringFilterCodec} from './advancedStringFilter';

/**
 * ProcessInstancesFilter represents the URL search params.
 * This is the single source of truth for what can be filtered from the UI.
 */
const ProcessInstancesFilterSchema = z
  .object({
    processDefinitionId: z.string().optional(),
    processDefinitionVersion: z.string().optional(),
    tenantId: z.string().optional(),
    processInstanceKey: z.string().optional(),
    parentProcessInstanceKey: z.string().optional(),
    active: z.coerce.boolean().optional(),
    suspended: z
      .string()
      .transform((value) => value === 'true')
      .optional(),
    incidents: z.coerce.boolean().optional(),
    completed: z.coerce.boolean().optional(),
    canceled: z.coerce.boolean().optional(),
    elementId: z.string().optional(),
    batchOperationKey: z.string().optional(),
    errorMessage: z.string().optional(),
    hasRetriesLeft: z.coerce.boolean().optional(),
    hasElementInstanceIncident: z.coerce.boolean().optional(),
    incidentErrorHashCode: z.coerce.number().optional(),
    startDateFrom: z.string().optional(),
    startDateTo: z.string().optional(),
    endDateFrom: z.string().optional(),
    endDateTo: z.string().optional(),
    variableName: z.string().optional(),
    variableValues: z.string().optional(),
    businessId: z.string().optional(),
  })
  .catch({});

type ProcessInstancesFilter = z.infer<typeof ProcessInstancesFilterSchema>;
type ProcessInstancesSearchFilter = NonNullable<
  QueryProcessInstancesRequestBody['filter']
>;

type ParseProcessInstancesSearchFilterOptions = {
  searchParams: URLSearchParams;
  includeSuspended?: boolean;
};

const parseProcessInstancesFilter = (
  search: URLSearchParams,
): ProcessInstancesFilter => {
  return ProcessInstancesFilterSchema.parse(Object.fromEntries(search));
};

const buildStateCriterion = (
  states: ProcessInstanceState[],
): ProcessInstancesSearchFilter['state'] => {
  const [state] = states;
  return state && states.length === 1 ? {$eq: state} : {$in: states};
};

const getSelectedStates = (
  filter: ProcessInstancesFilter,
): ProcessInstanceState[] =>
  [
    filter.active ? 'ACTIVE' : undefined,
    filter.completed ? 'COMPLETED' : undefined,
    filter.canceled ? 'TERMINATED' : undefined,
  ].filter((state): state is ProcessInstanceState => state !== undefined);

const compactFilters = (
  filters: Array<ProcessInstancesSearchFilter | false | undefined>,
): ProcessInstancesSearchFilter[] =>
  filters.filter(
    (filter): filter is ProcessInstancesSearchFilter =>
      filter !== false && filter !== undefined,
  );

const combineFilters = (
  filters: ProcessInstancesSearchFilter[],
): ProcessInstancesSearchFilter => {
  if (filters.length === 0) {
    return {};
  }

  if (filters.length === 1) {
    return filters[0] ?? {};
  }

  return {$or: filters};
};

const buildStateFilter = (
  filter: ProcessInstancesFilter,
  hasSuspendedFilter: boolean,
): ProcessInstancesSearchFilter => {
  const states = getSelectedStates(filter);

  if (hasSuspendedFilter) {
    return combineFilters(
      compactFilters([
        states.length > 0 && {
          state: buildStateCriterion(states),
          hasIncident: false,
        },
        {state: {$eq: 'SUSPENDED'}},
        filter.incidents && {
          hasIncident: true,
          state: {$neq: 'SUSPENDED'},
        },
      ]),
    );
  }

  if (filter.incidents && states.length > 0) {
    return {$or: [{state: {$in: states}}, {hasIncident: true}]};
  }

  if (filter.incidents) {
    return {hasIncident: true};
  }

  if (states.length > 0) {
    return {
      state: buildStateCriterion(states),
      hasIncident: false,
    };
  }

  return {};
};

const buildElementFilter = (
  elementId: string,
  matchActiveElement: boolean,
): ProcessInstancesSearchFilter => ({
  elementId: {$eq: elementId},
  ...(matchActiveElement && {
    elementInstanceState: {$eq: 'ACTIVE'},
  }),
});

const buildMixedStateElementFilter = (
  filter: ProcessInstancesFilter,
  elementId: string,
  hasSuspendedFilter: boolean,
): ProcessInstancesSearchFilter => {
  const activeElementFilter = buildElementFilter(elementId, true);
  const executedElementFilter = buildElementFilter(elementId, false);
  const finishedStates = getSelectedStates(filter).filter(
    (state) => state !== 'ACTIVE',
  );

  return {
    $or: compactFilters([
      filter.active && {
        ...activeElementFilter,
        state: {$eq: 'ACTIVE'},
        hasIncident: false,
      },
      finishedStates.length > 0 && {
        ...executedElementFilter,
        state: buildStateCriterion(finishedStates),
        hasIncident: false,
      },
      hasSuspendedFilter && {
        ...activeElementFilter,
        state: {$eq: 'SUSPENDED'},
      },
      filter.incidents && {
        ...activeElementFilter,
        hasIncident: true,
        ...(hasSuspendedFilter && {state: {$neq: 'SUSPENDED'}}),
      },
    ]),
  };
};

const buildStateAndElementFilter = (
  filter: ProcessInstancesFilter,
  hasSuspendedFilter: boolean,
): ProcessInstancesSearchFilter => {
  const stateFilter = buildStateFilter(filter, hasSuspendedFilter);

  if (!filter.elementId) {
    return stateFilter;
  }

  const hasFinishedStateFilter = Boolean(filter.completed || filter.canceled);
  const hasActiveElementStateFilter = Boolean(
    filter.active || hasSuspendedFilter || filter.incidents,
  );

  if (hasFinishedStateFilter && hasActiveElementStateFilter) {
    return buildMixedStateElementFilter(
      filter,
      filter.elementId,
      hasSuspendedFilter,
    );
  }

  return {
    ...stateFilter,
    ...buildElementFilter(filter.elementId, !hasFinishedStateFilter),
  };
};

const parseProcessInstancesSearchFilter = ({
  searchParams,
  includeSuspended = false,
}: ParseProcessInstancesSearchFilterOptions):
  ProcessInstancesSearchFilter | undefined => {
  const filter = parseProcessInstancesFilter(searchParams);
  const hasSuspendedFilter = Boolean(includeSuspended && filter.suspended);

  const hasStateFilters =
    filter.active ||
    hasSuspendedFilter ||
    filter.completed ||
    filter.canceled ||
    filter.incidents;

  if (!hasStateFilters && !filter.batchOperationKey && !filter.elementId) {
    return undefined;
  }

  const apiFilter = buildStateAndElementFilter(filter, hasSuspendedFilter);

  if (filter.processDefinitionId) {
    apiFilter.processDefinitionId = {$eq: filter.processDefinitionId};
  }

  if (
    filter.processDefinitionVersion &&
    filter.processDefinitionVersion !== 'all'
  ) {
    const versionNumber = parseInt(filter.processDefinitionVersion, 10);
    if (!isNaN(versionNumber)) {
      apiFilter.processDefinitionVersion = versionNumber;
    }
  }

  if (filter.tenantId && filter.tenantId !== 'all') {
    apiFilter.tenantId = {$eq: filter.tenantId};
  }

  if (filter.processInstanceKey) {
    const keys = parseIds(filter.processInstanceKey);
    if (keys.length > 0) {
      apiFilter.processInstanceKey = {$in: keys};
    }
  }

  if (filter.parentProcessInstanceKey) {
    apiFilter.parentProcessInstanceKey = {
      $eq: filter.parentProcessInstanceKey,
    };
  }

  if (filter.batchOperationKey) {
    apiFilter.batchOperationKey = {$eq: filter.batchOperationKey};
  }

  if (filter.errorMessage) {
    apiFilter.errorMessage = {$in: [filter.errorMessage]};
  }

  if (filter.hasRetriesLeft) {
    apiFilter.hasRetriesLeft = true;
  }

  if (filter.hasElementInstanceIncident) {
    apiFilter.hasElementInstanceIncident = true;
  }

  if (typeof filter.incidentErrorHashCode === 'number') {
    apiFilter.incidentErrorHashCode = filter.incidentErrorHashCode;
  }

  if (filter.startDateFrom || filter.startDateTo) {
    apiFilter.startDate = {
      ...(filter.startDateFrom && {
        $gt: formatToISO(filter.startDateFrom),
      }),
      ...(filter.startDateTo && {
        $lt: formatToISO(filter.startDateTo),
      }),
    };
  }

  if (filter.endDateFrom || filter.endDateTo) {
    apiFilter.endDate = {
      ...(filter.endDateFrom && {$gt: formatToISO(filter.endDateFrom)}),
      ...(filter.endDateTo && {$lt: formatToISO(filter.endDateTo)}),
    };
  }

  if (filter.businessId) {
    const advancedFilter = advancedStringFilterCodec.safeDecode(
      filter.businessId,
    );
    if (advancedFilter.success) {
      apiFilter.businessId = advancedFilter.data;
    }
  }

  return apiFilter;
};

type ProcessInstancesSearchSort = NonNullable<
  QueryProcessInstancesRequestBody['sort']
>;

const ProcessInstancesSearchSortFieldSchema =
  queryProcessInstancesRequestBodySchema.shape.sort.unwrap().unwrap()
    .shape.field;

const parseProcessInstancesSearchSort = (
  search: URLSearchParams,
): ProcessInstancesSearchSort => {
  return parseSortParamsV2(search, ProcessInstancesSearchSortFieldSchema, {
    field: 'startDate',
    order: 'desc',
  });
};

const PROCESS_INSTANCE_FILTER_FIELDS = Object.values(
  ProcessInstancesFilterSchema.unwrap().keyof().enum,
);

const BOOLEAN_PROCESS_INSTANCE_FILTER_FIELDS = Object.values(
  ProcessInstancesFilterSchema.unwrap()
    .pick({
      active: true,
      suspended: true,
      incidents: true,
      completed: true,
      canceled: true,
      hasRetriesLeft: true,
      hasElementInstanceIncident: true,
    })
    .keyof().enum,
);

function updateProcessInstancesFilterSearchString(
  currentSearch: URLSearchParams,
  newFilters: ProcessInstancesFilter,
) {
  const {variableName, variableValues, ...filtersWithoutVariable} = newFilters;

  return updateFiltersSearchString<ProcessInstancesFilter>(
    currentSearch,
    filtersWithoutVariable,
    PROCESS_INSTANCE_FILTER_FIELDS,
    BOOLEAN_PROCESS_INSTANCE_FILTER_FIELDS,
  );
}

export {
  parseProcessInstancesFilter,
  parseProcessInstancesSearchFilter,
  parseProcessInstancesSearchSort,
  updateProcessInstancesFilterSearchString,
};
export type {ProcessInstancesFilter};
