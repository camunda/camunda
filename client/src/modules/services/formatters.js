export function frequency(number) {
  const separator = '\u202F';
  const numberString = '' + number;
  const formattedNumberString = numberString
    // first separators position depends on the total number of digits, add space as separator
    .replace(new RegExp('^(\\d{' + (numberString.length%3) + '})', 'g'), '$1 ')
    // any subsequent separators appear after three numbers, add space as separator
    .replace(/(\d{3})+?/gi, '$1 ')
    // remove potential last space (would be created for '123 ')
    .trim();

  // replace placeholder thousand separator (space) with actual separator
  return formattedNumberString.replace(/\s/g, separator);
}

export function duration(timeObject) {
  const units = [
    {name: 'y',   longName: 'years',   value: 1000 * 60 * 60 * 24 * 30 * 12},
    {name: 'm',   longName: 'months',  value: 1000 * 60 * 60 * 24 * 30},
    {name: 'wk',  longName: 'weeks',   value: 1000 * 60 * 60 * 24 * 7},
    {name: 'd',   longName: 'days',    value: 1000 * 60 * 60 * 24},
    {name: 'h',   longName: 'hours',   value: 1000 * 60 * 60},
    {name: 'min', longName: 'minutes', value: 1000 * 60},
    {name: 's',   longName: 'seconds', value: 1000},
    {name: 'ms',  longName: 'millis',  value: 1}
  ];

  const time = typeof timeObject === 'object' ?
    timeObject.value * units.find(({longName}) => longName === timeObject.unit).value :
    timeObject;

  if (time === 0) {
    return '0ms';
  }

  const timeSegments = [];
  let remainingTime = time;
  for(let i = 0; i < units.length; i++) {
    const currentUnit = units[i];

    if(remainingTime >= currentUnit.value) {
      const numberOfUnits = Math.floor(remainingTime / currentUnit.value);

      timeSegments.push(numberOfUnits + currentUnit.name);

      remainingTime -= numberOfUnits * currentUnit.value;
    }
  }

  return timeSegments.join('\u00A0');
}
