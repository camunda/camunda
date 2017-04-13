import {dispatchAction, includes} from 'view-utils';
import {get, post} from 'http';
import {createLoadingDiagramAction, createLoadingDiagramResultAction, createLoadingDiagramErrorAction,
        createLoadingHeatmapAction, createLoadingHeatmapResultAction, createLoadingHeatmapErrorAction} from './diagram';
import {getFilterQuery} from 'utils';
import {getLastRoute} from 'router';
import {addNotification} from 'notifications';

const viewHeatmapEndpoints = {
  branch_analysis: 'frequency',
  frequency: 'frequency',
  duration: 'duration'
};

export function loadData({query, view}) {
  const params = {
    processDefinitionId: getDefinitionId(),
    filter: getFilterQuery(query)
  };

  if (areParamsValid(params)) {
    if (includes(['duration', 'frequency', 'branch_analysis'], view)) {
      loadHeatmap(view, params);
    }
  }
}

function areParamsValid({processDefinitionId}) {
  return !!processDefinitionId;
}

export function loadHeatmap(view, filter) {
  dispatchAction(createLoadingHeatmapAction());
  post(`/api/process-definition/heatmap/${viewHeatmapEndpoints[view]}`, filter)
    .then(response => response.json())
    .then(result => {
      dispatchAction(createLoadingHeatmapResultAction(result));
    })
    .catch(err => {
      addNotification({
        status: 'Could not load heatmap data',
        text: err,
        isError: true
      });
      dispatchAction(createLoadingHeatmapErrorAction(err));
    });
}

export function loadDiagram(processDefinitionId = getDefinitionId()) {
  dispatchAction(createLoadingDiagramAction());
  get('/api/process-definition/' + processDefinitionId + '/xml')
    .then(response => response.text())
    .then(result => {
      dispatchAction(createLoadingDiagramResultAction(result));
    })
    .catch(err => {
      addNotification({
        status: 'Could not load diagram',
        text: err,
        isError: true
      });
      dispatchAction(createLoadingDiagramErrorAction(err));
    });
}

export function getDefinitionId() {
  const {params: {definition}} = getLastRoute();

  return definition;
}
