const ADD_NOTIFICATION = 'ADD_NOTIFICATION';
const REMOVE_NOTIFICATION = 'REMOVE_NOTIFICATION';

export function reducer(state = [], action) {
  if (action.type === ADD_NOTIFICATION) {
    return state.concat([
      action.notification
    ]);
  }

  if (action.type === REMOVE_NOTIFICATION) {
    return state.filter(
      ({id}) => id !== action.notification.id
    );
  }

  return state;
}

export function createAddNotificationAction(notification) {
  return {
    type: ADD_NOTIFICATION,
    notification
  };
}

export function createRemoveNotificationAction(notification) {
  return {
    type: REMOVE_NOTIFICATION,
    notification
  };
}
