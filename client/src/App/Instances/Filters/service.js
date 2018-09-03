import {isValid, addDays, startOfDay, addMinutes, format} from 'date-fns';
import {ALL_VERSIONS_OPTION, DEFAULT_CONTROLLED_VALUES} from './constants';

/**
 * Creates an array of {value: String, label: String} objects
 * used to create options list for workflowName select based on workflows data
 */
export const getOptionsForWorkflowName = (workflows = {}) => {
  let options = [];
  Object.keys(workflows).forEach(item => {
    options.push({value: item, label: workflows[item].name || item});
  });

  return options;
};

/**
 * Creates an array of {value: String, label: String} objects
 * used to create options list for workflowIds select based on workflows list
 */
export function getOptionsForWorkdflowIds(versions = []) {
  return versions.map(item => ({
    value: item.id,
    label: `Version ${item.version}`
  }));
}

/**
 * Pushes an All version option to the given options array
 * used for workflowIds select
 */
export function addAllVersionsOption(options = []) {
  options.push({value: ALL_VERSIONS_OPTION, label: 'All versions'});
  return options;
}

/**
 * For a given date field's value returns the corresponding url options for filtering
 * Returns an object of two values [name]dateBefore and [name]dateAfter
 */
const parseDate = (value, name) => {
  let date = new Date(value);
  const isValidDate = isValid(date);
  let dateAfter, dateBefore;
  // enforce no comma in the timezone
  const formatWithTimezone = 'YYYY-MM-DDTHH:mm:ss.SSSZZ';

  if (value === '') {
    return {
      [`${name}After`]: null,
      [`${name}Before`]: null
    };
  }

  if (!isValidDate) {
    return null;
  }

  // temporary condition to check for presence of time in user input
  // as we can't decide based on a string
  const hasTime = value.indexOf(':') !== -1;

  dateAfter = hasTime ? date : startOfDay(date);
  dateBefore = hasTime ? addMinutes(date, 1) : addDays(date, 1);

  return {
    [`${name}After`]: format(dateAfter, formatWithTimezone),
    [`${name}Before`]: format(dateBefore, formatWithTimezone)
  };
};

/**
 * Collection of parsers for filter field
 * each field value should be reflected in the url after it's parsed
 */
export const fieldParser = {
  errorMessage: value => (value.length === 0 ? null : value),
  ids: value => value.split(/[ ,]+/).filter(Boolean),
  startDate: value => {
    return parseDate(value, 'startDate');
  },
  endDate: value => {
    return parseDate(value, 'endDate');
  },
  activityId: value => value
};

/**
 * Prevents controlled filter fields from receiving undefined values
 * Instances page passes filter prop with the active filters from url
 */

export function getFilterWithDefaults(filter) {
  return {
    ...DEFAULT_CONTROLLED_VALUES,
    ...filter
  };
}
