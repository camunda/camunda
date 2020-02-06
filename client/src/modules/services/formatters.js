/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import moment from 'moment';
import {t} from 'translation';

const timeUnits = {
  millis: {value: 1, abbreviation: 'ms', label: 'milli'},
  seconds: {value: 1000, abbreviation: 's', label: 'second'},
  minutes: {value: 60 * 1000, abbreviation: 'min', label: 'minute'},
  hours: {value: 60 * 60 * 1000, abbreviation: 'h', label: 'hour'},
  days: {value: 24 * 60 * 60 * 1000, abbreviation: 'd', label: 'day'},
  weeks: {value: 7 * 24 * 60 * 60 * 1000, abbreviation: 'wk', label: 'week'},
  months: {value: 30 * 24 * 60 * 60 * 1000, abbreviation: 'm', label: 'month'},
  years: {value: 12 * 30 * 24 * 60 * 60 * 1000, abbreviation: 'y', label: 'year'}
};

export function frequency(number, precision) {
  if (!number && number !== 0) {
    return '--';
  }

  const intl = new Intl.NumberFormat(undefined, {maximumFractionDigits: precision});

  if (precision) {
    const digitsFactor = 10 ** ('' + number).length;
    const precisionFactor = 10 ** precision;
    const roundedToPrecision =
      (Math.round((number / digitsFactor) * precisionFactor) / precisionFactor) * digitsFactor;

    if (roundedToPrecision >= 10 ** 6) {
      const millions = roundedToPrecision / 10 ** 6;
      return (
        intl.format(millions) +
        ' ' +
        t(`common.unit.million.label${millions !== 1 ? '-plural' : ''}`)
      );
    }

    if (roundedToPrecision >= 10 ** 3) {
      const thousand = roundedToPrecision / 10 ** 3;
      return (
        intl.format(thousand) +
        ' ' +
        t(`common.unit.thousand.label${thousand !== 1 ? '-plural' : ''}`)
      );
    }
    return intl.format(roundedToPrecision);
  }
  return intl.format(number);
}

export function duration(timeObject, precision) {
  if (!timeObject && timeObject !== 0) {
    return '--';
  }

  const time =
    typeof timeObject === 'object'
      ? timeObject.value * timeUnits[timeObject.unit].value
      : timeObject;

  if (time >= 0 && time < 1) {
    return `${time || 0}ms`;
  }

  const timeSegments = [];
  let remainingTime = time;
  let remainingPrecision = precision;
  Object.keys(timeUnits)
    .map(key => timeUnits[key])
    .sort((a, b) => b.value - a.value)
    .filter(({value}) => value <= time)
    .forEach(currentUnit => {
      if (precision) {
        if (remainingPrecision-- > 0) {
          let number = Math.floor(remainingTime / currentUnit.value);
          if (!remainingPrecision || currentUnit.abbreviation === 'ms') {
            number = Math.round(remainingTime / currentUnit.value);
          }
          timeSegments.push(
            `${number} ${t(
              `common.unit.${currentUnit.label}.label${number !== 1 ? '-plural' : ''}`
            )}`
          );
          remainingTime -= number * currentUnit.value;
        }
      } else if (remainingTime >= currentUnit.value) {
        let numberOfUnits = Math.floor(remainingTime / currentUnit.value);
        // allow numbers with ms abreviation to have floating numbers (avoid flooring)
        // e.g 1.2ms => 1.2 ms. On the other hand, 1.2 seconds => 1 seconds 200ms
        if (currentUnit.abbreviation === 'ms') {
          numberOfUnits = remainingTime / currentUnit.value;
        }
        timeSegments.push(numberOfUnits + currentUnit.abbreviation);

        remainingTime -= numberOfUnits * currentUnit.value;
      }
    });

  return timeSegments.join('\u00A0');
}

export const convertDurationToObject = value => {
  // sort the time units in descending order, then find the first one
  // that fits the provided value without any decimal places
  const [divisor, unit] = Object.keys(timeUnits)
    .map(key => [timeUnits[key].value, key])
    .sort(([a], [b]) => b - a)
    .find(([divisor]) => ~~(value / divisor) === value / divisor);

  return {
    value: (value / divisor).toString(),
    unit
  };
};

export const convertDurationToSingleNumber = threshold => {
  if (typeof threshold.value === 'undefined') {
    return threshold;
  }
  return threshold.value * timeUnits[threshold.unit].value;
};

export const convertCamelToSpaces = label => {
  let formattedLabel = label.replace(/([A-Z])/g, ' $1');
  return formattedLabel.charAt(0).toUpperCase() + formattedLabel.slice(1);
};

export function convertToMilliseconds(value, unit) {
  return value * timeUnits[unit].value;
}

export function getHighlightedText(text, highlight, matchFromStart) {
  if (!highlight) {
    return text;
  }
  let regex = highlight;
  if (matchFromStart) {
    regex = '^' + regex;
  }
  // Split on highlight term and include term into parts, ignore case
  const parts = text.split(new RegExp(`(${regex})`, 'gi'));
  return parts.map((part, i) => (
    <span
      key={i}
      className={part.toLowerCase() === highlight.toLowerCase() ? 'textBold' : undefined}
    >
      {part}
    </span>
  ));
}

