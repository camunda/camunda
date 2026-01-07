/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {logger} from 'modules/logger';
import {
  PROCESS_INSTANCE_FILTER_FIELDS,
  BOOLEAN_PROCESS_INSTANCE_FILTER_FIELDS,
  type ProcessInstanceFilters,
  type ProcessInstanceFilterField,
} from './shared';

function getFilters<Fields extends string, Filters>(
  searchParams: string,
  fields: Fields[],
  booleanFields: string[],
): Filters {
  return Array.from(new URLSearchParams(searchParams)).reduce(
    (accumulator, [param, value]) => {
      if (param === 'incidentErrorHashCode') {
        const incidentErrorHashCode = Number(value);

        if (isNaN(incidentErrorHashCode)) {
          logger.error('incidentErrorHashCode is not a number');
          return accumulator;
        }

        return {
          ...accumulator,
          [param]: incidentErrorHashCode,
        };
      }

      if (booleanFields.includes(param)) {
        return {
          ...accumulator,
          [param]: value === 'true',
        };
      }

      if (fields.includes(param as Fields)) {
        return {
          ...accumulator,
          [param]: value,
        };
      }

      return accumulator;
    },
    {},
  ) as Filters;
}

function getProcessInstanceFilters(
  searchParams: string,
): ProcessInstanceFilters {
  const {variableName, variableValues, ...filters} = getFilters<
    ProcessInstanceFilterField,
    ProcessInstanceFilters
  >(
    searchParams,
    PROCESS_INSTANCE_FILTER_FIELDS,
    BOOLEAN_PROCESS_INSTANCE_FILTER_FIELDS,
  );
  return filters;
}

export {getProcessInstanceFilters, getFilters};
