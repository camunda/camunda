/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {
  isValid,
  addDays,
  startOfDay,
  addMinutes,
  format,
  parse,
} from 'date-fns';

import {compactObject} from '../index';
import {isValidJSON, trimValue, tryDecodeURI} from 'modules/utils';
import {trimVariable} from 'modules/utils/variable';
import {workflowsStore} from 'modules/stores/workflows';

type FilterFieldsType =
  | 'workflow'
  | 'workflowVersion'
  | 'ids'
  | 'errorMessage'
  | 'startDate'
  | 'endDate'
  | 'flowNodeId'
  | 'variableName'
  | 'variableValue'
  | 'operationId'
  | 'active'
  | 'incidents'
  | 'completed'
  | 'canceled';

type FiltersType = {
  [key in FilterFieldsType]?: string;
};

/**
 * Returns a query string for the filter objects
 * removes keys with empty values (null, "", []) so that they don't appear in URL
 */
export function getFilterQueryString(filter = {}, name?: string) {
  const cleanedFilter = compactObject(filter);
  const filterString = `?filter=${JSON.stringify(cleanedFilter)}`;
  const nameString = `&name=${JSON.stringify(name)}`;
  return name ? filterString + nameString : filterString;
}

export function parseQueryString(queryString = '') {
  const searchParams = new URLSearchParams(queryString);
  return Array.from(searchParams.entries()).reduce<{[key: string]: any}>(
    (accumulator, [key, value]) => {
      if (isValidJSON(value)) {
        return {...accumulator, ...{[key]: JSON.parse(value)}};
      }
      return accumulator;
    },
    {}
  );
}

/**
 * For a given date field's value returns the corresponding url options for filtering
 * Returns an object of two values [name]dateBefore and [name]dateAfter
 * where name is oneOf['starDate', 'endDate']
 */
const parseDate = (value: any, name: any) => {
  const date = new Date(trimValue(value));
  const isValidDate = isValid(date);
  let dateAfter, dateBefore;
  // enforce no comma in the timezone
  const formatWithTimezone = "yyyy-MM-dd'T'HH:mm:ss.SSSxx";

  if (value === '') {
    return {
      [`${name}After`]: null,
      [`${name}Before`]: null,
    };
  }

  if (!isValidDate) {
    return null;
  }

  // temporary condition to check for presence of time in user input
  // as we can't decide based on a string
  const hasTime = value.indexOf(':') !== -1;

  dateAfter = hasTime ? date : startOfDay(date);
  dateBefore = hasTime ? addMinutes(date, 1) : addDays(startOfDay(date), 1);

  return {
    [`${name}After`]: format(dateAfter, formatWithTimezone),
    [`${name}Before`]: format(dateBefore, formatWithTimezone),
  };
};

/**
 * Collection of parsers for filter field values
 * we used this parser before making a call to backend with the current filters
 */
export const fieldParser = {
  ids: (name: any, value: any) => {
    // split by space, comma, tab or return key
    return {[name]: value.split(/[ ,\t\n]+/).filter(Boolean)};
  },
  startDate: (name: any, value: any) => {
    return parseDate(value, 'startDate');
  },
  endDate: (name: any, value: any) => {
    return parseDate(value, 'endDate');
  },
  variable: (name: any, value: any) => {
    return {[name]: trimVariable(value)};
  },
  batchOperationId: (name: any, value: any) => {
    return trimValue(value) === '' ? null : {[name]: value};
  },
};

function defaultFieldParser(name: any, value: any) {
  return {[name]: value};
}

/**
 * Adds running or finished additional payload,
 * they are required when fetching the instances by state
 */
export function getInstanceStatePayload(filter: any) {
  const {active, incidents, completed, canceled} = filter;
  const result = {};

  if (active || incidents) {
    // @ts-expect-error ts-migrate(2339) FIXME: Property 'running' does not exist on type '{}'.
    result.running = true;
  }

  if (completed || canceled) {
    // @ts-expect-error ts-migrate(2339) FIXME: Property 'finished' does not exist on type '{}'.
    result.finished = true;
  }

  return result;
}

/**
 * Before fetching the instances
 * the filter field values need to be parsed
 * @param {Object} filter
 * @return {Object}
 */
export function parseFilterForRequest(filter: any) {
  let parsedFilter = {...getInstanceStatePayload(filter)};

  for (let key in filter) {
    const value =
      typeof filter[key] === 'string'
        ? decodeURIComponent(filter[key])
        : filter[key];

    // @ts-expect-error ts-migrate(7053) FIXME: No index signature with a parameter of type 'strin... Remove this comment to see the full error message
    const parsedField = fieldParser[key]
      ? // @ts-expect-error ts-migrate(7053) FIXME: No index signature with a parameter of type 'strin... Remove this comment to see the full error message
        fieldParser[key](key, value)
      : defaultFieldParser(key, value);

    parsedFilter = {
      ...parsedFilter,
      ...parsedField,
    };
  }

  return {
    ...trimmFilter(parsedFilter),
  };
}

