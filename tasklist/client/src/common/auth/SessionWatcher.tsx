/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useEffect, useRef} from 'react';
import {useTranslation} from 'react-i18next';
import {authenticationStore} from 'common/auth/authentication';
import {observer} from 'mobx-react-lite';
import {useLocation} from 'react-router-dom';
import {pages} from 'common/routing';
import {notificationsStore} from 'common/notifications/notifications.store';

const SessionWatcher: React.FC = observer(() => {
  const removeNotification = useRef<(() => void) | null>(null);
  const status = authenticationStore.status;
  const location = useLocation();
  const {t} = useTranslation();

  useEffect(() => {
    function handleSessionExpiration() {
      removeNotification.current = notificationsStore.displayNotification({
        kind: 'info',
        title: t('sessionWatcherExpiredTitle'),
        isDismissable: true,
      });
    }

    if (location.pathname === pages.login) {
      return;
    }

    if (
      status === 'session-expired' ||
      (status === 'session-invalid' && location.pathname !== pages.initial)
    ) {
      handleSessionExpiration();
    }
  }, [status, t, location.pathname]);

  useEffect(() => {
    if (status === 'logged-in') {
      removeNotification.current?.();
    }
  }, [status]);

  return null;
});

export {SessionWatcher};
