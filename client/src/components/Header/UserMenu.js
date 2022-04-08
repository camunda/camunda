/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React, {useEffect, useState} from 'react';
import {withRouter} from 'react-router-dom';

import {withErrorHandling, withUser} from 'HOC';
import {Dropdown} from 'components';
import {t} from 'translation';
import {isLogoutHidden, areSettingsManuallyConfirmed} from 'config';
import {showError} from 'notifications';

import {TelemetrySettings} from './TelemetrySettings';

import './UserMenu.scss';

export function UserMenu({user, history, mightFail}) {
  const [logoutHidden, setLogoutHidden] = useState(false);
  const [telemetrySettingsOpen, setTelemetrySettingsOpen] = useState(false);

  useEffect(() => {
    mightFail(isLogoutHidden(), setLogoutHidden, showError);

    // automatically open the telemetry settings if settings have not been confirmed
    mightFail(areSettingsManuallyConfirmed(), (confirmed) => {
      if (!confirmed && user?.authorizations.includes('telemetry_administration')) {
        setTelemetrySettingsOpen(true);
      }
    });
  }, [mightFail, user]);

  const options = [];
  const isTelemetryAdmin = user?.authorizations.includes('telemetry_administration');
  if (isTelemetryAdmin) {
    options.push(
      <Dropdown.Option key="telemetry" onClick={() => setTelemetrySettingsOpen(true)}>
        {t('navigation.telemetry')}
      </Dropdown.Option>
    );
  }

  if (!logoutHidden) {
    if (isTelemetryAdmin) {
      options.push([<hr key="seperator" />]);
    }
    options.push(
      <Dropdown.Option key="logout" onClick={() => history.push('/logout')}>
        {t('navigation.logout')}
      </Dropdown.Option>
    );
  }

  if (options.length === 0) {
    if (!user) {
      return null;
    }

    return <span className="UserMenu userLabel">{user.name}</span>;
  }

  return (
    <>
      <Dropdown className="UserMenu" label={user?.name}>
        {options}
      </Dropdown>
      {telemetrySettingsOpen && (
        <TelemetrySettings
          open={telemetrySettingsOpen}
          onClose={() => setTelemetrySettingsOpen(false)}
        />
      )}
    </>
  );
}

export default withUser(withErrorHandling(withRouter(UserMenu)));
