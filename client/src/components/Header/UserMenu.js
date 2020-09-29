/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {useEffect, useState} from 'react';
import {withRouter} from 'react-router-dom';

import {withErrorHandling} from 'HOC';
import {Dropdown} from 'components';
import {t} from 'translation';
import {isLogoutHidden} from 'config';
import {showError} from 'notifications';

import './UserMenu.scss';

export function UserMenu({user, onTelemetryOpen, history, mightFail}) {
  const [logoutHidden, setLogoutHidden] = useState(false);

  useEffect(() => {
    mightFail(isLogoutHidden(), setLogoutHidden, showError);
  }, [mightFail]);

  const options = [];
  const isTelemetryAdmin = user?.authorizations.includes('telemetry_administration');
  if (isTelemetryAdmin) {
    options.push(
      <Dropdown.Option key={0} onClick={onTelemetryOpen} title={t('navigation.telemetry')}>
        {t('navigation.telemetry')}
      </Dropdown.Option>
    );
  }

  if (!logoutHidden) {
    if (isTelemetryAdmin) {
      options.push([<hr key={1} />]);
    }
    options.push(
      <Dropdown.Option
        key={2}
        onClick={() => history.push('/logout')}
        title={t('navigation.logout')}
      >
        {t('navigation.logout')}
      </Dropdown.Option>
    );
  }

  if (options.length === 0) {
    return <span className="UserMenu userLabel">{user?.name}</span>;
  }

  return (
    <Dropdown className="UserMenu" label={user?.name}>
      {options}
    </Dropdown>
  );
}

export default withErrorHandling(withRouter(UserMenu));
