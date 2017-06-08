import {jsx, withSelector, Match, Case, Default, Socket} from 'view-utils';
import {
  createHeatmapRendererFunction, createAnalyticsComponents, getInstanceCount,
  TargetValueDisplay, Statistics, resetStatisticData
} from './views';
import {isLoading, isLoaded, createDelayedTimePrecisionElement} from 'utils';
import {loadData, loadDiagram, getDefinitionId} from './service';
import {isViewSelected, Controls} from './controls';
import {LoadingIndicator, createDiagram} from 'widgets';
import {ProcessInstanceCount} from './ProcessInstanceCount';

export const ProcessDisplay = withSelector(Process);

function Process() {
  const Diagram = createDiagram();
  const {AnalyticsDiagram, AnalysisSelection} = createAnalyticsComponents();

  const template = <div className="process-display">
    <Controls selector={createControlsState} onCriteriaChanged={handleCriteriaChange} getProcessDefinition={getDefinitionId} >
      <Socket name="head">
        <Match>
          <Case predicate={shouldDisplay('branch_analysis')}>
            <td><label>End Event</label></td>
            <td><label>Gateway</label></td>
          </Case>
        </Match>
      </Socket>
      <Socket name="body">
        <Match>
          <Case predicate={shouldDisplay('branch_analysis')}>
            <AnalysisSelection selector="analytics" />
          </Case>
        </Match>
      </Socket>
    </Controls>
    <div className="diagram">
      <LoadingIndicator predicate={isLoadingSomething}>
        <Match>
          <Case predicate={hasNoData}>
            <Diagram selector="views" />
            <div className="no-data-indicator">
              No Data
            </div>
          </Case>
          <Case predicate={shouldDisplay('frequency')}>
            <Diagram selector="views" createOverlaysRenderer={createHeatmapRendererFunction(x => x)} />
          </Case>
          <Case predicate={shouldDisplay('duration')}>
            <Diagram selector="views" createOverlaysRenderer={createHeatmapRendererFunction(x => createDelayedTimePrecisionElement(x, {
              initialPrecision: 2,
              delay: 1500
            }))} />
          </Case>
          <Case predicate={shouldDisplay('branch_analysis')}>
            <AnalyticsDiagram selector="views" />
          </Case>
          <Case predicate={shouldDisplay('target_value')}>
            <TargetValueDisplay selector="views" getProcessDefinition={getDefinitionId} Diagram={Diagram} />
          </Case>
          <Default>
            <Diagram selector="views" />
          </Default>
        </Match>
        <Match>
          <Case predicate={shouldDisplay(['frequency', 'duration', 'branch_analysis', 'target_value'])}>
            <ProcessInstanceCount selector={getProcessInstanceCount} />
          </Case>
        </Match>
      </LoadingIndicator>
    </div>
    <Statistics selector="views" getBpmnViewer={AnalyticsDiagram.getViewer} />
  </div>;

  function handleCriteriaChange(newCriteria) {
    resetStatisticData();
    loadData(newCriteria);
  }

  function getProcessInstanceCount({views}) {
    return getInstanceCount(views);
  }

  function hasNoData({controls, views:{heatmap}}) {
    const {data} = heatmap;

    return isLoaded(heatmap) && (!data || !data.piCount) && isViewSelected(['frequency', 'duration', 'branch_analysis', 'target_value']);
  }

  function shouldDisplay(targetView) {
    return () => isViewSelected(targetView);
  }

  function createControlsState({controls, views}) {
    return {
      ...controls,
      analytics: views.analytics
    };
  }

  function isLoadingSomething({views: {bpmnXml, heatmap, targetValue}}) {
    return isLoading(bpmnXml) || isLoading(heatmap) || isLoading(targetValue);
  }

  return (node, eventsBus) => {
    loadDiagram();

    return template(node, eventsBus);
  };
}
