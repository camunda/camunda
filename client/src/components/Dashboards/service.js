/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {get, del, post} from 'request';

export async function shareDashboard(dashboardId) {
  const body = {
    dashboardId,
  };
  const response = await post(`api/share/dashboard`, body);

  const json = await response.json();
  return json.id;
}

export async function getSharedDashboard(reportId) {
  const response = await get(`api/share/dashboard/${reportId}`);

  if (response.status > 201) {
    return '';
  } else {
    const json = await response.json();
    return json.id;
  }
}

export async function revokeDashboardSharing(id) {
  return await del(`api/share/dashboard/${id}`);
}

export async function isAuthorizedToShareDashboard(dashboardId) {
  try {
    const response = await get(`api/share/dashboard/${dashboardId}/isAuthorizedToShare`);
    return response.status === 200;
  } catch (error) {
    return false;
  }
}

export function convertFilterToDefaultValues(availableFilter, filters) {
  // dates
  if (availableFilter.type === 'instanceStartDate' || availableFilter.type === 'instanceEndDate') {
    return filters.find(({type}) => type === availableFilter.type)?.data ?? null;
  }

  // assignee / candidate group
  if (availableFilter.type === 'assignee' || availableFilter.type === 'candidateGroup') {
    return (
      filters.find(
        ({type, data}) =>
          type === availableFilter.type && data.operator === availableFilter.data.operator
      )?.data.values ?? null
    );
  }

  // state
  if (availableFilter.type === 'state') {
    const defaultValues = filters
      .filter(({type}) =>
        [
          'runningInstancesOnly',
          'completedInstancesOnly',
          'canceledInstancesOnly',
          'nonCanceledInstancesOnly',
          'suspendedInstancesOnly',
          'nonSuspendedInstancesOnly',
        ].includes(type)
      )
      .map(({type}) => type);

    if (defaultValues.length > 0) {
      return defaultValues;
    }
    return null;
  }

  // Boolean Variables
  if (availableFilter.type === 'variable' && availableFilter.data.type === 'Boolean') {
    return (
      filters.find(
        ({type, data}) =>
          type === 'variable' && data.type === 'Boolean' && data.name === availableFilter.data.name
      )?.data.data.values ?? null
    );
  }

  // Date Variables
  if (availableFilter.type === 'variable' && availableFilter.data.type === 'Date') {
    return (
      filters.find(
        ({type, data}) =>
          type === 'variable' && data.type === 'Date' && data.name === availableFilter.data.name
      )?.data.data ?? null
    );
  }

  // string and number variables
  if (availableFilter.type === 'variable') {
    return (
      filters.find(
        ({type, data}) =>
          type === 'variable' &&
          data.type === availableFilter.data.type &&
          data.name === availableFilter.data.name &&
          data.data.operator === availableFilter.data.data.operator
      )?.data.data.values ?? null
    );
  }

  return null;
}

export function getDefaultFilter(availableFilters) {
  const filters = [];

  availableFilters.forEach((availableFilter) => {
    if (availableFilter?.data.defaultValues) {
      if (availableFilter.type === 'state') {
        return filters.push(
          ...availableFilter.data.defaultValues.map((type) => ({
            filterLevel: 'instance',
            type,
            data: null,
          }))
        );
      }

      if (
        availableFilter.type === 'instanceStartDate' ||
        availableFilter.type === 'instanceEndDate'
      ) {
        return filters.push({
          type: availableFilter.type,
          filterLevel: 'instance',
          data: availableFilter.data.defaultValues,
        });
      }

      if (availableFilter.type === 'assignee' || availableFilter.type === 'candidateGroup') {
        return filters.push({
          type: availableFilter.type,
          filterLevel: 'view',
          data: {
            operator: availableFilter.data.operator,
            values: availableFilter.data.defaultValues,
          },
        });
      }

      if (availableFilter.type === 'variable' && availableFilter.data.type === 'Boolean') {
        return filters.push({
          type: availableFilter.type,
          filterLevel: 'instance',
          data: {
            name: availableFilter.data.name,
            type: availableFilter.data.type,
            data: {values: availableFilter.data.defaultValues},
          },
        });
      }

      if (availableFilter.type === 'variable' && availableFilter.data.type === 'Date') {
        return filters.push({
          type: availableFilter.type,
          filterLevel: 'instance',
          data: {
            name: availableFilter.data.name,
            type: availableFilter.data.type,
            data: availableFilter.data.defaultValues,
          },
        });
      }

      if (availableFilter.type === 'variable') {
        return filters.push({
          type: availableFilter.type,
          filterLevel: 'instance',
          data: {
            name: availableFilter.data.name,
            type: availableFilter.data.type,
            data: {
              operator: availableFilter.data.data.operator,
              values: availableFilter.data.defaultValues,
            },
          },
        });
      }
    }
  });

  return filters;
}
