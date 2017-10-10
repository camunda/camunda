import React from 'react';
const jsx = React.createElement;

const maxValues = {
  d: 6,
  h: 23,
  m: 59,
  s: 59,
  ms: 999
};

function getMaxValue(unit) {
  return maxValues[unit];
}

export function TargetValueInput({unit, val, onChange}) {
  return (<td className="target-duration-input-cell">
    <input type="number" htmlFor={unit} className="form-control" value={val} min="0" max={getMaxValue(unit)} onChange={onChange} />
  </td>);
}
