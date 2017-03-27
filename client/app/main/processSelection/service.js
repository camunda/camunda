import {dispatchAction} from 'view-utils';
import {get} from 'http';
import {createLoadProcessDefinitionsAction, createLoadProcessDefinitionsResultAction} from './reducer';
import {getRouter} from 'router';

const router = getRouter();

export function loadProcessDefinitions() {
  dispatchAction(createLoadProcessDefinitionsAction());
  get('/api/process-definition?includeXml=true')
    .then(response => response.json())
    .then(result => {
      dispatchAction(createLoadProcessDefinitionsResultAction(result));
    });
}

export function openDefinition(id) {
  router.goTo('processDisplay', {definition: id});
}
