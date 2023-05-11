/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {FilterData} from 'types';
import {t} from 'translation';

export function getHighlightedText(
  text: string,
  highlight?: string,
  matchFromStart?: boolean
): string | JSX.Element[] {
  if (!highlight) {
    return text;
  }

  // we need to escape special characters in the highlight text
  // https://stackoverflow.com/a/3561711
  let regex = highlight.replace(/[-\\^$*+?.()|[\]{}]/g, '\\$&');
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

export function duration(
  timeObject?: FilterData | number | string | null,
  precision?: number | null,
  shortNotation?: boolean
): string {
  // In case the precision from the report configuration is passed to the function but it is turned off, its value is set to null
  // In this case we want to set the default value of the precision to be 3
  if (precision === null) {
    precision = 3;
  }

  if (!timeObject && timeObject !== 0) {
    return '--';
  }

  const time =
    typeof timeObject === 'object'
      ? +timeObject.value * Number(timeUnits[timeObject.unit]?.value)
      : Number(timeObject);

  if (time >= 0 && time < 1) {
    return `${Number(time.toFixed(2)) || 0}ms`;
  }

  const timeSegments: string[] = [];
  let remainingTime = time;
  let remainingPrecision = precision || 0;
  Object.values(timeUnits)
    .sort((a, b) => b.value - a.value)
    .filter(({value}) => value <= time)
    .forEach((currentUnit) => {
      if (precision) {
        if (remainingPrecision-- > 0) {
          let number = Math.floor(remainingTime / currentUnit.value);
          if (!remainingPrecision || currentUnit.abbreviation === 'ms') {
            number = Math.round(remainingTime / currentUnit.value);
          }

          if (number === 0) {
            remainingPrecision++;
          } else {
            const longLabel = `\u00A0${t(
              `common.unit.${currentUnit.label}.label${number !== 1 ? '-plural' : ''}`
            )}`;
            timeSegments.push(`${number}${shortNotation ? currentUnit.abbreviation : longLabel}`);
          }
          remainingTime -= number * currentUnit.value;
        }
      } else if (remainingTime >= currentUnit.value) {
        let numberOfUnits = Math.floor(remainingTime / currentUnit.value);
        // allow numbers with ms abreviation to have floating numbers (avoid flooring)
        // e.g 1.2ms => 1.2 ms. On the other hand, 1.2 seconds => 1 seconds 200ms
        if (currentUnit.abbreviation === 'ms') {
          numberOfUnits = Number((remainingTime / currentUnit.value).toFixed(2));
        }
        timeSegments.push(numberOfUnits + currentUnit.abbreviation);

        remainingTime -= numberOfUnits * currentUnit.value;
      }
    });

  return timeSegments.join('\u00A0');
}

export const convertDurationToObject = (value: number): FilterData => {
  // sort the time units in descending order, then find the first one
  // that fits the provided value without any decimal places
  const [divisor, unit] = (Object.keys(timeUnits) as (keyof typeof timeUnits)[])
    .map<[number, keyof typeof timeUnits]>((key) => [timeUnits[key]!.value, key])
    .sort(([a], [b]) => b - a)
    .find(([divisor]) => value % divisor === 0)!;

  return {
    value: (value / divisor).toString(),
    unit,
  };
};

export const convertToDecimalTimeUnit = (value: number): FilterData => {
  // sort the time units in descending order, then find
  // the biggest one that fits the provided value even if it
  // has decimal places

  const possibleUnits = (Object.keys(timeUnits) as (keyof typeof timeUnits)[])
    .map<[number, keyof typeof timeUnits]>((key) => [timeUnits[key]!.value, key])
    .sort(([a], [b]) => b - a);

  const [divisor = 1, unit] =
    possibleUnits.find(([divisor]) => value / divisor >= 1) ||
    possibleUnits[possibleUnits.length - 1]!;

  return {
    value: String(Number((value / divisor).toFixed(3))),
    unit,
  };
};

export const convertDurationToSingleNumber = (
  threshold:
    | {
        value?: number | string;
        unit: string;
      }
    | string
    | number
): number => {
  if (typeof threshold === 'number' || typeof threshold === 'string') {
    return +threshold;
  }
  return +(threshold.value || 0) * (timeUnits[threshold.unit]?.value || 0);
};

export function convertToMilliseconds(value: number, unit: string) {
  return value * (timeUnits[unit]?.value || 1);
}

const timeUnits: Record<string, {value: number; abbreviation: string; label: string}> = {
  millis: {value: 1, abbreviation: 'ms', label: 'milli'},
  seconds: {value: 1000, abbreviation: 's', label: 'second'},
  minutes: {value: 60 * 1000, abbreviation: 'min', label: 'minute'},
  hours: {value: 60 * 60 * 1000, abbreviation: 'h', label: 'hour'},
  days: {value: 24 * 60 * 60 * 1000, abbreviation: 'd', label: 'day'},
  weeks: {value: 7 * 24 * 60 * 60 * 1000, abbreviation: 'wk', label: 'week'},
  months: {value: 30 * 24 * 60 * 60 * 1000, abbreviation: 'm', label: 'month'},
  years: {value: 12 * 30 * 24 * 60 * 60 * 1000, abbreviation: 'y', label: 'year'},
};
