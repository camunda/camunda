import {jsx, withSelector, Match, Case} from 'view-utils';
import {runOnce} from 'utils';
import {Filter, getFilter} from './filter';
import {createAnalysisSelection} from './analysisSelection';
import {View, getView} from './view';

export function createControls(analysisControlIntegrator) {
  const AnalysisSelection = createAnalysisSelection(analysisControlIntegrator);

  const Controls = withSelector(({onCriteriaChanged, getBpmnViewer, getProcessDefinition}) => {
    const template = <div className="controls row">
      <div className="col-xs-12">
        <form>
          <table>
            <tbody>
              <tr>
                <td><label>View</label></td>
                <td colspan="2"><label>Filter</label></td>
                <Match>
                  <Case predicate={isBranchAnalyisView}>
                    <td><label>End Event</label></td>
                    <td><label>Gateway</label></td>
                  </Case>
                </Match>
              </tr>
            <tr>
              <View onViewChanged={onViewChanged}/>
              <Filter onFilterChanged={onControlsChange} getProcessDefinition={getProcessDefinition} />
              <Match>
                <Case predicate={isBranchAnalyisView}>
                  <AnalysisSelection selector="selection" />
                </Case>
              </Match>
            </tr>
          </tbody>
          </table>
        </form>
      </div>
    </div>;

    function isBranchAnalyisView() {
      return getView() === 'branch_analysis';
    }

    function onViewChanged(view) {
      onCriteriaChanged({
        query: getFilter(),
        view
      });
    }

    function onControlsChange() {
      onCriteriaChanged({
        query: getFilter(),
        view: getView()
      });
    }

    return (parentNode, eventsBus) => {
      const templateUpdate = template(parentNode, eventsBus);

      return [templateUpdate, runOnce(onControlsChange)];
    };
  });

  Controls.nodes = AnalysisSelection.nodes;

  return Controls;
}
