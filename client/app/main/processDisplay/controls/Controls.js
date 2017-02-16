import {jsx, createStateInjector, withSelector} from 'view-utils';
import {Filter, CreateFilter} from './filter';
import {ProcessDefinition, getDefinitionId} from './processDefinition';
import {Result} from './result';
import {View} from './view';

export const Controls = withSelector(({onFilterChanged}) => {
  const StateInjector = createStateInjector();

  return <StateInjector>
    <div className="controls row">
      <div className="col-xs-12">
        <form>
          <table>
            <tbody>
              <tr>
                <td><label>Process Definition</label></td>
                <td><label>View</label></td>
                <td colspan="2"><label>Filter</label></td>
                <td><label>Result</label></td>
              </tr>
            <tr>
              <ProcessDefinition selector="processDefinition" onProcessDefinitionSelected={onControlsChange} />
              <View />
              <Filter onFilterDeleted={onControlsChange} />
              <CreateFilter onFilterAdded={onControlsChange} />
              <Result />
            </tr>
          </tbody>
          </table>
        </form>
      </div>
    </div>
  </StateInjector>;

  function onControlsChange() {
    const {processDefinition, filter: {query}} = StateInjector.getState();

    const newFilter = {
      definition: getDefinitionId(processDefinition),
      query
    };

    onFilterChanged(newFilter);
  }
});
