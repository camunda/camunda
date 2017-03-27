import {jsx, withSelector, Match, Case, Default, includes} from 'view-utils';
import {createHeatmapRenderer, createCreateAnalyticsRendererFunction} from './diagram';
import {Statistics} from './statistics';
import {isLoading} from 'utils';
import {loadData} from './service';
import {LoadingIndicator} from 'widgets';
import {createDiagramControlsIntegrator} from './diagramControlsIntegrator';

export const ProcessDisplay = withSelector(Process);

function Process() {
  const {Diagram, Controls, integrator} = createDiagramControlsIntegrator();

  const template = <div className="process-display">
    <Controls onCriteriaChanged={loadData} getBpmnViewer={Diagram.getViewer} />
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
            <Diagram selector="display" createOverlaysRenderer={createHeatmapRenderer} />
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

  function hasNoData({display:{heatmap:{data}}}) {
    return !data || !data.piCount;
  }

  function shouldDisplay(targetView) {
    if (typeof targetView === 'string') {
      targetView = [targetView];
    }

    return ({controls: {view}}) => {
      return includes(targetView, view);
    };
  }

  function isLoadingSomething({display: {diagram, heatmap}, controls}) {
    return isLoading(diagram) || isLoading(heatmap);
  }

  return template;
}
