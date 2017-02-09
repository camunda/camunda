import {dispatchAction} from 'view-utils';
import {get, post} from 'http';
import {createLoadingDiagramAction, createLoadingDiagramResultAction,
        createLoadingHeatmapAction, createLoadingHeatmapResultAction} from './reducer';

export function loadData(filter) {
  const params = getParams(filter);

  if (areParamsValid(params)) {
    loadDiagram(params);
    loadHeatmap(params);
  }
}

function areParamsValid({processDefinitionId}) {
  return !!processDefinitionId;
}

function getParams({definition, query}) {
  const dates = query.reduce((dates, entry) => {
    return dates.concat([
      {
        type: 'start_date',
        operator: '>=',
        value : entry.data.start,
        lowerBoundary : true,
        upperBoundary : true
      },
      {
        type: 'start_date',
        operator: '<=',
        value : entry.data.end,
        lowerBoundary : true,
        upperBoundary : true
      }
    ]);
  }, []);

  return {
    processDefinitionId: definition,
    filter: {
      dates
    }
  };
}

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
