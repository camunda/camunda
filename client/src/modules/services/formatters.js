/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {parseISO, isValid} from 'date-fns';
import {Chart} from 'chart.js';

import {format} from 'dates';
import {t} from 'translation';

const scaleUnits = [
  {exponent: 18, label: 'quintillion'},
  {exponent: 15, label: 'quadrillion'},
  {exponent: 12, label: 'trillion'},
  {exponent: 9, label: 'billion'},
  {exponent: 6, label: 'million'},
  {exponent: 3, label: 'thousand'},
];

function getNumberOfDigits(x) {
  // https://stackoverflow.com/a/28203456
  return Math.max(Math.floor(Math.log10(Math.abs(x))), 0) + 1;
}

export function frequency(number, precision) {
  if (!number && number !== 0) {
    return '--';
  }

  const intl = new Intl.NumberFormat();

  if (precision) {
    const digitsFactor = 10 ** getNumberOfDigits(number);
    const precisionFactor = 10 ** precision;
    const roundedToPrecision =
      (Math.round((number / digitsFactor) * precisionFactor) / precisionFactor) * digitsFactor;

    for (let i = 0; i < scaleUnits.length; i++) {
      const {exponent, label} = scaleUnits[i];
      if (Math.abs(roundedToPrecision) >= 10 ** exponent) {
        const shortened = roundedToPrecision / 10 ** exponent;
        return (
          intl.format(shortened) +
          ' ' +
          t(`common.unit.${label}.label${shortened !== 1 ? '-plural' : ''}`)
        );
      }
    }
    return intl.format(roundedToPrecision);
  }
  return intl.format(number);
}

export function percentage(number) {
  if (!number && number !== 0) {
    return '--';
  }

  return Number(Number(number).toFixed(2)) + '%';
}

export function getRelativeValue(data, total) {
  if (!data && data !== 0) {
    return '--';
  }
  if (data === 0) {
    return '0%';
  }
  return Math.round((data / total) * 1000) / 10 + '%';
}

export function camelCaseToLabel(type) {
  return type.replace(/([A-Z])/g, ' $1').replace(/^./, (str) => str.toUpperCase());
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
  } else if (
    groupBy.value &&
    groupBy.type.toLowerCase().includes('variable') &&
    groupBy.value.type === 'Date'
  ) {
    unit = determineUnit(data.configuration.groupByDateVariableUnit, result);
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

  const formattedResult = result.map((entry) => {
    const date = parseISO(entry.label);
    if (!isValid(date)) {
      return entry;
    }
    return {
      ...entry,
      label: format(date, dateFormat),
    };
  });

  return formattedResult;
}

export function createDurationFormattingOptions(targetLine, dataMinStep, logScale) {
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
    {value: 100 * 1000 * 60 * 60 * 24 * 30 * 12, unit: 'y', base: 1000 * 60 * 60 * 24 * 30 * 12}, //100s of years
  ];

  const niceStepSize = steps.find(({value}) => value > minimumStepSize);
  if (!niceStepSize) {
    return;
  }

  return {
    callback: function (value, ...args) {
      let durationMs = value;

      if (this.type === 'category') {
        const labels = this.getLabels();
        durationMs = Number(labels[value]);
      }

      if (logScale) {
        const logValue = Chart.defaults.scales.logarithmic.ticks.callback.call(
          this,
          value,
          ...args
        );

        if (!logValue) {
          return '';
        }
      }

      return +(durationMs / niceStepSize.base).toFixed(2) + niceStepSize.unit;
    },
    stepSize: niceStepSize.value,
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
    const firstEntry = parseISO(resultData[0].key);
    const secondEntry = parseISO(resultData[1].key);
    const intervalInMs = Math.abs(firstEntry - secondEntry);

    const intervals = [
      {value: 1000 * 60 * 60 * 24 * 30 * 12, unit: 'year'},
      {value: 1000 * 60 * 60 * 24 * 30, unit: 'month'},
      {value: 1000 * 60 * 60 * 24, unit: 'day'},
      {value: 1000 * 60 * 60, unit: 'hour'},
      {value: 0, unit: 'second'},
      {value: -Infinity, unit: 'day'},
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
      dateFormat = 'yyyy-MM-dd HH:00:00';
      break;
    case 'day':
    case 'week':
      dateFormat = 'yyyy-MM-dd';
      break;
    case 'month':
      dateFormat = 'MMM yyyy';
      break;
    case 'year':
      dateFormat = 'yyyy';
      break;
    case 'second':
    default:
      dateFormat = 'yyyy-MM-dd HH:mm:ss';
  }
  return dateFormat;
}

export function formatVersions(versions) {
  if (versions.length === 1 && versions[0] === 'all') {
    return t('common.all');
  } else if (versions.length === 1 && versions[0] === 'latest') {
    return t('common.definitionSelection.latest');
  } else if (versions.length) {
    return versions.join(', ');
  }

  return t('common.none');
}

export function formatTenants(tenantIds, tenantInfo) {
  if (tenantIds.length === 0) {
    return t('common.none');
  }

  if (tenantInfo && tenantInfo.length > 1) {
    if (tenantInfo.length === tenantIds.length) {
      return t('common.all');
    } else {
      return tenantIds
        .map((tenantId) =>
          formatTenantName(tenantInfo.find(({id}) => id === tenantId) ?? {id: tenantId})
        )
        .join(', ');
    }
  }

  return '';
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

export function formatFileName(name) {
  return name.replace(/[^a-zA-Z0-9-_.]/gi, '_').toLowerCase();
}

export function formatLabel(label, numbersOnly) {
  if (!label || typeof label === 'object') {
    return label;
  }
  const MAX_LENGHT = 50;
  const tooLong = label.length >= MAX_LENGHT;
  const parsedLabel = Number.parseFloat(label);
  const isNan = Number.isNaN(parsedLabel);

  if (!tooLong || (numbersOnly && isNan)) {
    return label;
  }

  if (isNan) {
    return label.slice(0, MAX_LENGHT) + '...';
  }

  return parsedLabel.toExponential();
}

export {
  duration,
  getHighlightedText,
  convertDurationToObject,
  convertDurationToSingleNumber,
  convertToDecimalTimeUnit,
  convertToMilliseconds,
} from './formatters.tsx';
