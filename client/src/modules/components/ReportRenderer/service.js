import {formatters} from 'services';

export function isEmpty(str) {
  return !str || 0 === str.length;
}

export function getConfig(props, viewProperty) {
  let formatter = {};

  switch (viewProperty) {
    case 'frequency':
      formatter = formatters.frequency;
      break;
    case 'duration':
      formatter = formatters.duration;
      break;
    default:
      formatter = v => v;
  }

  return {
    formatter,
    ...props
  };
}
