import {jsx, OnEvent} from 'view-utils';
import {onNextUpdate} from 'utils';
import {setView} from './service';

export function View({onViewChanged}) {
  return <td>
    <div className="form-group">
      <select className="form-control view-select">
        <OnEvent event="change" listener={handleChange} />
        <option value="none" selected="selected">None</option>
        <option value="frequency">Frequency</option>
        <option value="duration">Duration</option>
        <option value="branch_analysis">Branch Analysis</option>
      </select>
    </div>
  </td>;

  function handleChange({event: {target: {value}}}) {
    setView(value);
    onNextUpdate(onViewChanged);
  }
}
