import {jsx} from 'view-utils';
import {Select, Option} from 'widgets';
import {Link} from 'router';
import {getDefinitionId} from 'main/processDisplay/service';
import {getView} from './service';
import {getLastRoute} from 'router';
import {ControlsElement} from '../ControlsElement';
import {definitions} from 'main/processDisplay/views';

export function View({onViewChanged}) {
  return <ControlsElement name="View">
    <div className="form-group">
      <Select onValueSelected={handleChange} getSelectValue={getView}>
        <Option value="none" isDefault>
          <Link selector={createRouteSelectorForView('none')} />
          None
        </Option>
        {
          Object
            .keys(definitions)
            .sort() // alphabetic order should be bit more intuitive
            .map((view, index) => {
              const {name} = definitions[view];

              return <Option value={view}>
                <Link selector={createRouteSelectorForView(view)} />
                {name}
              </Option>;
            })
        }
      </Select>
    </div>
  </ControlsElement>;

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
