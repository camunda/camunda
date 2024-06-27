/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
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
        onActionButtonClick={async () => {
          const result = await requestPermission();
          if (result !== 'default') {
            setEnabled(false);
          }
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
