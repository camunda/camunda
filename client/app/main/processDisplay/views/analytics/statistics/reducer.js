import {addLoading, createResultActionFunction, createLoadingActionFunction, createResetActionFunction} from 'utils';
export const MIN_HEIGHT = 200;
export const DEFAULT_HEIGHT = 350;
export const MAX_HEIGHT = window.innerHeight - 250;

export const SET_HEIGHT = 'SET_HEIGHT';

export const reducer = addLoading((state = {height: DEFAULT_HEIGHT}, action) => {
  if (action.type === SET_HEIGHT) {
    return {
      ...state,
      height: Math.min(MAX_HEIGHT, Math.max(action.height, MIN_HEIGHT))
    };
  }

  return state;
}, 'correlation');

export function createSetHeightAction(height) {
  return {
    type: SET_HEIGHT,
    height
  };
}

export const createLoadCorrelationAction = createLoadingActionFunction('correlation');
export const createLoadCorrelationResultAction = createResultActionFunction('correlation');
export const createResetCorrelationAction = createResetActionFunction('correlation');
