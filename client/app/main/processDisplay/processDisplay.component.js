import {jsx, withSelector} from 'view-utils';
import {Diagram} from './diagram';
import {ProcessDefinition, FilterList, FilterCreation, Result, View} from './controls';
import {INITIAL_STATE} from 'utils/loading';
import {loadDiagram, loadHeatmap} from './processDisplay.service';

export const ProcessDisplay = withSelector(Process);

function Process() {
  const template = <div className="process-display">
    <div className="controls row">
      <div className="col-xs-12">
        <form>
          <table>
            <tbody>
              <tr>
                <td><label>Process Definition</label></td>
                <td colspan="2"><label>Filter</label></td>
                <td><label>Result</label></td>
                <td><label>View</label></td>
              </tr>
            <tr>
              <ProcessDefinition selector="processDefinition" />
              <FilterList />
              <FilterCreation />
              <Result />
              <View />
            </tr>
          </tbody>
          </table>
        </form>
      </div>
    </div>
    <Diagram selector="display"/>
  </div>;

  function filterValid({id}) {
    return !!id;
  }

  function getFilter({processDefinition}) {
    return {
      id: processDefinition.selected
    };
  }

  function isInInitialState({state}) {
    return state === INITIAL_STATE;
  }

  function update(parentNode, eventsBus) {
    const templateUpdate = template(parentNode, eventsBus);

    return [templateUpdate, (state) => {
      const filter = getFilter(state);

      if (filterValid(filter)) {
        if (isInInitialState(state.display.diagram)) {
          loadDiagram(filter);
        }
        if (isInInitialState(state.display.heatmap)) {
          loadHeatmap(filter);
        }
      }
    }];
  }

  return update;
}
