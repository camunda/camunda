import {jsx, withSelector, Match, Case, Default} from 'view-utils';
import {createHeatmapRenderer, createCreateAnalyticsRendererFunction} from './diagram';
import {Statistics} from './statistics';
import {isLoading} from 'utils';
import {loadData} from './service';
import {isViewSelected, getView as getControlsView} from './controls';
import {LoadingIndicator} from 'widgets';
import {createDiagramControlsIntegrator} from './diagramControlsIntegrator';

export const ProcessDisplay = withSelector(Process);

function Process() {
  const {Diagram, Controls, integrator} = createDiagramControlsIntegrator();

  const template = <div className="process-display">
    <Controls selector={createControlsState} onCriteriaChanged={loadData} />
    <div className="diagram">
      <LoadingIndicator predicate={isLoadingSomething}>
        <Match>
          <Case predicate={hasNoData}>
            <Diagram selector="display" />
            <div className="no-data-indicator">
              No Data
            </div>
          </Case>
          <Case predicate={shouldDisplay(['frequency', 'duration'])}>
            <Diagram selector={getHeatmapState} createOverlaysRenderer={createHeatmapRenderer} />
          </Case>
          <Case predicate={shouldDisplay('branch_analysis')}>
            <Diagram selector="display" createOverlaysRenderer={createCreateAnalyticsRendererFunction(integrator)} />
          </Case>
          <Default>
            <Diagram selector="display" />
          </Default>
        </Match>
      </LoadingIndicator>
    </div>
    <Statistics getBpmnViewer={Diagram.getViewer} />
  </div>;

  function getHeatmapState({display, controls}) {
    return {
      ...display,
      view: getControlsView(controls)
    };
  }

  function hasNoData({controls, display:{heatmap:{data}}}) {
    return (!data || !data.piCount) && isViewSelected(controls, ['frequency', 'duration', 'branch_analysis']);
  }

  function shouldDisplay(targetView) {
    return ({controls}) => {
      return isViewSelected(controls, targetView);
    };
  }

  function createControlsState({controls, display}) {
    const selection = {};

    Object.keys(display.selection).forEach(key => {
      selection[key] = getName(display.selection[key]);
    });

    return {
      ...controls,
      selection
    };
  }

  function getName(id) {
    const viewer = Diagram.getViewer();

    if (id && viewer) {
      return viewer
      .get('elementRegistry')
      .get(id)
      .businessObject
      .name
      || id;
    }
  }

  function isLoadingSomething({display: {diagram, heatmap}, controls}) {
    return isLoading(diagram) || isLoading(heatmap);
  }

  return template;
}
