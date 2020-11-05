/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {isValid, addDays, startOfDay, addMinutes, format} from 'date-fns';

import {compactObject} from '../index';
import {isValidJSON, trimValue, tryDecodeURI} from 'modules/utils';
import {trimVariable} from 'modules/utils/variable';

/**
 * Reduce a filter object down to the state properties
 */
export function reduceToStates(filter: any) {
  const {active, incidents, completed, canceled} = filter;
  return {active, incidents, completed, canceled};
}

/**
 * Returns a query string for the filter objects
 * removes keys with empty values (null, "", []) so that they don't appear in URL
 */
export function getFilterQueryString(filter = {}, name: any) {
  const cleanedFilter = compactObject(filter);
  const filterString = `?filter=${JSON.stringify(cleanedFilter)}`;
  const nameString = `&name=${JSON.stringify(name)}`;
  return name ? filterString + nameString : filterString;
}

export function parseQueryString(queryString = '') {
  var params = {};

  const queries = queryString.replace(/%22/g, '"').substring(1).split('&');

  queries.forEach((item, index) => {
    const [paramKey, paramValue] = queries[index].split('=');
    if (isValidJSON(paramValue)) {
      // @ts-expect-error ts-migrate(7053) FIXME: No index signature with a parameter of type 'strin... Remove this comment to see the full error message
      params[paramKey] = JSON.parse(paramValue);
    }
  });

  return params;
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

export {decodeFields, isVariableEmpty};
