const timeUnits = {
  millis: {value: 1, abbreviation: 'ms'},
  seconds: {value: 1000, abbreviation: 's'},
  minutes: {value: 60 * 1000, abbreviation: 'min'},
  hours: {value: 60 * 60 * 1000, abbreviation: 'h'},
  days: {value: 24 * 60 * 60 * 1000, abbreviation: 'd'},
  weeks: {value: 7 * 24 * 60 * 60 * 1000, abbreviation: 'wk'},
  months: {value: 30 * 24 * 60 * 60 * 1000, abbreviation: 'm'},
  years: {value: 12 * 30 * 24 * 60 * 60 * 1000, abbreviation: 'y'}
};

export function frequency(number) {
  const separator = '\u202F';
  const numberString = '' + number;
  const formattedNumberString = numberString
    // first separators position depends on the total number of digits, add space as separator
    .replace(new RegExp('^(\\d{' + numberString.length % 3 + '})', 'g'), '$1 ')
    // any subsequent separators appear after three numbers, add space as separator
    .replace(/(\d{3})+?/gi, '$1 ')
    // remove potential last space (would be created for '123 ')
    .trim();

  // replace placeholder thousand separator (space) with actual separator
  return formattedNumberString.replace(/\s/g, separator);
}

export function duration(timeObject) {
  const time =
    typeof timeObject === 'object'
      ? timeObject.value * timeUnits[timeObject.unit].value
      : timeObject;

  if (time >= 0 && time < 1) {
    return `${time}ms`;
  }

  const timeSegments = [];
  let remainingTime = time;
  Object.keys(timeUnits)
    .map(key => timeUnits[key])
    .sort((a, b) => b.value - a.value)
    .forEach(currentUnit => {
      if (remainingTime >= currentUnit.value) {
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
