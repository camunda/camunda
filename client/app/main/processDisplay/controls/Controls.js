import {jsx, createStateComponent, withSelector, Match, Case} from 'view-utils';
import {Filter, CreateFilter} from './filter';
import {createAnalysisSelection} from './analysisSelection';
import {View} from './view';

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
                <View onViewChanged={onControlsChange}/>
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

    function isBranchAnalyisView({view}) {
      return view === 'branch_analysis';
    }

    function onControlsChange() {
      const {filter: query, view} = State.getState();

      onCriteriaChanged({
        query,
        view
      });
    }

    return (parentNode, eventsBus) => {
      const templateUpdate = template(parentNode, eventsBus);
      let initialChangeTriggered = false;

      return [templateUpdate, () => {
        if (!initialChangeTriggered) {
          initialChangeTriggered = true;
          onControlsChange();
        }
      }];
    };
  });

  Controls.nodes = AnalysisSelection.nodes;

  return Controls;
}
