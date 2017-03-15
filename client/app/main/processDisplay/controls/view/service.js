import {dispatchAction} from 'view-utils';
import {createSetViewAction} from './reducer';

export function setView(value) {
  dispatchAction(createSetViewAction(value));
}
