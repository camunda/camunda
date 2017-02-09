import {dispatchAction} from 'view-utils';
import {get} from 'http';
import {
  createLoadProcessDefinitionsAction, createLoadProcessDefinitionsResultAction,
  createSelectProcessDefinitionAction
} from './reducer';

export function loadProcessDefinitions() {
  dispatchAction(createLoadProcessDefinitionsAction());
  get('/api/process-definition')
    .then(response => response.json())
    .then(result => {
      dispatchAction(createLoadProcessDefinitionsResultAction(result));
    });
}

export function selectProcessDefinition(id) {
  return dispatchAction(createSelectProcessDefinitionAction(id));
}
