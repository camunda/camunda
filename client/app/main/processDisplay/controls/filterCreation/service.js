import {dispatchAction} from 'view-utils';
import {createOpenDateFilterModalAction,
        createCloseDateFilterModalAction} from './reducer';

export function openModal() {
  dispatchAction(createOpenDateFilterModalAction());
}

export function closeModal() {
  dispatchAction(createCloseDateFilterModalAction());
}
