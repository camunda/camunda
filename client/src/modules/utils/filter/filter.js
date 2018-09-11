import {isValid, addDays, startOfDay, addMinutes, format} from 'date-fns';

/**
 * Returns a query string for the filter objects
 * removes keys with empty values (null, "", []) so that they don't appear in URL
 */
export function getFilterQueryString(filter = {}) {
  const cleanedFilter = Object.entries(filter).reduce((obj, [key, value]) => {
    return !!value && value.length !== 0 ? {...obj, [key]: value} : obj;
  }, {});

  return `?filter=${JSON.stringify(cleanedFilter)}`;
}

/**
 * For a given date field's value returns the corresponding url options for filtering
 * Returns an object of two values [name]dateBefore and [name]dateAfter
 * where name is oneOf['starDate', 'endDate']
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
 * Collection of parsers for filter field values
 * we used this parser before making a call to backend with the current filters
 */
export const fieldParser = {
  errorMessage: (name, value) => {
    return {[name]: value.length === 0 ? null : value};
  },
  ids: (name, value) => {
    return {[name]: value.split(/[ ,]+/).filter(Boolean)};
  },
  startDate: (name, value) => {
    return parseDate(value, 'startDate');
  },
  endDate: (name, value) => {
    return parseDate(value, 'endDate');
  }
};

function defaultFieldParser(name, value) {
  return {[name]: value};
}

/**
 * Adds running or finished additional payload,
 * they are required when fetching the instances by state
 */
export function getInstanceStatePayload(filter) {
  const {active, incidents, completed, canceled} = filter;

  return {
    running: active || incidents,
    finished: completed || canceled
  };
}

/**
 * Before fetching the instances for the ListView
 * the filter field values need to be parsed
 */
export function parseFilterForRequest(filter) {
  let parsedFilter = {};

  for (let key in filter) {
    const value = filter[key];
    const parsedField = fieldParser[key]
      ? fieldParser[key](key, value)
      : defaultFieldParser(key, value);

    parsedFilter = {
      ...parsedFilter,
      ...parsedField
    };
  }

  return {
    ...parsedFilter,
    ...getInstanceStatePayload(filter)
  };
}
