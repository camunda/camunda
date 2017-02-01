import {dispatchAction} from 'view-utils';
// import bpmnFile from './diagram/bpmnFile';
import {get} from 'http';
import {createLoadingDiagramAction, createLoadingDiagramResultAction,
        createLoadingHeatmapAction, createLoadingHeatmapResultAction} from './processDisplay.reducer';

export function loadHeatmap({id}) {
  dispatchAction(createLoadingHeatmapAction());
  get('/api/process-definition/' + id + '/heatmap')
    .then(response => response.json())
    .then(result => {
      dispatchAction(createLoadingHeatmapResultAction(result));
    })
    .catch(err => {
      //TODO: Add error handling with notifications

      //TODO: Remove once we have demo workflow data seed
      // dispatchAction(createLoadingHeatmapResultAction({
      //   StartEvent_1: 2,
      //   Task_04xnoiz: 4,
      //   Task_1wjnddq: 3,
      //   Task_1b0sjb2: 1,
      //   ExclusiveGateway_07mu6ot: 4,
      //   ExclusiveGateway_1qeh21h: 4,
      //   EndEvent_0145qhl: 4
      // }));
    });
}

export function loadDiagram({id}) {
  dispatchAction(createLoadingDiagramAction());
  get('/api/process-definition/' + id + '/xml')
    .then(response => response.text())
    .then(result => {
      //TODO: Remove once we have demo workflow data seed
      // result = bpmnFile;

      dispatchAction(createLoadingDiagramResultAction(result));
    });
}
