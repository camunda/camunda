import {jsx, Text, OnEvent, Class, isFalsy} from 'view-utils';
import {removeNotification} from './service';

export function Notification() {
  return <div className="alert" role="alert">
    <Class className="alert-info" selector="isError" predicate={isFalsy} />
    <Class className="alert-error" selector="isError" />
    <button type="button" className="close" aria-label="close">
      <OnEvent event="click" listener={close} />
      &times;
    </button>
    <strong class="status">
      <Text property="status" />
    </strong>
    :
    <div class="message">
      <Text property="text" />
    </div>
  </div>;

  function close({state: notification}) {
    removeNotification(notification);
  }
}
