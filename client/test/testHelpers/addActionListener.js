import {ACTION_EVENT_NAME, $document} from 'view-utils';

export function addActionListener(listener) {
  const callback = ({reduxAction}) => {
    listener(reduxAction);
  };

  $document.addEventListener(ACTION_EVENT_NAME, callback);

  return $document.removeEventListener.bind($document, ACTION_EVENT_NAME, callback);
}
