export const LOAD_PROCESS_DIAGRAM = 'LOAD_PROCESS_DIAGRAM';
export const LOAD_PROCESS_DIAGRAM_RESULT = 'LOAD_PROCESS_DIAGRAM_RESULT';
export const LOAD_HEATMAP = 'LOAD_HEATMAP';
export const LOAD_HEATMAP_RESULT = 'LOAD_HEATMAP_RESULT';

export function reducer(state = {id: 'aProcessInstanceId', state: 'INITIAL', heatmap: {state: 'INITIAL'}}, action) {
  if (action.type === LOAD_PROCESS_DIAGRAM) {
    return {
      ...state,
      state: 'LOADING'
    };
  }

  if (action.type === LOAD_PROCESS_DIAGRAM_RESULT) {
    return {
      ...state,
      state: 'LOADED',
      xml: action.result
    };
  }

  if (action.type === LOAD_HEATMAP) {
    return {
      ...state,
      heatmap: {
        state: 'LOADING'
      }
    };
  }

  if (action.type === LOAD_HEATMAP_RESULT) {
    return {
      ...state,
      heatmap: {
        state: 'LOADED',
        data: action.result
      }
    };
  }
  return state;
}

export function createLoadingDiagramAction(diagram) {
  return {
    type: LOAD_PROCESS_DIAGRAM,
    diagram
  };
}

export function createLoadingDiagramResultAction(result) {
  return {
    type: LOAD_PROCESS_DIAGRAM_RESULT,
    result
  };
}

export function createLoadingHeatmapAction(diagram) {
  return {
    type: LOAD_HEATMAP,
    diagram
  };
}

export function createLoadingHeatmapResultAction(result) {
  return {
    type: LOAD_HEATMAP_RESULT,
    result
  };
}
