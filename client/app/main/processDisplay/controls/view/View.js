import {jsx} from 'view-utils';
import {onNextUpdate} from 'utils';
import {Select, Option} from 'widgets';
import {setView} from './service';

export function View({onViewChanged}) {
  return <td>
    <div className="form-group">
      <Select onValueSelected={handleChange}>
        <Option value="none" isDefault>None</Option>
        <Option value="frequency">Frequency</Option>
        <Option value="duration">Duration</Option>
        <Option value="branch_analysis">Branch Analysis</Option>
      </Select>
    </div>
  </td>;

  function handleChange({value}) {
    setView(value);
    onNextUpdate(onViewChanged);
  }
}
