import {jsx, withSelector, Match, Case, Default} from 'view-utils';
import {HeatmapDiagram} from './diagram';
import {Controls, areControlsLoadingSomething, isDataEmpty, getDefinitionId} from './controls';
import {Statistics} from './statistics';
import {isLoading} from 'utils';
import {loadData} from './service';

export const ProcessDisplay = withSelector(Process);

function Process() {
  const template = <div className="process-display">
    <Controls selector="controls" onCriteriaChanged={loadData} />
    <div className="diagram">
      <Match>
        <Case predicate={isLoadingSomething}>
          <div className="loading_indicator overlay">
            <div className="spinner"><span className="glyphicon glyphicon-refresh spin"></span></div>
            <div className="text">loading</div>
          </div>
        </Case>
        <Case predicate={isThereNoProcessDefinitions}>
          <div className="help_screen overlay">
            <div className="no_definitions">
              <span className="indicator glyphicon glyphicon-info-sign"></span>
              <div className="title">No Process Definitions</div>
              <div className="text"><a href="https://github.com/camunda/camunda-optimize/wiki/Installation-guide">Find out how to import your data</a></div>
            </div>
          </div>
        </Case>
        <Case predicate={noDefinitionSelected}>
          <div className="help_screen overlay">
            <div className="process_definition">
              <span className="indicator glyphicon glyphicon-chevron-up"></span>
              <div>Select Process Defintion</div>
            </div>
          </div>
        </Case>
        <Default>
          <HeatmapDiagram selector="display"/>
        </Default>
      </Match>
    </div>
    <Statistics selector="display" />
  </div>;

  function isLoadingSomething({display: {diagram, heatmap}, controls}) {
    return isLoading(diagram) || isLoading(heatmap) || areControlsLoadingSomething(controls);
  }

  function isThereNoProcessDefinitions({controls}) {
    return isDataEmpty(controls);
  }

  function noDefinitionSelected({controls}) {
    return !getDefinitionId(controls);
  }

  return template;
}
