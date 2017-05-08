import {$window, $document} from 'view-utils';

export const ms = 1;
export const s = 1000;
export const m = 60 * s;
export const h = 60 * m;
export const d = 24 * h;
export const w = 7 * d;

const unitsMap = {ms, s, m, h, d, w};
// create sorted array of time units with {name, value} objects
const units = Object
  .keys(unitsMap)
  .reduce((entries, key) => entries.concat({name: key, value: unitsMap[key]}), [])
  .sort(({value: valueA}, {value: valueB}) => valueB - valueA);

export function formatTime(time, config = {}) {
  const {precision, returnRaw} = config;

  if (time === 0) {
    return returnRaw ? [] : '0ms';
  }

  // construct array that breaks up time into weeks, days and so on
  const {parts} = units.reduce(({parts, time}, {name, value}) => {
    // findout how many of currently iterated units of time can we use
    // and return the rest.
    if (time >= value) {
      const reset = time % value;
      const howMuch = (time - reset) / value;

      return {
        parts: parts.concat({
          howMuch,
          name
        }),
        time: reset
      };
    }

    return {parts, time};
  }, {parts: [], time});

  if (returnRaw) {
    return parts;
  }

  // Take biggest non-empty units of time according to precision and construct string out of them
  let nonEmptyParts = parts.filter(({howMuch}) => howMuch > 0);

  if (precision) {
    nonEmptyParts = nonEmptyParts.slice(0, precision);
  }

  return nonEmptyParts
    .reduce((str, {howMuch, name}) => {
      const unit = `${howMuch}${name}`;

      if (str.length > 0) {
        return `${str}&nbsp;${unit}`;
      }

      return unit;
    }, '');
}

export function createDelayedTimePrecisionElement(timeStamp, {initialPrecision, delay}) {
  const element = $document.createElement('span');
  const initialTimeString = formatTime(timeStamp, {precision: initialPrecision});
  const exactTimeString = formatTime(timeStamp);

  element.innerHTML = initialTimeString;

  if (initialTimeString !== exactTimeString) {
    element.innerHTML += '\u2026';

    $window.setTimeout(() => {
      element.innerHTML = formatTime(timeStamp);
    }, delay);
  }

  return element;
}
