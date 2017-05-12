import {
  createLoadingReducer, createLoadingActionFunction, createResultActionFunction,
  createErrorActionFunction
} from 'utils';

export const reducer = createLoadingReducer('progress');

export const createLoadingProgressAction = createLoadingActionFunction('progress');
export const createLoadingProgressResultAction = createResultActionFunction('progress');
export const createLoadingProgressErrorAction = createErrorActionFunction('progress');
