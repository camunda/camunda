export const LOAD_PROCESS_DIAGRAM = 'LOAD_PROCESS_DIAGRAM';
export const LOAD_PROCESS_DIAGRAM_RESULT = 'LOAD_PROCESS_DIAGRAM_RESULT';
export const LOAD_HEATMAP = 'LOAD_HEATMAP';
export const LOAD_HEATMAP_RESULT = 'LOAD_HEATMAP_RESULT';
export const HOVER_ELEMENT = 'HOVER_ELEMENT';

export const INITIAL_STATE = 'INITIAL';
export const LOADING_STATE = 'LOADING';
export const LOADED_STATE = 'LOADED';

export function reducer(state = {id: 'aProcessInstanceId', state: 'INITIAL', heatmap: {state: 'INITIAL'}}, action) {
  if (action.type === HOVER_ELEMENT) {
    return {
      ...state,
      hovered: action.element
    };
  }

  if (action.type === LOAD_PROCESS_DIAGRAM) {
    return {
      ...state,
      state: LOADING_STATE
    };
  }

  if (action.type === LOAD_PROCESS_DIAGRAM_RESULT) {
    return {
      ...state,
      state: LOADED_STATE,
      xml: action.result
    };
  }

  if (action.type === LOAD_HEATMAP) {
    return {
      ...state,
      heatmap: {
        state: LOADING_STATE
      }
    };
  }

  if (action.type === LOAD_HEATMAP_RESULT) {
    return {
      ...state,
      heatmap: {
        state: LOADED_STATE,
        data: action.result
      }
    };
  }
  return state;
}

export function createLoadingDiagramAction() {
  return {
    type: LOAD_PROCESS_DIAGRAM
  };
}

export function createLoadingDiagramResultAction(result) {
  return {
    type: LOAD_PROCESS_DIAGRAM_RESULT,
    result
  };
}

export function createLoadingHeatmapAction() {
  return {
    type: LOAD_HEATMAP
  };
}

export function createLoadingHeatmapResultAction(result) {
  return {
    type: LOAD_HEATMAP_RESULT,
    result
  };
}

export function createHoverElementAction(element) {
  return {
    type: HOVER_ELEMENT,
    element: element.id
  };
}
