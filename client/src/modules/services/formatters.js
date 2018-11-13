import React from 'react';

const timeUnits = {
  millis: {value: 1, abbreviation: 'ms', label: 'millisecond'},
  seconds: {value: 1000, abbreviation: 's', label: 'second'},
  minutes: {value: 60 * 1000, abbreviation: 'min', label: 'minute'},
  hours: {value: 60 * 60 * 1000, abbreviation: 'h', label: 'hour'},
  days: {value: 24 * 60 * 60 * 1000, abbreviation: 'd', label: 'day'},
  weeks: {value: 7 * 24 * 60 * 60 * 1000, abbreviation: 'wk', label: 'week'},
  months: {value: 30 * 24 * 60 * 60 * 1000, abbreviation: 'm', label: 'month'},
  years: {value: 12 * 30 * 24 * 60 * 60 * 1000, abbreviation: 'y', label: 'year'}
};

export function frequency(number, precision) {
  const intl = new Intl.NumberFormat(undefined, {maximumFractionDigits: precision});

  if (precision) {
    const digitsFactor = 10 ** ('' + number).length;
    const precisionFactor = 10 ** precision;
    const roundedToPrecision =
      Math.round(number / digitsFactor * precisionFactor) / precisionFactor * digitsFactor;

    if (roundedToPrecision >= 10 ** 6) {
      return intl.format(roundedToPrecision / 10 ** 6) + ' Million';
    }
    if (roundedToPrecision >= 10 ** 3) {
      return intl.format(roundedToPrecision / 10 ** 3) + ' Thousand';
    }
    return intl.format(roundedToPrecision);
  }
  return intl.format(number);
}

export function duration(timeObject, precision) {
  const time =
    typeof timeObject === 'object'
      ? timeObject.value * timeUnits[timeObject.unit].value
      : timeObject;

  if (time >= 0 && time < 1) {
    return `${time}ms`;
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
          timeSegments.push(`${number} ${currentUnit.label}${number !== 1 ? 's' : ''}`);
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

export function getHighlightedText(text, highlight) {
  if (!highlight) return text;
  // Split on highlight term and include term into parts, ignore case
  const parts = text.split(new RegExp(`(${highlight})`, 'gi'));
  return parts.map((part, i) => (
    <span
      key={i}
      className={part.toLowerCase() === highlight.toLowerCase() ? 'textBlue' : undefined}
    >
      {part}
    </span>
  ));
}

export function camelCaseToLabel(type) {
  return type.replace(/([A-Z])/g, ' $1').replace(/^./, str => str.toUpperCase());
}
