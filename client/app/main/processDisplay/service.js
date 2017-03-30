import {dispatchAction, includes} from 'view-utils';
import {get, post} from 'http';
import {createLoadingDiagramAction, createLoadingDiagramResultAction,
        createLoadingHeatmapAction, createLoadingHeatmapResultAction} from './reducer';
import {getFilterQuery} from 'utils';
import {getLastRoute} from 'router';

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
    loadDiagram(params);
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
      //TODO: Add error handling with notifications
    });
}

export function loadDiagram({processDefinitionId}) {
  dispatchAction(createLoadingDiagramAction());
  get('/api/process-definition/' + processDefinitionId + '/xml')
    .then(response => response.text())
    .then(result => {
      dispatchAction(createLoadingDiagramResultAction(result));
    });
}

export function getDefinitionId() {
  const {params: {definition}} = getLastRoute();

  return definition;
}
