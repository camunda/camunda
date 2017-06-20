import {dispatchAction, $window} from 'view-utils';
import {createAddNotificationAction, createRemoveNotificationAction} from './reducer';

export function addNotification({status, text, timeout, type, isError = false}) {
  const notification = {
    status,
    text,
    type: type ? type : isError ? 'error' : 'info',
    id: Math.random()
  };
  const remove = removeNotification.bind(null, notification);

  dispatchAction(createAddNotificationAction(notification));

  if (timeout) {
    $window.setTimeout(remove, timeout);
  }

  return remove;
}

export function removeNotification(notification) {
  dispatchAction(createRemoveNotificationAction(notification));
}

$window.addNotification = addNotification;
