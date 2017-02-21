import {jsx, List, withSelector} from 'view-utils';
import {Notification} from './Notification';

export const Notifications = withSelector(() => {
  return <div className="page-notifications notifications-panel">
    <List>
      <Notification />
    </List>
  </div>;
});
