import {dispatchAction} from 'view-utils';
import {createLoadProcessDefinitionsAction, createLoadProcessDefinitionsResultAction,
        createSelectProcessDefinitionAction} from './processDefinition.reducer';
import {get} from 'http';

export function loadProcessDefinitions() {
  dispatchAction(createLoadProcessDefinitionsAction());
  get('/api/process-definition')
    .then(response => response.json())
    .then(result => {
      //TODO: Remove once we have demo workflow data seed
      // result.push({id: 'mock1', name: 'Mock Process 1'});
      // result.push({id: 'mock2', name: 'Mock Process 2'});

      dispatchAction(createLoadProcessDefinitionsResultAction(result));
    });
}

export function selectProcessDefinition(procDefId) {
  dispatchAction(createSelectProcessDefinitionAction(procDefId));
}
