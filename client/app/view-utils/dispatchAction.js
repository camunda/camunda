import {$document} from './dom';

export const ACTION_EVENT_NAME = 'ACTION_EVENT';

export function dispatchAction(action) {
  const actionEvent = $document.createEvent('CustomEvent');

  actionEvent.initEvent(ACTION_EVENT_NAME, true, true, null);
  actionEvent.reduxAction = action;

  $document.dispatchEvent(actionEvent);
}
