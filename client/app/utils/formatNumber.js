export function formatNumber(number, separator = '\u202F') {
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
