import {addLoading, createResultActionFunction, createLoadingActionFunction, createResetActionFunction} from 'utils';

export const SET_HEIGHT = 'SET_HEIGHT';

export const reducer = addLoading((state = {}, action) => {
  if (action.type === SET_HEIGHT) {
    return {
      ...state,
      height: action.height
    };
  }
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
