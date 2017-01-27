import {jsx, withSelector} from 'view-utils';
import {Diagram} from './diagram';
import {ProcessDefinition, FilterList, FilterCreation, Result, View} from './controls';

export const ProcessDisplay = withSelector(Process);

function Process() {
  return <div className="process-display">
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
}
