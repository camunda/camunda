import {addLoading, createLoadingAction, createResultAction} from 'utils/loading';

export const SELECT_PROCESS_DEFINITION = 'SELECT_PROCESS_DEFINITION';

export function defaultReducer(state = {}, action) {
  if (action.type === SELECT_PROCESS_DEFINITION) {
    return {
      ...state,
      selected: action.id
    };
  }

  return state;
}

export const reducer = addLoading(defaultReducer, 'availableProcessDefinitions');

export function createLoadProcessDefinitionsAction() {
  return createLoadingAction('availableProcessDefinitions');
}

export function createLoadProcessDefinitionsResultAction(result) {
  return createResultAction('availableProcessDefinitions', result);
}

export function createSelectProcessDefinitionAction(procDefId) {
  return {
    type: SELECT_PROCESS_DEFINITION,
    id: procDefId
  };
}
