import {jsx, createStateComponent, withSelector, Match, Case} from 'view-utils';
import {runOnce} from 'utils';
import {Filter, CreateFilter} from './filter';
import {createAnalysisSelection} from './analysisSelection';
import {View, getView} from './view';

export function createControls(analysisControlIntegrator) {
  const AnalysisSelection = createAnalysisSelection(analysisControlIntegrator);

  const Controls = withSelector(({onCriteriaChanged, getBpmnViewer}) => {
    const State = createStateComponent();

    const template = <State>
      <div className="controls row">
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
                <Filter onFilterDeleted={onControlsChange} />
                <CreateFilter onFilterAdded={onControlsChange} />
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
      </div>
    </State>;

    function isBranchAnalyisView() {
      return getView() === 'branch_analysis';
    }

    function onViewChanged(view) {
      const state = State.getState();

      if (state) { //for default value select is trigger before first state update
        const {filter: query} = state;

        onCriteriaChanged({
          query,
          view
        });
      }
    }

    function onControlsChange() {
      const {filter: query} = State.getState();

      onCriteriaChanged({
        query,
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
