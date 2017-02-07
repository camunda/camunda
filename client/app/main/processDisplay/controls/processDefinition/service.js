import {dispatchAction} from 'view-utils';
import {createLoadProcessDefinitionsAction, createLoadProcessDefinitionsResultAction,
        createSelectProcessDefinitionAction} from './reducer';
import {get} from 'http';

export function loadProcessDefinitions() {
  dispatchAction(createLoadProcessDefinitionsAction());
  get('/api/process-definition')
    .then(response => response.json())
    .then(result => {
      dispatchAction(createLoadProcessDefinitionsResultAction(result));
    });
}

export function selectProcessDefinition(procDefId) {
  dispatchAction(createSelectProcessDefinitionAction(procDefId));
}
