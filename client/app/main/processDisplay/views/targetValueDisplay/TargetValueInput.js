import {jsx} from 'view-utils';

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

export function TargetValueInput({unit}) {
  return <td className="target-duration-input-cell">
    <input type="number" for={unit} className="form-control" value="0" min="0" max={getMaxValue(unit)} />
  </td>;
}
