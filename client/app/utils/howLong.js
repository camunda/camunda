export const ms = 1;
export const s = 1000;
export const m = 60 * s;
export const h = 60 * m;
export const d = 24 * h;

const unitsMap = {ms, s, m, h, d};
const units = Object
  .keys(unitsMap)
  .reduce((entries, key) => entries.concat({name: key, value: unitsMap[key]}), [])
  .sort(({value: valueA}, {value: valueB}) => valueB - valueA);

export function howLong(time) {
  const {parts} = units.reduce(({parts, time}, {name, value}) => {
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

  return parts
    .filter(({howMuch}) => howMuch > 0)
    .slice(0, 2)
    .reduce((str, {howMuch, name}) => {
      const unit = `${howMuch}${name}`;

      if (str.length > 0) {
        return `${str}&nbsp;${unit}`;
      }

      return unit;
    }, '');
}
