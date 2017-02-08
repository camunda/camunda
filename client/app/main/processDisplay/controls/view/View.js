import {jsx} from 'view-utils';

export function View() {
  return <td>
    <div className="form-group">
      <select className="form-control">
        <option value="none" selected="selected">None</option>
        <option value="frequency">Frequency</option>
        <option value="duration">Duration</option>
        <option value="comparison">Target Value Comparison</option>
      </select>
    </div>
  </td>;
}
