import {jsx, OnEvent} from 'view-utils';
import {setView} from './service';

export function View() {
  return <td>
    <div className="form-group">
      <select className="form-control view-select">
        <OnEvent event="change" listener={handleChange} />
        <option value="none" selected="selected">None</option>
        <option value="frequency">Frequency</option>
      </select>
    </div>
  </td>;

  function handleChange({event: {target: {value}}}) {
    setView(value);
  }
}
