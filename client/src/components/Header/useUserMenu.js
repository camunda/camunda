/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {useEffect, useState} from 'react';
import {useHistory} from 'react-router-dom';
import {ArrowRight} from '@carbon/react/icons';

import {t} from 'translation';
import {isLogoutHidden, areSettingsManuallyConfirmed} from 'config';
import {showError} from 'notifications';

export default function useUserMenu({user, mightFail, setTelemetrySettingsOpen}) {
  const [logoutHidden, setLogoutHidden] = useState(false);
  const history = useHistory();

  useEffect(() => {
    mightFail(isLogoutHidden(), setLogoutHidden, showError);

    // automatically open the telemetry settings if settings have not been confirmed
    mightFail(areSettingsManuallyConfirmed(), (confirmed) => {
      if (!confirmed && user?.authorizations.includes('telemetry_administration')) {
        setTelemetrySettingsOpen(true);
      }
    });
  }, [mightFail, setTelemetrySettingsOpen, user]);

  const menu = {
    type: 'user',
    ariaLabel: t('common.user.label'),
    customElements: {
      profile: {
        label: t('navigation.profile'),
        user: {
          email: user?.email,
          name: user?.name,
        },
      },
    },
    elements: [],
    bottomElements: [],
  };

  const isTelemetryAdmin = user?.authorizations.includes('telemetry_administration');
  if (isTelemetryAdmin) {
    menu.elements.push({
      key: 'telemetry',
      label: t('navigation.telemetry'),
      onClick: () => setTelemetrySettingsOpen(true),
    });
  }

  if (!logoutHidden) {
    menu.bottomElements.push({
      key: 'logout',
      label: t('navigation.logout'),
      kind: 'ghost',
      onClick: () => history.replace('/logout'),
      renderIcon: ArrowRight,
    });
  }

  return menu;
}
