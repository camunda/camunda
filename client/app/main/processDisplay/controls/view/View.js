import {jsx} from 'view-utils';
import {Select, Option} from 'widgets';
import {Link} from 'router';
import {getDefinitionId} from 'main/processDisplay/service';
import {getView} from './service';
import {getLastRoute} from 'router';

export function View({onViewChanged}) {
  return <td>
    <div className="form-group">
      <Select onValueSelected={handleChange} getSelectValue={getView}>
        <Option value="none" isDefault>
          <Link selector={createRouteSelectorForView('none')} />
          None
        </Option>
        <Option value="frequency">
          <Link selector={createRouteSelectorForView('frequency')} />
          Frequency
        </Option>
        <Option value="duration">
          <Link selector={createRouteSelectorForView('duration')} />
          Duration
        </Option>
        <Option value="branch_analysis">
          <Link selector={createRouteSelectorForView('branch_analysis')} />
          Branch Analysis
        </Option>
        <Option value="target_value">
          <Link selector={createRouteSelectorForView('target_value')} />
          Target Value Comparison
        </Option>
      </Select>
    </div>
  </td>;

  function handleChange({value}) {
    onViewChanged(value);
  }

  function createRouteSelectorForView(view) {
    return () => {
      const {params: {filter}} = getLastRoute();

      return {
        name: 'processDisplay',
        params: {
          filter,
          view: view,
          definition: getDefinitionId()
        }
      };
    };
  }
}
