import {dispatchAction} from 'view-utils';
import {get} from 'http';
import {createLoadProcessDefinitionsAction, createLoadProcessDefinitionsResultAction,
        createLoadProcessDefinitionsErrorAction, createSetVersionAction} from './reducer';
import {getRouter} from 'router';
import {addNotification} from 'notifications';

const router = getRouter();

export function loadProcessDefinitions() {
  dispatchAction(createLoadProcessDefinitionsAction());
  get('/api/process-definition?includeXml=true')
    .then(response => response.json())
    .then(result => {
      dispatchAction(createLoadProcessDefinitionsResultAction(result));
    })
    .catch(err => {
      addNotification({
        status: 'Could not load process definitions',
        text: err,
        isError: true
      });
      dispatchAction(createLoadProcessDefinitionsErrorAction(err));
    });
}

export function openDefinition(id) {
  router.goTo('processDisplay', {definition: id});
}

export function setVersionForProcess(key, version) {
  dispatchAction(createSetVersionAction(key, version));
}
