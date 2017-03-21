import {dispatchAction} from 'view-utils';
import {createSetElementAction} from './reducer';

export function setEndEvent({id}) {
  dispatchAction(createSetElementAction(id, 'endEvent'));
}

export function unsetEndEvent() {
  dispatchAction(createSetElementAction(null, 'endEvent'));
}

export function setGateway({id}) {
  dispatchAction(createSetElementAction(id, 'gateway'));
}

export function unsetGateway() {
  dispatchAction(createSetElementAction(null, 'gateway'));
}

export function leaveGatewayAnalysisMode() {
  unsetGateway();
  unsetEndEvent();
}
