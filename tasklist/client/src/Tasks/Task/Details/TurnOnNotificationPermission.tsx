/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {ActionableNotification} from '@carbon/react';
import {requestPermission} from 'modules/os-notifications/requestPermission';
import {useState} from 'react';

const TurnOnNotificationPermission: React.FC = () => {
  const [enabled, setEnabled] = useState(true);
  if (!(enabled && Notification.permission === 'default')) {
    return null;
  }
  return (
    <div>
      <ActionableNotification
        inline
        kind="info"
        title="Don't miss new assignments"
        subtitle="Turn on notifications in your browser to get notified when new tasks are assigned to you"
        actionButtonLabel="Turn on notifications"
        onActionButtonClick={() => {
          requestPermission();
        }}
        onClose={() => setEnabled(false)}
        style={{maxInlineSize: 'initial'}}
        lowContrast
        hasFocus={false}
      />
    </div>
  );
};

export {TurnOnNotificationPermission};
