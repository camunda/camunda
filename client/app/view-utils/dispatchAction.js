export const ACTION_EVENT_NAME = 'ACTION_EVENT';

export function dispatchAction(action) {
  const actionEvent = new Event(ACTION_EVENT_NAME);

  actionEvent.reduxAction = action;

  document.dispatchEvent(actionEvent);
}
