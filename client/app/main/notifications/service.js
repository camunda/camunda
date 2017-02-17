import {dispatchAction, $window} from 'view-utils';
import {createAddNotificationAction, createRemoveNotificationAction} from './reducer';

const oneMinute = 60 * 1000;

export function addNotification({status, text, isError = false, timeout = oneMinute}) {
  const notification = {
    status,
    text,
    isError
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
