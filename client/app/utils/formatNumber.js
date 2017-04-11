export function formatNumber(number, separator = ' ') {
  const numberString = '' + number;
  const formattedNumberString = numberString
    .replace(new RegExp('^(\\d{' + (numberString.length%3) + '})', 'g'), '$1 ')
    .replace(/(\d{3})+?/gi, '$1 ')
    .trim();

  return formattedNumberString.replace(/\s/g, separator);
}