export function getWorkflowByVersion(workflow: any, version: any) {
  if (!workflow || !version || version === 'all') return {};
  return workflow.workflows.find((item: any) => {
    return String(item.version) === String(version);
  });
}

function trimmFilter(filter: any) {
  let newFilter = {};

  for (let key in filter) {
    const value = filter[key];
    // @ts-expect-error ts-migrate(7053) FIXME: No index signature with a parameter of type 'strin... Remove this comment to see the full error message
    newFilter[key] = trimValue(value);
  }

  return newFilter;
}

const decodeFields = function (object: any) {
  let result = {};

  for (let key in object) {
    const value = object[key];

    // @ts-expect-error ts-migrate(7053) FIXME: No index signature with a parameter of type 'strin... Remove this comment to see the full error message
    result[key] = typeof value === 'string' ? tryDecodeURI(object[key]) : value;
  }

  return result;
};

/**
 * For using a filter in a request we replace filter.workflow & filter.version
 * with the corresponding workflowIds:[..] field
 * @param {Object} filter
 * @param {Object} allWorkflows all the available workflows
 */
export function getFilterWithWorkflowIds(
  filter: unknown = {},
  allWorkflows: unknown = {}
) {
  // @ts-expect-error ts-migrate(2339) FIXME: Property 'workflow' does not exist on type '{}'.
  const {workflow, version, ...otherFields} = filter;
  let workflowIds: any = [];
  let newFilter = {...otherFields};

  if (!Boolean(workflow) && !Boolean(version)) {
    return decodeFields(otherFields);
  }

  if (version === 'all') {
    // @ts-expect-error ts-migrate(7053) FIXME: Element implicitly has an 'any' type because expre... Remove this comment to see the full error message
    allWorkflows[workflow].workflows.forEach((item: any) => {
      workflowIds.push(item.id);
    });
    newFilter.workflowIds = workflowIds;
  } else {
    const workflowByVersion = getWorkflowByVersion(
      // @ts-expect-error ts-migrate(7053) FIXME: Element implicitly has an 'any' type because expre... Remove this comment to see the full error message
      allWorkflows[workflow],
      version
    );

    if (Boolean(workflowByVersion)) {
      workflowIds.push(workflowByVersion.id);
      newFilter.workflowIds = workflowIds;
    }
  }

  return decodeFields({...newFilter});
}

function isVariableEmpty(variable: any) {
  return (
    variable === undefined || (variable.name === '' && variable.value === '')
  );
}

const FILTER_FIELDS: FilterFieldsType[] = [
  'workflow',
  'workflowVersion',
  'ids',
  'errorMessage',
  'startDate',
  'endDate',
  'flowNodeId',
  'variableName',
  'variableValue',
  'operationId',
  'active',
  'incidents',
  'completed',
  'canceled',
];

const BOOLEAN_FILTER_FIELDS = ['active', 'incidents', 'completed', 'canceled'];

function getFilters(
  searchParams: string,
  fields: FilterFieldsType[] = FILTER_FIELDS
): ParsedFilters {
  return parseFilters(
    Array.from(new URLSearchParams(searchParams)).reduce(
      (accumulator, [param, value]) => {
        if (fields.includes(param as FilterFieldsType)) {
          return {
            ...accumulator,
            [param]: value,
          };
        }

        return accumulator;
      },
      {}
    )
  );
}

type ParsedFilters = FiltersType & {
  active?: boolean;
  incidents?: boolean;
  completed?: boolean;
  canceled?: boolean;
};

function parseFilters(filters: FiltersType): ParsedFilters {
  return Object.fromEntries(
    Object.entries(filters).map(([field, value]) => {
      if (BOOLEAN_FILTER_FIELDS.includes(field)) {
        return [field, value === 'true'];
      }

      return [field, value];
    })
  );
}

function getSearchString() {
  const HASH_PATHNAME_PATTERN = /^#(\/\w{1,}\/*)+\?/;
  const hash = window.location.hash;

  if (HASH_PATHNAME_PATTERN.test(hash)) {
    return hash.replace(HASH_PATHNAME_PATTERN, '');
  }

  return '';
}

type GetRequestDatePairReturn =
  | {
      startDateBefore: string;
      startDateAfter: string;
    }
  | {
      endDateBefore: string;
      endDateAfter: string;
    };

function getRequestDatePair(
  date: Date,
  type: 'startDate' | 'endDate'
): GetRequestDatePairReturn {
  const DATE_REQUEST_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSSxx";
  const hasTime = date.getHours() + date.getMinutes() + date.getSeconds() !== 0;
  const dateAfter = format(
    hasTime ? date : startOfDay(date),
    DATE_REQUEST_FORMAT
  );
  const dateBefore = format(
    hasTime ? addMinutes(date, 1) : addDays(startOfDay(date), 1),
    DATE_REQUEST_FORMAT
  );

  if (type === 'startDate') {
    return {
      startDateBefore: dateBefore,
      startDateAfter: dateAfter,
    };
  }

  return {
    endDateBefore: dateBefore,
    endDateAfter: dateAfter,
  };
}

