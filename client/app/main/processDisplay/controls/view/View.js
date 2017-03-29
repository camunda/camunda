import {jsx, Socket} from 'view-utils';
import {onNextUpdate} from 'utils';
import {Select, StaticOption} from 'widgets';
import {setView} from './service';

export function View({onViewChanged}) {
  return <td>
    <div className="form-group">
      <Select onValueSelected={handleChange}>
        <Socket name="list">
          <StaticOption value="none" name="None" isDefault={true} />
          <StaticOption value="frequency" name="Frequency" />
          <StaticOption value="duration" name="Duration" />
          <StaticOption value="branch_analysis" name="Branch Analysis" />
        </Socket>
      </Select>
    </div>
  </td>;

  function handleChange({value}) {
    setView(value);
    onNextUpdate(onViewChanged);
  }
}
