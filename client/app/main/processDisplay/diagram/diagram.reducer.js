import {addLoading, createLoadingAction, createResultAction} from 'utils/loading';

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

export function createLoadingDiagramAction() {
  return createLoadingAction('diagram');
}

export function createLoadingDiagramResultAction(result) {
  return createResultAction('diagram', result);
}

export function createLoadingHeatmapAction() {
  return createLoadingAction('heatmap');
}

export function createLoadingHeatmapResultAction(result) {
  return createResultAction('heatmap', result);
}

export function createHoverElementAction(element) {
  return {
    type: HOVER_ELEMENT,
    element: element.id
  };
}
