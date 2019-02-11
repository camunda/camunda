import {formatters} from 'services';

export function isEmpty(str) {
  return !str || 0 === str.length;
}

export function getFormatter(viewProperty) {
  switch (viewProperty) {
    case 'frequency':
      return formatters.frequency;
    case 'duration':
      return formatters.duration;
    default:
      return v => v;
  }
}