export function camelCaseToLabel(type) {
  return type.replace(/([A-Z])/g, ' $1').replace(/^./, str => str.toUpperCase());
}

export function objectifyResult(result) {
  return result.reduce((acc, {key, value}) => {
    acc[key] = value;
    return acc;
  }, {});
}

export function formatReportResult(data, result) {
  const groupBy = data.groupBy;
  let unit;
  if (groupBy.value && groupBy.type.includes('Date')) {
    unit = determineUnit(groupBy.value.unit, result);
  } else if (groupBy.value && groupBy.type === 'variable' && groupBy.value.type === 'Date') {
    unit = 'second';
  }

  if (!unit || !result) {
    // the result data is no time series
    return [...result];
  }

  let dateFormat = getDateFormat(unit);
  // The added space is to make sure all dates are string,
  // since integer keys in objects does not preserve order
  if (unit === 'year') {
    dateFormat += ' ';
  }

  const formattedResult = result.map(entry => ({
    ...entry,
    label: moment(entry.label).format(dateFormat)
  }));

  return formattedResult;
}

export function createDurationFormattingOptions(targetLine, dataMinStep) {
  // since the duration is given in milliseconds, chart.js cannot create nice y axis
  // ticks. So we define our own set of possible stepSizes and find one that the maximum
  // value of the dataset fits into or the maximum target line value if it is defined.

  const targetLineMinStep = targetLine ? targetLine : 0;
  const minimumStepSize = Math.max(targetLineMinStep, dataMinStep) / 10;

  const steps = [
    {value: 1, unit: 'ms', base: 1},
    {value: 10, unit: 'ms', base: 1},
    {value: 100, unit: 'ms', base: 1},
    {value: 1000, unit: 's', base: 1000},
    {value: 1000 * 10, unit: 's', base: 1000},
    {value: 1000 * 60, unit: 'min', base: 1000 * 60},
    {value: 1000 * 60 * 10, unit: 'min', base: 1000 * 60},
    {value: 1000 * 60 * 60, unit: 'h', base: 1000 * 60 * 60},
    {value: 1000 * 60 * 60 * 6, unit: 'h', base: 1000 * 60 * 60},
    {value: 1000 * 60 * 60 * 24, unit: 'd', base: 1000 * 60 * 60 * 24},
    {value: 1000 * 60 * 60 * 24 * 7, unit: 'wk', base: 1000 * 60 * 60 * 24 * 7},
    {value: 1000 * 60 * 60 * 24 * 30, unit: 'm', base: 1000 * 60 * 60 * 24 * 30},
    {value: 1000 * 60 * 60 * 24 * 30 * 6, unit: 'm', base: 1000 * 60 * 60 * 24 * 30},
    {value: 1000 * 60 * 60 * 24 * 30 * 12, unit: 'y', base: 1000 * 60 * 60 * 24 * 30 * 12},
    {value: 10 * 1000 * 60 * 60 * 24 * 30 * 12, unit: 'y', base: 1000 * 60 * 60 * 24 * 30 * 12}, //10s of years
    {value: 100 * 1000 * 60 * 60 * 24 * 30 * 12, unit: 'y', base: 1000 * 60 * 60 * 24 * 30 * 12} //100s of years
  ];

  const niceStepSize = steps.find(({value}) => value > minimumStepSize);
  if (!niceStepSize) {
    return;
  }

  return {
    callback: v => +(v / niceStepSize.base).toFixed(2) + niceStepSize.unit,
    stepSize: niceStepSize.value
  };
}

function determineUnit(unit, resultData) {
  if (unit === 'automatic') {
    return determineUnitForAutomaticIntervalSelection(resultData);
  } else {
    // in this case the unit was already defined by the user
    // and can just directly be used.
    return unit;
  }
}

function determineUnitForAutomaticIntervalSelection(resultData) {
  if (resultData.length > 1) {
    const firstEntry = moment(resultData[0].key);
    const secondEntry = moment(resultData[1].key);
    const intervalInMs = Math.abs(firstEntry.diff(secondEntry));

    const intervals = [
      {value: 1000 * 60 * 60 * 24 * 30 * 12, unit: 'year'},
      {value: 1000 * 60 * 60 * 24 * 30, unit: 'month'},
      {value: 1000 * 60 * 60 * 24, unit: 'day'},
      {value: 1000 * 60 * 60, unit: 'hour'},
      {value: 0, unit: 'second'},
      {value: -Infinity, unit: 'day'}
    ];
    return intervals.find(({value}) => intervalInMs >= value).unit;
  } else {
    return 'day';
  }
}

function getDateFormat(unit) {
  let dateFormat;
  switch (unit) {
    case 'hour':
      dateFormat = 'YYYY-MM-DD HH:00:00';
      break;
    case 'day':
    case 'week':
      dateFormat = 'YYYY-MM-DD';
      break;
    case 'month':
      dateFormat = 'MMM YYYY';
      break;
    case 'year':
      dateFormat = 'YYYY';
      break;
    case 'second':
    default:
      dateFormat = 'YYYY-MM-DD HH:mm:ss';
  }
  return dateFormat;
}

export function formatTenantName({id, name}) {
  if (!id) {
    return t('common.definitionSelection.tenant.notDefined');
  }

  if (id === '__unauthorizedTenantId__') {
    return t('home.sources.unauthorizedTenant');
  }

  return name || id;
}