type RequestFilters = {
  running?: boolean;
  active?: boolean;
  incidents?: boolean;
  finished?: boolean;
  canceled?: boolean;
  completed?: boolean;
  activityId?: string;
  batchOperationId?: string;
  endDateAfter?: string;
  endDateBefore?: string;
  errorMessage?: string;
  ids?: string[];
  startDateAfter?: string;
  startDateBefore?: string;
  variable?: {
    name: string;
    value: string;
  };
  workflowIds?: string[];
};

function parseIds(value: string) {
  return value
    .trim()
    .replace(/,\s/g, '|')
    .replace(/\s{1,}/g, '|')
    .replace(/,{1,}/g, '|')
    .split('|');
}

function parseFilterDate(value: string) {
  const DATE_PATTERN = /^[0-9]{4}-[0-9]{2}-[0-9]{2}$/;
  const DATE_HOUR_PATTERN = /^[0-9]{4}-[0-9]{2}-[0-9]{2}\s[0-9]{2}$/;
  const DATE_HOUR_MINUTES_PATTERN = /^[0-9]{4}-[0-9]{2}-[0-9]{2}\s[0-9]{2}:[0-9]{2}$/;
  const DATE_TIME_PATTERN_WITH = /^[0-9]{4}-[0-9]{2}-[0-9]{2}\s[0-9]{2}:[0-9]{2}:[0-9]{2}$/;
  const trimmedValue = value.trim();

  if (DATE_PATTERN.test(trimmedValue)) {
    return parse(trimmedValue, 'yyyy-MM-dd', new Date());
  }

  if (DATE_HOUR_PATTERN.test(trimmedValue)) {
    return parse(trimmedValue, 'yyyy-MM-dd kk', new Date());
  }

  if (DATE_HOUR_MINUTES_PATTERN.test(trimmedValue)) {
    return parse(trimmedValue, 'yyyy-MM-dd kk:mm', new Date());
  }

  if (DATE_TIME_PATTERN_WITH.test(trimmedValue)) {
    return parse(trimmedValue, 'yyyy-MM-dd kk:mm:ss', new Date());
  }
}

function getWorkflowIds(workflow: string, workflowVersion: string) {
  if (workflowVersion === 'all') {
    return (
      workflowsStore.versionsByWorkflow?.[workflow]?.map(({id}) => id) ?? []
    );
  }

  return (
    workflowsStore.versionsByWorkflow?.[workflow]
      ?.filter(({version}) => version === parseInt(workflowVersion))
      ?.map(({id}) => id) ?? []
  );
}

function getRequestFilters(): RequestFilters {
  const filters = getFilters(getSearchString());

  return Object.entries(filters).reduce<RequestFilters>(
    (accumulator, [key, value]) => {
      if (value === undefined) {
        return accumulator;
      }

      if (['active', 'incidents'].includes(key) && typeof value === 'boolean') {
        return {
          ...accumulator,
          [key]: value,
          ...(value === true ? {running: true} : {}),
        };
      }

      if (
        ['canceled', 'completed'].includes(key) &&
        typeof value === 'boolean'
      ) {
        return {
          ...accumulator,
          [key]: value,
          ...(value === true ? {finished: true} : {}),
        };
      }

      if (key === 'errorMessage') {
        return {
          ...accumulator,
          [key]: value,
        };
      }

      if (key === 'flowNodeId') {
        return {
          ...accumulator,
          activityId: value,
        };
      }

      if (key === 'operationId') {
        return {
          ...accumulator,
          batchOperationId: value,
        };
      }

      if (key === 'ids') {
        return {
          ...accumulator,
          ids: parseIds(value),
        };
      }

      if (
        key === 'workflowVersion' &&
        filters.workflow !== undefined &&
        value !== undefined
      ) {
        const workflowIds = getWorkflowIds(filters.workflow, value);

        if (workflowIds.length > 0) {
          return {
            ...accumulator,
            workflowIds,
          };
        }
      }

      if (key === 'variableName' || key === 'variableValue') {
        return {
          ...accumulator,
          variables: {
            ...(accumulator?.variable ?? {}),
            [key === 'variableName' ? 'name' : 'value']: value,
          },
        };
      }

      const parsedDate = parseFilterDate(value);
      if (
        (key === 'startDate' || key === 'endDate') &&
        parsedDate !== undefined
      ) {
        return {
          ...accumulator,
          ...getRequestDatePair(parsedDate, key),
        };
      }

      return accumulator;
    },
    {}
  );
}

const IS_FILTERS_V2 = false;

export {
  decodeFields,
  isVariableEmpty,
  getFilters,
  parseIds,
  parseFilterDate,
  getRequestFilters,
  IS_FILTERS_V2,
  FILTER_FIELDS,
  BOOLEAN_FILTER_FIELDS,
  parseFilters,
};
export type {FilterFieldsType, FiltersType, ParsedFilters, RequestFilters};
