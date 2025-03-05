/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {
  ActionableNotification,
  unstable_FeatureFlags as FeatureFlags,
} from '@carbon/react';
import {requestPermission} from 'common/os-notifications/requestPermission';
import {getStateLocally, storeStateLocally} from 'common/local-storage';
import {useState} from 'react';
import {useTranslation} from 'react-i18next';
import styles from './TurnOnNotificationPermission.module.scss';

const TurnOnNotificationPermission: React.FC = () => {
  const {t} = useTranslation();
  const areNativeNotificationsEnabled = getStateLocally(
    'areNativeNotificationsEnabled',
  );
  const [isEnabled, setIsEnabled] = useState(
    'Notification' in window &&
      Notification.permission === 'default' &&
      !(areNativeNotificationsEnabled === false),
  );

  if (!isEnabled) {
    return null;
  }

  return (
    <div>
      {/* This is a temporary fix, it should be removed once this feature is implemented on Carbon: https://github.com/camunda/camunda/issues/26648 */}
      <FeatureFlags
        flags={{
          'enable-experimental-focus-wrap-without-sentinels': true,
        }}
      >
        <ActionableNotification
          inline
          kind="info"
          role="status"
          aria-live="polite"
          title={t('turnOnNotificationTitle')}
          subtitle={t('turnOnNotificationSubtitle')}
          actionButtonLabel={t('turnOnNotificationsActionButton')}
          onActionButtonClick={async () => {
            const result = await requestPermission();
            if (result !== 'default') {
              setIsEnabled(false);
            }
          }}
          onClose={() => {
            setIsEnabled(false);
            storeStateLocally('areNativeNotificationsEnabled', false);
          }}
          className={styles.actionableNotification}
          lowContrast
        />
      </FeatureFlags>
    </div>
  );
};

export {TurnOnNotificationPermission};
