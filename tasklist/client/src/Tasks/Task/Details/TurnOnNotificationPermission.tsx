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
import {useTranslation} from 'react-i18next';

const TurnOnNotificationPermission: React.FC = () => {
  const {t} = useTranslation();
  const [enabled, setEnabled] = useState(true);
  if (!(enabled && Notification.permission === 'default')) {
    return null;
  }
  return (
    <div>
      <ActionableNotification
        inline
        kind="info"
        title={t('turnOnNotificationTitle')}
        subtitle={t('turnOnNotificationSubtitle')}
        actionButtonLabel={t('turnOnNotificationsActionButton')}
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
