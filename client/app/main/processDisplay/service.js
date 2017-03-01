import {dispatchAction} from 'view-utils';
import {get, post} from 'http';
import {createLoadingDiagramAction, createLoadingDiagramResultAction,
        createLoadingHeatmapAction, createLoadingHeatmapResultAction} from './reducer';
import {getFilterQuery} from 'utils';

export function loadData({definition, query}) {
  const params = {
    processDefinitionId: definition,
    filter: getFilterQuery(query)
  };

  if (areParamsValid(params)) {
    loadDiagram(params);
    loadHeatmap(params);
  }
}

function areParamsValid({processDefinitionId}) {
  return !!processDefinitionId;
}

export function loadHeatmap(filter) {
  dispatchAction(createLoadingHeatmapAction());
  post('/api/process-definition/heatmap/frequency', filter)
    .then(response => response.json())
    .then(result => {
      dispatchAction(createLoadingHeatmapResultAction(result.flowNodes));
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
