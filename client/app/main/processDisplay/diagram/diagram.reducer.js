import {addLoading, createLoadingActionFunction, createResultActionFunction} from 'utils/loading';

export const HOVER_ELEMENT = 'HOVER_ELEMENT';

function hoverReducer(state = {}, action) {
  if (action.type === HOVER_ELEMENT) {
    return {
      ...state,
      hovered: action.element
    };
  }
  return state;
}

export const reducer = addLoading(hoverReducer, 'diagram', 'heatmap');

export const createLoadingDiagramAction = createLoadingActionFunction('diagram');
export const createLoadingDiagramResultAction = createResultActionFunction('diagram');
export const createLoadingHeatmapAction = createLoadingActionFunction('heatmap');
export const createLoadingHeatmapResultAction = createResultActionFunction('heatmap');

export function createHoverElementAction(element) {
  return {
    type: HOVER_ELEMENT,
    element: element.id
  };
}
