import {dispatchAction} from 'view-utils';
import {get, post} from 'http';
import {createLoadingDiagramAction, createLoadingDiagramResultAction,
        createLoadingHeatmapAction, createLoadingHeatmapResultAction} from './reducer';

export function loadHeatmap(filter) {
  dispatchAction(createLoadingHeatmapAction());
  post('/api/process-definition/heatmap', filter)
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
