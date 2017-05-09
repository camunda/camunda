import {jsx, withSelector, Match, Case, Default} from 'view-utils';
import {createHeatmapRendererFunction, createCreateAnalyticsRendererFunction, getInstanceCount, TargetValueDisplay} from './diagram';
import {Statistics, resetStatisticData} from './statistics';
import {isLoading, createDelayedTimePrecisionElement} from 'utils';
import {loadData, loadDiagram, getDefinitionId} from './service';
import {isViewSelected} from './controls';
import {LoadingIndicator} from 'widgets';
import {createDiagramControlsIntegrator} from './diagramControlsIntegrator';
import {ProcessInstanceCount} from './ProcessInstanceCount';

export const ProcessDisplay = withSelector(Process);

function Process() {
  const {Diagram, Controls, integrator} = createDiagramControlsIntegrator();

  const template = <div className="process-display">
    <Controls selector={createControlsState} onCriteriaChanged={handleCriteriaChange} />
    <div className="diagram">
      <LoadingIndicator predicate={isLoadingSomething}>
        <Match>
          <Case predicate={hasNoData}>
            <Diagram selector="diagram" />
            <div className="no-data-indicator">
              No Data
            </div>
          </Case>
          <Case predicate={shouldDisplay('frequency')}>
            <Diagram selector="diagram" createOverlaysRenderer={createHeatmapRendererFunction(x => x)} />
          </Case>
          <Case predicate={shouldDisplay('duration')}>
            <Diagram selector="diagram" createOverlaysRenderer={createHeatmapRendererFunction(x => createDelayedTimePrecisionElement(x, {
              initialPrecision: 2,
              delay: 1500
            }))} />
          </Case>
          <Case predicate={shouldDisplay('branch_analysis')}>
            <Diagram selector="diagram" createOverlaysRenderer={createCreateAnalyticsRendererFunction(integrator)} />
          </Case>
          <Case predicate={shouldDisplay('target_value')}>
            <TargetValueDisplay selector="diagram" getProcessDefinition={getDefinitionId} Diagram={Diagram} />
          </Case>
          <Default>
            <Diagram selector="diagram" />
          </Default>
        </Match>
        <Match>
          <Case predicate={shouldDisplay(['frequency', 'duration', 'branch_analysis', 'target_value'])}>
            <ProcessInstanceCount selector={getProcessInstanceCount} />
          </Case>
        </Match>
      </LoadingIndicator>
    </div>
    <Statistics getBpmnViewer={Diagram.getViewer} />
  </div>;

  function handleCriteriaChange(newCriteria) {
    resetStatisticData();
    loadData(newCriteria);
  }

  function getProcessInstanceCount({diagram}) {
    return getInstanceCount(diagram);
  }

  function hasNoData({controls, diagram:{heatmap:{data}}}) {
    return (!data || !data.piCount) && isViewSelected(['frequency', 'duration', 'branch_analysis', 'target_value']);
  }

  function shouldDisplay(targetView) {
    return () => isViewSelected(targetView);
  }

  function createControlsState({controls, diagram}) {
    const selection = {};

    Object.keys(diagram.selection).forEach(key => {
      selection[key] = getName(diagram.selection[key]);
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

  function isLoadingSomething({diagram: {bpmnXml, heatmap, targetValue}, controls}) {
    return isLoading(bpmnXml) || isLoading(heatmap) || isLoading(targetValue);
  }

  return (node, eventsBus) => {
    loadDiagram();

    return template(node, eventsBus);
  };
}
