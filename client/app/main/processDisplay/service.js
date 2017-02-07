import {dispatchAction} from 'view-utils';
import {get} from 'http';
import {createLoadingDiagramAction, createLoadingDiagramResultAction,
        createLoadingHeatmapAction, createLoadingHeatmapResultAction} from './reducer';

export function loadHeatmap({id}) {
  dispatchAction(createLoadingHeatmapAction());
  get('/api/process-definition/' + id + '/heatmap')
    .then(response => response.json())
    .then(result => {
      dispatchAction(createLoadingHeatmapResultAction(result));
    })
    .catch(err => {
      //TODO: Add error handling with notifications
    });
}

export function loadDiagram({id}) {
  dispatchAction(createLoadingDiagramAction());
  get('/api/process-definition/' + id + '/xml')
    .then(response => response.text())
    .then(result => {
      dispatchAction(createLoadingDiagramResultAction(result));
    });
}
