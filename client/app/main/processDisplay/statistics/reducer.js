import {emptyReducer, addLoading, createResultActionFunction, createLoadingActionFunction, createResetActionFunction} from 'utils';

export const reducer = addLoading(emptyReducer, 'correlation');

export const createLoadCorrelationAction = createLoadingActionFunction('correlation');
export const createLoadCorrelationResultAction = createResultActionFunction('correlation');
export const createResetCorrelationAction = createResetActionFunction('correlation');
