import {emptyReducer, addLoading, createLoadingActionFunction, createResultActionFunction} from 'utils';

export const reducer = addLoading(emptyReducer, 'processDefinitions');

export const createLoadProcessDefinitionsAction = createLoadingActionFunction('processDefinitions');
export const createLoadProcessDefinitionsResultAction = createResultActionFunction('processDefinitions');
